package com.rootpad.bridge.core

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * キーボード/マウス → ゲームパッド変換エンジン (純Kotlin・非依存・単体テスト対象)。
 *
 * - キー: 押下/長押し(保持)/解放を正確に処理。自動リピートは無視。
 * - 斜め移動: ベクトルを正規化して最大値を超えないようにする。
 * - マウス: 相対移動量から右スティックの傾きを生成し、停止後は指定時間で
 *   滑らかに中央へ復帰する (座標の単純置換ではない)。
 *
 * スレッド安全性: サーバの入力スレッド(on*)と tick スレッドから使われるが、
 * 単一の同期ブロックで保護している。
 */
class BridgeEngine(profile: MappingProfile) {

    private val lock = Any()

    private var keyBindings: Map<Int, Set<PadAction>> = profile.keyBindings
    private var mouseButtonBindings: Map<Int, PadAction> = profile.mouse.buttonBindings
    private val mouseStick = MouseStickEngine(profile.mouse)

    /**
     * (devid, linuxCode) → 押下中のアクション集合。
     * 1 つのキーに複数アクションを割り当てられるため Set で保持する。
     */
    private val pressed = HashMap<Pair<Int, Int>, Set<PadAction>>()

    /** プロファイル変更 (実行中でも反映) */
    fun updateProfile(newProfile: MappingProfile) = synchronized(lock) {
        keyBindings = newProfile.keyBindings
        mouseButtonBindings = newProfile.mouse.buttonBindings
        mouseStick.updateSettings(newProfile.mouse)
        // 割り当てから消えたアクションは離す (押しっぱなし防止)
        val all = (newProfile.keyBindings.values.flatten() +
            newProfile.mouse.buttonBindings.values).toSet()
        pressed.entries.removeAll { (_, actions) ->
            actions.any { it != PadAction.NONE && it !in all }
        }
    }

    /**
     * 生の Linux 入力イベントを受け付ける。
     * @return 変換に使用した (Android へは転送しない想定の) 場合は true
     */
    fun onLinuxEvent(devid: Int, type: Int, code: Int, value: Int, nowMs: Long): Boolean {
        synchronized(lock) {
            return when (type) {
                EVENT_KEY -> onKey(devid, code, value != 0)
                EVENT_REL -> {
                    when (code) {
                        REL_X, REL_Y -> {
                            mouseStick.onMove(
                                if (code == REL_X) value else 0,
                                if (code == REL_Y) value else 0,
                                nowMs
                            )
                            true
                        }
                        // ホイールは 1 ノッチごとに ±1 が来る。押下の瞬間 (value!=0) のみ扱い、
                        // 一定時間後に自動解放するため MouseStickEngine 側で扱う。
                        REL_WHEEL -> {
                            mouseStick.onScroll(vertical = value, horizontal = 0, nowMs = nowMs)
                            true
                        }
                        REL_HWHEEL -> {
                            mouseStick.onScroll(vertical = 0, horizontal = value, nowMs = nowMs)
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }
    }

    private fun onKey(devid: Int, linuxCode: Int, down: Boolean): Boolean {
        val key = devid to linuxCode
        val actions = if (down) {
            keyBindings[linuxCode]
                ?: mouseButtonBindings[linuxCode]?.let { setOf(it) }
        } else {
            pressed[key]
                ?: mouseButtonBindings[linuxCode]?.let { setOf(it) }
                ?: keyBindings[linuxCode]
        } ?: return false

        val effective = actions.filter { it != PadAction.NONE }.toSet()
        if (effective.isEmpty()) return false

        if (down) {
            // 自動リピートは無視 (既に押下済みなら状態変更なし)
            if (!pressed.containsKey(key)) pressed[key] = effective
        } else {
            // 解放は押下時と同じアクションを確実に解除する
            pressed.remove(key)
        }
        return true
    }

    /** 指定デバイスの全ての押下を解除 (ケーブル抜き・デバイス消失時) */
    fun onDeviceRemoved(devid: Int) = synchronized(lock) {
        pressed.keys.removeAll { it.first == devid }
    }

    /** 全入力を解除 (サービス停止時) */
    fun releaseAll() = synchronized(lock) {
        pressed.clear()
        mouseStick.reset()
    }

    /** 現在の押下中アクション (UI 表示・テスト用) */
    fun heldActions(): Set<PadAction> = synchronized(lock) {
        pressed.values.flatten().toSet()
    }

    /**
     * ゲームパッド状態を組み立てて返す。呼び出し側は変化時のみ daemon へ送る。
     */
    fun tick(nowMs: Long): PadState = synchronized(lock) {
        var buttons = 0
        var triggerL = 0
        var triggerR = 0
        var hatX = 0
        var hatY = 0
        var lsXf = 0f
        var lsYf = 0f
        var rsXf = 0f
        var rsYf = 0f

        for (action in pressed.values.flatten()) {
            val bit = PadBits.of(action)
            if (bit != 0) buttons = buttons or bit
            when (action) {
                PadAction.LT -> triggerL = 255
                PadAction.RT -> triggerR = 255
                PadAction.DPAD_UP -> hatY = -1
                PadAction.DPAD_DOWN -> hatY = 1
                PadAction.DPAD_LEFT -> hatX = -1
                PadAction.DPAD_RIGHT -> hatX = 1
                PadAction.LS_UP -> lsYf -= 1f
                PadAction.LS_DOWN -> lsYf += 1f
                PadAction.LS_LEFT -> lsXf -= 1f
                PadAction.LS_RIGHT -> lsXf += 1f
                PadAction.RS_UP -> rsYf -= 1f
                PadAction.RS_DOWN -> rsYf += 1f
                PadAction.RS_LEFT -> rsXf -= 1f
                PadAction.RS_RIGHT -> rsXf += 1f
                else -> Unit
            }
        }

        // スティック方向は正規化 (斜めでも最大値を超えない)
        val ls = normalize(lsXf, lsYf)
        val (mx, my) = mouseStick.tick(nowMs)

        // ホイール割り当て (スクロール 1 ノッチ = 一定時間押下)
        val wheel = mouseStick.pressedScrollActions(nowMs)

        return PadState(
            buttons = buttons or wheel.fold(0) { acc, a -> acc or PadBits.of(a) },
            lx = (ls.first * AXIS_MAX).roundToInt(),
            ly = (ls.second * AXIS_MAX).roundToInt(),
            rx = (mx * AXIS_MAX).roundToInt(),
            ry = (my * AXIS_MAX).roundToInt(),
            lt = maxOf(triggerL, if (PadAction.LT in wheel) 255 else 0),
            rt = maxOf(triggerR, if (PadAction.RT in wheel) 255 else 0),
            hatX = hatX + wheel.fold(0) { acc, a -> acc + hatXOf(a) }.coerceIn(-1, 1),
            hatY = hatY + wheel.fold(0) { acc, a -> acc + hatYOf(a) }.coerceIn(-1, 1)
        )
    }

    private fun hatXOf(a: PadAction): Int = when (a) {
        PadAction.DPAD_LEFT, PadAction.LS_LEFT, PadAction.RS_LEFT -> -1
        PadAction.DPAD_RIGHT, PadAction.LS_RIGHT, PadAction.RS_RIGHT -> 1
        else -> 0
    }

    private fun hatYOf(a: PadAction): Int = when (a) {
        PadAction.DPAD_UP, PadAction.LS_UP, PadAction.RS_UP -> -1
        PadAction.DPAD_DOWN, PadAction.LS_DOWN, PadAction.RS_DOWN -> 1
        else -> 0
    }

    /** マウスプレビュー用 (UI) */
    fun mousePreview(): Pair<Float, Float> = synchronized(lock) { mouseStick.preview() }

    companion object {
        const val AXIS_MAX = 32767
        const val EVENT_KEY = 1
        const val EVENT_REL = 2
        const val REL_X = 0
        const val REL_Y = 1
        const val REL_WHEEL = 8
        const val REL_HWHEEL = 6

        /** 単位ベクトルへ正規化 (ゼロならそのまま) */
        fun normalize(x: Float, y: Float): Pair<Float, Float> {
            val len = hypot(x, y)
            return if (len > 1f) x / len to y / len else x to y
        }
    }
}

/**
 * マウス相対移動 → 右アナログスティック変換。
 *
 *  - 感度 (X/Y 独立)・軸反転・加速度・カーブ・最大値制限
 *  - 放射状デッドゾーン
 *  - 移動停止後 idleReturnMs で減衰開始、decayMs で正確に 0 へ復帰
 */
class MouseStickEngine(private var settings: MouseSettings) {

    private var pendingX = 0.0
    private var pendingY = 0.0
    private var outX = 0.0
    private var outY = 0.0
    private var lastMoveMs = Long.MIN_VALUE
    private var lastTickMs = Long.MIN_VALUE

    /**
     * ホイールの押し込み状態。
     * スクロールは離散イベントなので、最後のノッチから一定時間だけ「押下」として扱い、
     * ゲーム側が確実にクリックとして検出できるようにする。
     */
    private val scrollHeld = HashMap<ScrollDirection, Long>()

    fun updateSettings(s: MouseSettings) {
        settings = s
    }

    fun reset() {
        pendingX = 0.0
        pendingY = 0.0
        outX = 0.0
        outY = 0.0
        lastMoveMs = Long.MIN_VALUE
        lastTickMs = Long.MIN_VALUE
        scrollHeld.clear()
    }

    /** 相対移動量の取り込み (invert はここで適用) */
    fun onMove(dx: Int, dy: Int, nowMs: Long) {
        if (dx == 0 && dy == 0) return
        pendingX += if (settings.invertX) -dx else dx
        pendingY += if (settings.invertY) -dy else dy
        lastMoveMs = nowMs
    }

    /** ホイール 1 ノッチを取り込む (value は通常 ±1) */
    fun onScroll(vertical: Int, horizontal: Int, nowMs: Long) {
        if (vertical > 0) scrollHeld[ScrollDirection.UP] = nowMs
        else if (vertical < 0) scrollHeld[ScrollDirection.DOWN] = nowMs
        if (horizontal > 0) scrollHeld[ScrollDirection.RIGHT] = nowMs
        else if (horizontal < 0) scrollHeld[ScrollDirection.LEFT] = nowMs
    }

    /**
     * 現在「押下」として扱うホイール方向に対応するアクション。
     * 保持時間 (SCROLL_HOLD_MS) を過ぎたものは自動的に解放する。
     */
    fun pressedScrollActions(nowMs: Long = System.currentTimeMillis()): Set<PadAction> {
        scrollHeld.entries.removeAll { nowMs - it.value > SCROLL_HOLD_MS }
        val bindings = settings.scrollBindings
        return scrollHeld.keys.mapNotNull { bindings[it] }
            .filter { it != PadAction.NONE }
            .toSet()
    }

    /** 1 ティックごとにスティック値を再計算する */
    fun tick(nowMs: Long): Pair<Float, Float> {
        val s = settings
        val dt = if (lastTickMs == Long.MIN_VALUE) TICK_MS else
            (nowMs - lastTickMs).coerceIn(1L, 100L)
        lastTickMs = nowMs

        if (pendingX != 0.0 || pendingY != 0.0) {
            // 加速度: 移動速度に応じて倍率を付与 (REF_PX_PER_MS で正規化、最大 2 倍)
            val speed = hypot(pendingX, pendingY) / dt
            val accelFactor = 1.0 + s.acceleration.coerceIn(0f, 1f) *
                (speed / REF_PX_PER_MS).coerceIn(0.0, 1.0)

            var nx = pendingX * s.sensitivityX.toDouble() * BASE * accelFactor
            var ny = pendingY * s.sensitivityY.toDouble() * BASE * accelFactor
            pendingX = 0.0
            pendingY = 0.0

            // カーブ適用: 大きさに応じた係数を掛ける (t = mag/REF で正規化)
            val mag = hypot(nx, ny)
            if (mag > 1e-9) {
                val t = (mag / REF_MAG).coerceIn(0.0, 1.0)
                val factor = when (s.curve) {
                    CurveType.LINEAR -> 1.0
                    CurveType.SQUARE -> t              // 低速域が更に精密に
                    CurveType.SMOOTH -> t * t * (3 - 2 * t) // スムーズステップ
                }
                nx *= factor
                ny *= factor
            }

            // 最大振幅制限
            val maxD = s.maxDeflection.coerceIn(0.1f, 1.0f).toDouble()
            outX = nx.coerceIn(-maxD, maxD)
            outY = ny.coerceIn(-maxD, maxD)
        } else if (lastMoveMs != Long.MIN_VALUE) {
            // 移動停止 → 一定時間後に減衰して中央へ復帰
            val idle = nowMs - lastMoveMs
            if (idle >= s.idleReturnMs + s.decayMs) {
                outX = 0.0
                outY = 0.0
            } else if (idle > s.idleReturnMs) {
                val progress = (idle - s.idleReturnMs).toDouble() / s.decayMs.coerceAtLeast(1L)
                val factor = (1.0 - progress).coerceIn(0.0, 1.0)
                outX *= factor
                outY *= factor
            }
        }

        // 放射状デッドゾーン
        val dz = s.deadzone.coerceIn(0f, 0.5f).toDouble()
        if (dz > 0 && hypot(outX, outY) < dz) {
            outX = 0.0
            outY = 0.0
        }
        return outX.toFloat() to outY.toFloat()
    }

    /** UI プレビュー用に [-1, 1] の float で返す */
    fun preview(): Pair<Float, Float> = outX.toFloat() to outY.toFloat()

    companion object {
        /** ティック間隔 (サービス側の更新周期と一致) */
        const val TICK_MS = 4L
        /** スティック換算の基準倍率: 感度1.0 で 1ティック約250px の移動で満傾き */
        const val BASE = 0.004
        /** 加速度参照速度 (px/ms) */
        const val REF_PX_PER_MS = 4.0
        /** カーブ整形の参照大きさ (満傾き ≒ 1.0) */
        const val REF_MAG = 1.0
        /** ホイール 1 ノッチを「押下」として保持する時間 (ms) */
        const val SCROLL_HOLD_MS = 60L
    }
}
