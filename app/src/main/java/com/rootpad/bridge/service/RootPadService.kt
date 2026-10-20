package com.rootpad.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rootpad.bridge.MainActivity
import com.rootpad.bridge.R
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.core.AppState
import com.rootpad.bridge.core.BridgeEngine
import com.rootpad.bridge.core.DaemonStatus
import com.rootpad.bridge.core.LangState
import com.rootpad.bridge.core.PadState
import com.rootpad.bridge.core.Proto
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.root.RootExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.ArrayDeque

/**
 * 入力変換のフォアグラウンドサービス。
 *
 * 役割:
 *  - root デーモンの起動・監視・再起動 (異常終了検知)
 *  - daemon からの生イベントを BridgeEngine へ流す
 *  - 4ms ティックでゲームパッド状態を生成し daemon へ注入
 *  - サービス停止時は必ず入力リセット (押下残留防止) を行う
 */
class RootPadService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.rootpad.bridge.action.START"
        const val ACTION_STOP = "com.rootpad.bridge.action.STOP"
        private const val CHANNEL_ID = "rootpad_conversion"
        private const val NOTIF_ID = 42

        private val _running = kotlinx.coroutines.flow.MutableStateFlow(false)
        val running: kotlinx.coroutines.flow.StateFlow<Boolean> = _running

        fun start(context: Context) {
            val i = Intent(context, RootPadService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, RootPadService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        /** 実行中のエンジンへプロファイル変更を即時反映する */
        @Volatile
        private var activeEngine: BridgeEngine? = null

        fun pushProfile() {
            activeEngine?.updateProfile(AppGraph.profiles.active)
        }

        /** サーバー自身への参照 (診断コマンド用) */
        @Volatile
        private var instance: RootPadService? = null

        /** uinput 生成テストを daemon に依頼する (結果は AppState.selfTest へ) */
        fun runSelfTest() {
            val lang = LangState.current()
            val inst = instance ?: run {
                AppState.setSelfTest(
                    com.rootpad.bridge.core.SelfTestResult(
                        false, Strings.get(lang, "diag.selftest_no_service"), null
                    )
                )
                return
            }
            AppState.setSelfTest(
                com.rootpad.bridge.core.SelfTestResult(false, Strings.get(lang, "diag.selftest_running"), null)
            )
            inst.serviceScope.launch {
                runCatching { inst.request("SELFTEST", "SELFTEST", 10000) }
                    .onFailure { e ->
                        AppState.setSelfTest(
                            com.rootpad.bridge.core.SelfTestResult(
                                false,
                                Strings.format(
                                    LangState.current(), "diag.selftest_comm_error",
                                    e.message ?: "timeout"
                                ),
                                null
                            )
                        )
                    }
            }
        }

        /** デバイス一覧を daemon に再スキャンさせる */
        fun refreshDevices() {
            val inst = instance ?: return
            inst.serviceScope.launch {
                runCatching { inst.request("LIST", "LIST", 5000) }
            }
        }
    }

    private data class Pending(val token: String, val deferred: CompletableDeferred<String>)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<Pending>()
    private val pendingLock = Any()

    private var daemonProcess: Process? = null
    private var connection: DaemonConnection? = null
    private var engine: BridgeEngine? = null
    private var tickThread: Thread? = null
    private var stdoutThread: Thread? = null

    @Volatile private var tickRunning = false
    @Volatile private var stopping = false
    @Volatile private var starting = false
    @Volatile private var grabActive = false
    @Volatile private var restartAttempts = 0
    @Volatile private var padEventn = -1

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch { stopConversion("ユーザー操作") }
            }
            else -> {
                startForegroundCompat(Strings.get(LangState.current(), "notif.starting"))
                if (!starting && !AppState.conversionActive.value) {
                    serviceScope.launch { startConversion() }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // suspend 関数は呼べないため、クリーンアップ本体を直接実行する
        runCatching { AppState.appendLog("変換を停止します (サービス破棄)") }
        runCatching { stopInternal() }
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 開始処理
    // ------------------------------------------------------------------

    private suspend fun startConversion() {
        if (starting || AppState.conversionActive.value) return
        starting = true
        try {
            AppState.setLastError(null)
            AppState.appendLog("変換を開始しています...")

            // 1. Root 権限の確認
            val root = RootExecutor.checkRoot()
            AppState.setRootStatus(root.granted, root.detail)
            if (!root.granted) {
                failAndStop("Root 権限が取得できません: ${root.detail}")
                return
            }
            AppState.appendLog("Root 権限: OK (${root.detail})")

            // 2. デーモンの展開
            val daemonFile = RootExecutor.prepareDaemonBinary(this)
            if (daemonFile == null) {
                failAndStop("rootpad_d を展開できません (arm64-v8a ビルドが APK に入っているか確認してください)")
                return
            }

            // 3. root でデーモン起動
            val su = RootExecutor.findSu() ?: "su"
            val proc = RootExecutor.startRootProcess(
                su,
                "${daemonFile.absolutePath} --sock ${Proto.SOCKET_NAME}"
            )
            daemonProcess = proc
            watchStdout(proc)

            // 4. ソケット接続
            val conn = DaemonConnection(object : DaemonConnection.Listener {
                override fun onDaemonLine(line: String) = handleDaemonLine(line)
                override fun onConnectionLost(reason: String) = handleConnectionLost(reason)
            })
            if (!conn.connect(Proto.SOCKET_NAME, 4000)) {
                failAndStop("デーモンへの接続に失敗しました (4秒以内にソケットが応答しません。ログを確認してください)")
                return
            }
            connection = conn
            AppState.setDaemonStatus(DaemonStatus.Starting)

            val profile = AppGraph.profiles.active

            // 5. Xbox 用キーレイアウト導入 (失敗しても変換自体は継続)
            if (profile.installKeylayout) {
                val kl = runCatching { request("KLINSTALL", "KLINSTALL", 5000) }.getOrNull()
                val ok = kl?.startsWith("OK") == true
                AppState.setKeylayoutInstalled(ok)
                AppState.appendLog(if (ok) "キーレイアウト導入: OK" else "キーレイアウト導入: 失敗 (続行します)")
            } else {
                AppState.setKeylayoutInstalled(null)
            }

            // 6. 仮想パッド生成
            val padLine = request("PADCREATE", "PADCREATE", 8000)
            if (padLine.startsWith("ERR")) {
                failAndStop("仮想ゲームパッドの生成に失敗: ${explainErrno(padLine)}")
                return
            }
            val eventn = Regex("""eventn=(-?\d+)""").find(padLine)?.groupValues?.get(1)?.toInt() ?: -1
            val node = Regex("""node=(\S+)""").find(padLine)?.groupValues?.get(1) ?: ""
            val uinput = Regex("""uinput=(\S+)""").find(padLine)?.groupValues?.get(1) ?: ""
            padEventn = eventn
            AppState.setPadInfo(
                com.rootpad.bridge.core.PadInfo(
                    eventn = eventn, node = node, uinputPath = uinput, name = Proto.PAD_NAME
                )
            )
            AppState.appendLog("仮想パッド生成: eventn=$eventn ($uinput)")

            // 7. 変換エンジン
            engine = BridgeEngine(profile)
            activeEngine = engine

            // 8. 物理デバイスの GRAB (二重送信防止)
            grabActive = profile.grabDevices
            if (grabActive) {
                val g = request("GRAB 1", "GRAB", 5000)
                AppState.appendLog(if (g.startsWith("OK")) "物理デバイスを独占 (GRAB) しました" else "GRAB 失敗: $g")
            }

            // 9. デバイス一覧の取得
            runCatching { request("LIST", "LIST", 5000) }

            // 10. Android からの認識状況を確認
            val (padInfo, _) = AndroidInputProbe.timedFindPad()
            AppState.setAndroidPad(padInfo)

            // 11. ティック開始
            startTick()

            AppState.setConversionActive(true)
            AppState.setDaemonStatus(
                DaemonStatus.Running(
                    pid = Regex("""pid=(\d+)""").find(lastReadyLine)?.groupValues?.get(1)?.toInt() ?: -1,
                    uinputPath = uinput
                )
            )
            _running.value = true
            restartAttempts = 0
            AppState.appendLog("入力変換を開始しました")
            updateNotification(Strings.get(LangState.current(), "notif.converting"))
        } catch (e: Exception) {
            failAndStop("開始に失敗しました: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            starting = false
        }
    }

    @Volatile
    private var lastReadyLine: String = ""

    // ------------------------------------------------------------------
    // daemon 行処理
    // ------------------------------------------------------------------

    private fun handleDaemonLine(line: String) {
        when {
            line.startsWith("READY ") -> {
                lastReadyLine = line
                AppState.appendLog("daemon: $line")
            }
            line.startsWith("OK ") || line.startsWith("ERR ") -> {
                val token = line.split(' ', limit = 3).getOrNull(1) ?: ""
                synchronized(pendingLock) {
                    val it = pending.iterator()
                    while (it.hasNext()) {
                        val p = it.next()
                        if (p.token == token) {
                            it.remove()
                            p.deferred.complete(line)
                            break
                        }
                    }
                }
                if (line.startsWith("OK SELFTEST")) AppState.setSelfTest(
                    com.rootpad.bridge.core.SelfTestResult(true, line)
                )
                if (line.startsWith("ERR SELFTEST")) AppState.setSelfTest(
                    com.rootpad.bridge.core.SelfTestResult(
                        false, explainErrno(line),
                        errnoOf(line)
                    )
                )
            }
            line.startsWith("EV ") -> {
                val parts = line.split(' ')
                if (parts.size >= 5) {
                    try {
                        val type = parts[2].toInt()
                        val code = parts[3].toInt()
                        val value = parts[4].toInt()
                        if (type == 1) AppState.incKeyboardEvents()
                        else if (type == 2) AppState.incMouseEvents()
                        // 割り当てキャプチャ: 押下 (value=1) のキー/ボタンのみ記録
                        if (type == 1 && value == 1) AppState.noteCapturedKey(type, code)
                        engine?.onLinuxEvent(
                            devid = parts[1].toInt(),
                            type = type, code = code, value = value,
                            nowMs = SystemClock.elapsedRealtime()
                        )
                    } catch (e: NumberFormatException) {
                        // 無視
                    }
                }
            }
            line.startsWith("DEVADD ") -> {
                val parts = line.split(' ', limit = 4)
                if (parts.size >= 4) {
                    val n = parts[1].toIntOrNull() ?: return
                    AppState.upsertDevice(
                        com.rootpad.bridge.core.RemoteDevice(n, parts[2], parts[3], grabActive)
                    )
                    AppState.appendLog("入力デバイス接続: ${parts[3]} (${parts[2]})")
                }
            }
            line.startsWith("DEVDEL ") -> {
                val n = line.substringAfter(' ').trim().toIntOrNull() ?: return
                AppState.removeDevice(n)
                engine?.onDeviceRemoved(n)
                AppState.appendLog("入力デバイス切断: event$n")
            }
            line.startsWith("DEV ") -> {
                // LIST 応答のデバイス行: DEV <n> <kind> grab=<0|1> <name>
                val parts = line.split(' ', limit = 5)
                if (parts.size >= 5) {
                    val n = parts[1].toIntOrNull() ?: return
                    val grabbed = parts[3] == "grab=1"
                    AppState.upsertDevice(com.rootpad.bridge.core.RemoteDevice(n, parts[2], parts[4], grabbed))
                }
            }
            line.startsWith("GONE") -> {
                AppState.appendLog("警告: $line")
                if (!stopping) {
                    serviceScope.launch {
                        failAndStop("仮想ゲームパッドが消失しました (uinput ノードの除去を検出)。再度開始してください。")
                    }
                }
            }
            line.startsWith("INFO ") -> {
                val kv = line.removePrefix("INFO ").split('=', limit = 2)
                if (kv.size == 2) AppState.updateSysInfo(kv[0], kv[1])
                if (kv[0] == "uinput") AppState.appendLog(line)
            }
            else -> AppState.appendLog("daemon: $line")
        }
    }

    private fun handleConnectionLost(reason: String) {
        AppState.appendLog("daemon 接続喪失: $reason")
        if (stopping || starting) return
        serviceScope.launch {
            if (AppState.conversionActive.value && restartAttempts < 3) {
                restartAttempts++
                AppState.appendLog("再起動を試みます ($restartAttempts/3)...")
                stopInternal()
                delay(800)
                startConversion()
            } else {
                failAndStop("デーモンとの接続が切れました: $reason")
            }
        }
    }

    private fun watchStdout(proc: Process) {
        stdoutThread = Thread({
            try {
                proc.inputStream.bufferedReader().forEachLine { l ->
                    if (l.isNotBlank()) AppState.appendLog("root: $l")
                }
            } catch (e: Exception) {
                // プロセス終了によるもの
            }
            val code = runCatching { proc.waitFor() }.getOrDefault(-1)
            onDaemonExited(code)
        }, "rootpad-stdout").apply { isDaemon = true; start() }
    }

    private fun onDaemonExited(code: Int) {
        if (stopping || !AppState.conversionActive.value) return
        AppState.appendLog("デーモンが終了しました (code=$code)")
        handleConnectionLost("process_exit code=$code")
    }

    // ------------------------------------------------------------------
    // リクエスト/レスポンス
    // ------------------------------------------------------------------

    private suspend fun request(cmd: String, token: String, timeoutMs: Long): String {
        val conn = connection ?: throw IllegalStateException("not connected")
        val deferred = CompletableDeferred<String>()
        synchronized(pendingLock) { pending.addLast(Pending(token, deferred)) }
        conn.send(cmd)
        return withTimeout(timeoutMs) { deferred.await() }
    }

    // ------------------------------------------------------------------
    // ティックループ
    // ------------------------------------------------------------------

    private fun startTick() {
        tickRunning = true
        tickThread = Thread({
            var lastSent: PadState? = null
            var lastPreview = 0L
            var lastProbe = 0L
            var lastPing = 0L
            while (tickRunning && !Thread.currentThread().isInterrupted) {
                val now = SystemClock.elapsedRealtime()
                val eng = engine
                if (eng == null) break
                val state = eng.tick(now)
                if (state != lastSent) {
                    connection?.send(state.encode())
                    lastSent = state
                    AppState.setCurrentPad(state)
                }
                // daemon の watchdog (8s 無通信で終了) を防ぐキープアライブ
                if (now - lastPing >= 2000) {
                    lastPing = now
                    connection?.send("PING")
                }
                if (now - lastPreview >= 100) {
                    lastPreview = now
                    val mp = eng.mousePreview()
                    AppState.setMousePreview(mp.first, mp.second)
                }
                if (now - lastProbe >= 2000) {
                    lastProbe = now
                    val (info, _) = AndroidInputProbe.timedFindPad()
                    val cur = AppState.androidPad.value
                    if (info != cur) AppState.setAndroidPad(info)
                }
                try {
                    Thread.sleep(4)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }, "rootpad-tick").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    // ------------------------------------------------------------------
    // 停止処理
    // ------------------------------------------------------------------

    private suspend fun stopConversion(reason: String) {
        AppState.appendLog("変換を停止します ($reason)")
        stopInternal()
        stopForegroundCompat()
        stopSelf()
    }

    /** 入力リセット → GRAB 解除 → daemon 終了 → 状態クリア */
    private fun stopInternal() {
        stopping = true
        try {
            tickRunning = false
            tickThread?.let {
                it.interrupt()
                it.join(300)
            }
        } catch (e: Exception) {
        }
        tickThread = null

        runCatching { engine?.releaseAll() }
        engine = null
        activeEngine = null

        connection?.let { c ->
            runCatching { c.send(PadState.NEUTRAL.encode()) }
            runCatching { c.send("GRAB 0") }
            runCatching { c.send("QUIT") }
            runCatching { Thread.sleep(60) }
            runCatching { c.close() }
        }
        connection = null

        synchronized(pendingLock) {
            pending.forEach { it.deferred.cancel() }
            pending.clear()
        }

        daemonProcess?.let { p ->
            runCatching { p.destroy() }
            val exited = runCatching { p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) }.getOrDefault(true)
            if (!exited) runCatching { p.destroyForcibly() }
        }
        daemonProcess = null
        stdoutThread = null

        padEventn = -1
        grabActive = false
        AppState.setConversionActive(false)
        AppState.setDaemonStatus(DaemonStatus.Stopped)
        AppState.setPadInfo(null)
        AppState.setDevices(emptyList())
        AppState.setCurrentPad(PadState.NEUTRAL)
        AppState.setMousePreview(0f, 0f)
        _running.value = false
        stopping = false
    }

    private fun failAndStop(message: String) {
        AppState.setLastError(message)
        AppState.appendLog("エラー: $message")
        AppState.setDaemonStatus(DaemonStatus.Failed(message))
        runCatching { stopInternal() }
        updateNotification(Strings.format(LangState.current(), "notif.stopped", message), ongoing = false)
        stopForegroundCompat()
        stopSelf()
    }

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(text: String) {
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(text),
            if (Build.VERSION.SDK_INT >= 34)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else 0
        )
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(text: String, ongoing: Boolean = true) {
        val mgr = getSystemService(NotificationManager::class.java)
        val n = buildNotification(text)
        if (!ongoing) n.flags = n.flags and Notification.FLAG_ONGOING_EVENT.inv()
        mgr.notify(NOTIF_ID, n)
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RootPadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.notification_action_stop),
                stopIntent
            )
            .build()
    }

    // ------------------------------------------------------------------
    // ユーティリティ
    // ------------------------------------------------------------------

    private fun errnoOf(line: String): Int? =
        Regex("""errno=(-?\d+)""").find(line)?.groupValues?.get(1)?.toInt()

    private fun explainErrno(line: String): String {
        val errno = errnoOf(line)
        val base = line
        val hint = when (errno) {
            13 -> "権限拒否 (EACCES): SELinux が uinput アクセスを拒否している可能性。Magisk の SELinux パッチ/ポリシーを確認してください。"
            2 -> "ファイルなし (ENOENT): カーネルに uinput が無効か、/dev/uinput が存在しません。カーネルの CONFIG_INPUT_UINPUT を確認してください。"
            16 -> "使用中 (EBUSY)"
            19 -> "デバイス消失 (ENODEV)"
            22 -> "引数不正 (EINVAL): このカーネルは uinput の新 API に非対応の可能性があります。"
            else -> ""
        }
        return if (hint.isEmpty()) base else "$base / $hint"
    }
}
