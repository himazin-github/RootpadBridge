package com.rootpad.bridge.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** daemon の生存状態 */
sealed class DaemonStatus {
    object Stopped : DaemonStatus()
    object Starting : DaemonStatus()
    data class Running(val pid: Int, val uinputPath: String) : DaemonStatus()
    data class Failed(val message: String) : DaemonStatus()
}

/** daemon が観測した物理入力デバイス */
data class RemoteDevice(
    val eventn: Int,
    val kind: String,   // "kbd" / "mouse"
    val name: String,
    val grabbed: Boolean
)

/** 仮想ゲームパッドの情報 */
data class PadInfo(
    val eventn: Int,
    val node: String,
    val uinputPath: String,
    val name: String
)

/** Android 側 (InputDevice API) から見た仮想パッドの認識状況 */
data class AndroidPadInfo(
    val found: Boolean,
    val name: String? = null,
    val vendor: Int = 0,
    val product: Int = 0,
    val sourcesGamepad: Boolean = false,
    val sourcesJoystick: Boolean = false,
    val motionAxes: List<String> = emptyList()
)

/** SELFTEST の結果 */
data class SelfTestResult(
    val ok: Boolean,
    val detail: String,
    val errno: Int? = null
)

/**
 * アプリ全体で共有する状態 (UI が監視する)。
 * サーバ・接続層が書き込み、Compose が読み取る。
 */
object AppState {
    private val _daemonStatus = MutableStateFlow<DaemonStatus>(DaemonStatus.Stopped)
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus.asStateFlow()

    private val _conversionActive = MutableStateFlow(false)
    val conversionActive: StateFlow<Boolean> = _conversionActive.asStateFlow()

    private val _rootStatus = MutableStateFlow<RootStatusInfo?>(null)
    val rootStatus: StateFlow<RootStatusInfo?> = _rootStatus.asStateFlow()

    private val _remoteDevices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val remoteDevices: StateFlow<List<RemoteDevice>> = _remoteDevices.asStateFlow()

    private val _padInfo = MutableStateFlow<PadInfo?>(null)
    val padInfo: StateFlow<PadInfo?> = _padInfo.asStateFlow()

    private val _androidPad = MutableStateFlow<AndroidPadInfo?>(null)
    val androidPad: StateFlow<AndroidPadInfo?> = _androidPad.asStateFlow()

    private val _sysInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val sysInfo: StateFlow<Map<String, String>> = _sysInfo.asStateFlow()

    private val _selfTest = MutableStateFlow<SelfTestResult?>(null)
    val selfTest: StateFlow<SelfTestResult?> = _selfTest.asStateFlow()

    private val _keylayoutInstalled = MutableStateFlow<Boolean?>(null)
    val keylayoutInstalled: StateFlow<Boolean?> = _keylayoutInstalled.asStateFlow()

    private val _evKeyboard = MutableStateFlow(0L)
    val evKeyboard: StateFlow<Long> = _evKeyboard.asStateFlow()

    private val _evMouse = MutableStateFlow(0L)
    val evMouse: StateFlow<Long> = _evMouse.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentPad = MutableStateFlow(PadState.NEUTRAL)
    val currentPad: StateFlow<PadState> = _currentPad.asStateFlow()

    private val _mousePreview = MutableStateFlow(0f to 0f)
    val mousePreview: StateFlow<Pair<Float, Float>> = _mousePreview.asStateFlow()

    /**
     * 割り当てキャプチャ用: daemon が取得した直近の入力 (type, code)。
     * 変換中は物理キーが daemon に GRAB されるため、Android 側の
     * KeyEvent では検出できない。daemon の EV 行から供給する。
     */
    private val _lastCapturedKey = MutableStateFlow<Pair<Int, Int>?>(null)
    val lastCapturedKey: StateFlow<Pair<Int, Int>?> = _lastCapturedKey.asStateFlow()

    /** キャプチャの世代番号。ダイアログ表示前の古い値を無視するために使う。 */
    private val _captureSeq = MutableStateFlow(0L)
    val captureSeq: StateFlow<Long> = _captureSeq.asStateFlow()

    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    val logs: StateFlow<List<LogLine>> = _logs.asStateFlow()

    data class RootStatusInfo(val granted: Boolean, val detail: String)
    data class LogLine(val time: String, val text: String)

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun setDaemonStatus(s: DaemonStatus) { _daemonStatus.value = s }
    fun setConversionActive(v: Boolean) { _conversionActive.value = v }
    fun setRootStatus(granted: Boolean, detail: String) {
        _rootStatus.value = RootStatusInfo(granted, detail)
    }
    fun setPadInfo(p: PadInfo?) { _padInfo.value = p }
    fun setAndroidPad(p: AndroidPadInfo?) { _androidPad.value = p }
    fun setSelfTest(r: SelfTestResult) { _selfTest.value = r }
    fun setKeylayoutInstalled(v: Boolean?) { _keylayoutInstalled.value = v }
    fun setLastError(msg: String?) { _lastError.value = msg }
    fun setCurrentPad(p: PadState) { _currentPad.value = p }
    fun setMousePreview(x: Float, y: Float) { _mousePreview.value = x to y }

    fun updateSysInfo(key: String, value: String) {
        val m = _sysInfo.value.toMutableMap()
        m[key] = value
        _sysInfo.value = m
    }

    fun setDevices(list: List<RemoteDevice>) { _remoteDevices.value = list }
    fun upsertDevice(d: RemoteDevice) {
        _remoteDevices.value = _remoteDevices.value.filterNot { it.eventn == d.eventn } + d
    }
    fun removeDevice(eventn: Int) {
        _remoteDevices.value = _remoteDevices.value.filterNot { it.eventn == eventn }
    }

    fun incKeyboardEvents() { _evKeyboard.value = _evKeyboard.value + 1 }
    fun incMouseEvents() { _evMouse.value = _evMouse.value + 1 }

    /** 割り当てキャプチャ対象の入力を記録する (押下=value 1 のみ) */
    fun noteCapturedKey(type: Int, code: Int) {
        _lastCapturedKey.value = type to code
        _captureSeq.value = _captureSeq.value + 1
    }

    fun clearCapturedKey() { _lastCapturedKey.value = null }

    /** 現在のキャプチャ世代。ダイアログは表示時の値を記録し、それより新しい入力のみ採用する。 */
    fun currentCaptureSeq(): Long = _captureSeq.value

    fun appendLog(text: String) {
        if (text.isBlank()) return
        val line = LogLine(timeFormat.format(Date()), text.take(200))
        val cur = _logs.value
        val next = (cur + line).takeLast(LOG_LIMIT)
        _logs.value = next
    }

    private const val LOG_LIMIT = 200
}
