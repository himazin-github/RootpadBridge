package com.rootpad.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * マウス → 右スティック変換の単体テスト。
 */
class MouseStickEngineTest {

    private fun settings(
        sensitivityX: Float = 1f,
        sensitivityY: Float = 1f,
        invertX: Boolean = false,
        invertY: Boolean = false,
        deadzone: Float = 0f,
        acceleration: Float = 0f,
        curve: CurveType = CurveType.LINEAR,
        maxDeflection: Float = 1f,
        idleReturnMs: Long = 40,
        decayMs: Long = 60
    ) = MouseSettings(
        sensitivityX = sensitivityX, sensitivityY = sensitivityY,
        invertX = invertX, invertY = invertY,
        deadzone = deadzone, acceleration = acceleration,
        curve = curve, maxDeflection = maxDeflection,
        idleReturnMs = idleReturnMs, decayMs = decayMs
    )

    private fun engine(s: MouseSettings = settings()) = MouseStickEngine(s)

    @Test
    fun `移動方向がそのままスティック方向になる`() {
        val e = engine()
        e.onMove(dx = 100, dy = 0, nowMs = 1000)
        val (x, y) = e.tick(1004)
        assertTrue("右移動で右スティックが正", x > 0f)
        assertEquals(0f, y, 1e-6f)
    }

    @Test
    fun `上移動はネガティブY (stick up)`() {
        val e = engine()
        e.onMove(dx = 0, dy = -100, nowMs = 1000)
        val (_, y) = e.tick(1004)
        assertTrue("マウス上移動でスティック上 (負値)", y < 0f)
    }

    @Test
    fun `Y軸反転が効く`() {
        val e = engine(settings(invertY = true))
        e.onMove(dx = 0, dy = -100, nowMs = 1000)
        val (_, y) = e.tick(1004)
        assertTrue(y > 0f)
    }

    @Test
    fun `感度が出力に比例する`() {
        val e1 = engine(settings(sensitivityX = 1f))
        val e2 = engine(settings(sensitivityX = 2f))
        e1.onMove(100, 0, 1000)
        e2.onMove(100, 0, 1000)
        val x1 = e1.tick(1004).first
        val x2 = e2.tick(1004).first
        assertTrue("感度2倍で出力は約2倍", Math.abs(x2 - x1 * 2f) < 1e-3f)
    }

    @Test
    fun `最大振幅でクランプされる`() {
        val e = engine(settings(maxDeflection = 0.5f))
        e.onMove(10000, 0, 1000)
        val (x, _) = e.tick(1004)
        assertTrue(x <= 0.5f + 1e-6f)
        assertTrue(x > 0.4f)
    }

    @Test
    fun `デッドゾーン未満の出力はゼロになる`() {
        val e = engine(settings(deadzone = 0.2f))
        e.onMove(1, 0, 1000)
        val (x, y) = e.tick(1004)
        assertEquals(0f, x, 1e-6f)
        assertEquals(0f, y, 1e-6f)
    }

    @Test
    fun `加速度は速い移動ほど出力を増やす`() {
        val slow = engine(settings(acceleration = 1f))
        val fast = engine(settings(acceleration = 1f))
        slow.onMove(5, 0, 1000)
        fast.onMove(200, 0, 1000)
        val slowX = Math.abs(slow.tick(1004).first)
        val fastX = Math.abs(fast.tick(1004).first)
        assertTrue("高速移動ほど出力が大きい", fastX > slowX)
    }

    @Test
    fun `停止後は指定時間で中央に復帰する`() {
        val e = engine(settings(idleReturnMs = 40, decayMs = 60))
        e.onMove(500, 0, 1000)
        val (x0, _) = e.tick(1004)
        assertTrue(x0 != 0f)
        // 減衰中は 0 < |x| < 初値
        val (x1, _) = e.tick(1040 + 30)
        assertTrue("減衰中は残る", Math.abs(x1) > 0f && Math.abs(x1) < Math.abs(x0))
        // idleReturnMs + decayMs 経過後は厳密に 0
        val (x2, y2) = e.tick(1040 + 60)
        assertEquals(0f, x2, 0f)
        assertEquals(0f, y2, 0f)
    }

    @Test
    fun `連続移動中は復帰しない`() {
        val e = engine(settings(idleReturnMs = 10, decayMs = 10))
        e.onMove(300, 0, 1000)
        e.tick(1004)
        e.onMove(300, 0, 1020) // 移動を継続
        val (x, _) = e.tick(1024)
        assertTrue(x != 0f)
    }

    @Test
    fun `カーブは低速域を抑える (square)`() {
        val lin = engine(settings(curve = CurveType.LINEAR))
        val sq = engine(settings(curve = CurveType.SQUARE))
        lin.onMove(20, 0, 1000)
        sq.onMove(20, 0, 1000)
        val linX = Math.abs(lin.tick(1004).first)
        val sqX = Math.abs(sq.tick(1004).first)
        assertTrue("スクエアカーブは低速域で出力が小さい", sqX < linX)
    }

    @Test
    fun `reset で完全にクリアされる`() {
        val e = engine()
        e.onMove(400, 0, 1000)
        e.tick(1004)
        e.reset()
        val (x, y) = e.tick(1008)
        assertEquals(0f, x, 0f)
        assertEquals(0f, y, 0f)
    }

    @Test
    fun `大きな値でも NaN にならない`() {
        val e = engine(settings(acceleration = 1f, maxDeflection = 1f))
        e.onMove(Int.MAX_VALUE / 4, Int.MAX_VALUE / 4, 1000)
        val (x, y) = e.tick(1004)
        assertTrue(!x.isNaN() && !y.isNaN())
        assertTrue(x <= 1f && x >= -1f)
        assertTrue(y <= 1f && y >= -1f)
    }

    @Test
    fun `BridgeEngine 経由でマウスイベントが右スティックになる`() {
        val e = BridgeEngine(
            MappingProfile(
                id = "t", name = "t",
                keyBindings = defaultKeyBindings(),
                mouse = settings(deadzone = 0f)
            )
        )
        // EV_REL(2) REL_X(0)
        e.onLinuxEvent(1, 2, 0, 200, 1000)
        val s = e.tick(1004)
        assertTrue("右スティックXが反応", s.rx != 0)
        assertEquals("左スティックは無反応", 0, s.lx)
    }

    @Test
    fun `マウスボタン割り当てが機能する`() {
        val e = BridgeEngine(
            MappingProfile(
                id = "t", name = "t",
                keyBindings = emptyMap(),
                mouse = settings().copy(
                    buttonBindings = mapOf(
                        LinuxKeys.BTN_LEFT to PadAction.RT,
                        LinuxKeys.BTN_SIDE to PadAction.LB
                    )
                )
            )
        )
        e.onLinuxEvent(1, 1, LinuxKeys.BTN_LEFT, 1, 1000)
        e.onLinuxEvent(1, 1, LinuxKeys.BTN_SIDE, 1, 1000)
        val s = e.tick(1004)
        // RT/LT はアナログトリガーなのでボタンマスクには含まれない
        assertEquals(PadBits.LB, s.buttons)
        assertEquals(255, s.rt)
        e.onLinuxEvent(1, 1, LinuxKeys.BTN_LEFT, 0, 1010)
        assertEquals(PadBits.LB, e.tick(1014).buttons)
        assertEquals(0, e.tick(1014).rt)
    }

    // ---- 高感度レンジ (拡張後) の検証 ----

    @Test
    fun `最大感度ではわずかな移動でフル振幅に達する`() {
        val e = engine(settings(sensitivityX = MouseSettings.SENSITIVITY_MAX, deadzone = 0f))
        // 30px の小さな移動でも満傾き (1.0) に到達すること
        e.onMove(30, 0, 1000)
        val (x, _) = e.tick(1004)
        assertEquals(1f, Math.abs(x), 1e-6f)
    }

    @Test
    fun `感度上限はクランプされない (旧上限5を超えても有効)`() {
        val low = engine(settings(sensitivityX = 5f, deadzone = 0f))
        val high = engine(settings(sensitivityX = 25f, deadzone = 0f))
        // 5 倍ではフル振幅に届かない程度の移動
        low.onMove(20, 0, 1000)
        high.onMove(20, 0, 1000)
        val lowX = Math.abs(low.tick(1004).first)
        val highX = Math.abs(high.tick(1004).first)
        assertTrue("高感度の方が大きい", highX > lowX)
        assertEquals("25倍なら満傾き", 1f, highX, 1e-6f)
    }

    @Test
    fun `感度レンジ定数は妥当`() {
        assertTrue(MouseSettings.SENSITIVITY_MIN > 0f)
        assertTrue(MouseSettings.SENSITIVITY_MAX >= 20f)
        assertTrue(MouseSettings.SENSITIVITY_MAX > MouseSettings.SENSITIVITY_MIN)
    }

    // ---- ホイール (スクロール) の検証 ----

    @Test
    fun `上スクロールで十字キー上が押され一定時間後に解放される`() {
        val s = settings().copy(
            scrollBindings = mapOf(ScrollDirection.UP to PadAction.DPAD_UP)
        )
        val e = BridgeEngine(
            MappingProfile(id = "t", name = "t", keyBindings = emptyMap(), mouse = s)
        )
        // EV_REL(2) REL_WHEEL(8) +1
        assertTrue(e.onLinuxEvent(1, 2, 8, 1, 1000))
        assertEquals("十字上", -1, e.tick(1004).hatY)
        // 保持時間を過ぎると解放される
        assertEquals(0, e.tick(1004 + MouseStickEngine.SCROLL_HOLD_MS + 10).hatY)
    }

    @Test
    fun `下スクロールで十字キー下`() {
        val s = settings().copy(
            scrollBindings = mapOf(ScrollDirection.DOWN to PadAction.DPAD_DOWN)
        )
        val e = BridgeEngine(
            MappingProfile(id = "t", name = "t", keyBindings = emptyMap(), mouse = s)
        )
        e.onLinuxEvent(1, 2, 8, -1, 1000)
        assertEquals(1, e.tick(1004).hatY)
    }

    @Test
    fun `ホイールをボタンに割り当てられる`() {
        val s = settings().copy(
            scrollBindings = mapOf(
                ScrollDirection.UP to PadAction.A,
                ScrollDirection.DOWN to PadAction.B
            )
        )
        val e = BridgeEngine(
            MappingProfile(id = "t", name = "t", keyBindings = emptyMap(), mouse = s)
        )
        e.onLinuxEvent(1, 2, 8, 1, 1000) // 上 → A
        assertEquals(PadBits.A, e.tick(1004).buttons)
        e.onLinuxEvent(1, 2, 8, -1, 1010) // 下 → B (A は保持時間内でまだ有効)
        val buttons = e.tick(1014).buttons
        assertTrue(buttons and PadBits.B != 0)
    }

    @Test
    fun `水平ホイールも割り当て可能`() {
        val s = settings().copy(
            scrollBindings = mapOf(ScrollDirection.RIGHT to PadAction.RB)
        )
        val e = BridgeEngine(
            MappingProfile(id = "t", name = "t", keyBindings = emptyMap(), mouse = s)
        )
        e.onLinuxEvent(1, 2, 6, 1, 1000) // REL_HWHEEL +1 → 右
        assertEquals(PadBits.RB, e.tick(1004).buttons)
    }

    @Test
    fun `未割当のホイール方向は何も起こらない`() {
        val s = settings().copy(
            scrollBindings = mapOf(ScrollDirection.UP to PadAction.NONE)
        )
        val e = BridgeEngine(
            MappingProfile(id = "t", name = "t", keyBindings = emptyMap(), mouse = s)
        )
        e.onLinuxEvent(1, 2, 8, 1, 1000)
        assertEquals(PadState.NEUTRAL, e.tick(1004))
    }

    @Test
    fun `既定ホイール設定は上下が十字キー`() {
        val d = defaultScrollBindings()
        assertEquals(PadAction.DPAD_UP, d[ScrollDirection.UP])
        assertEquals(PadAction.DPAD_DOWN, d[ScrollDirection.DOWN])
    }
}
