package com.rootpad.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * キーボード → ゲームパッド変換の単体テスト。
 * Linux イベント (type=1/EV_KEY) をエンジンに注入して検証する。
 */
class BridgeEngineKeyboardTest {

    private fun engine(
        keyBindings: Map<Int, Set<PadAction>> = defaultKeyBindings()
    ): BridgeEngine = BridgeEngine(
        MappingProfile(id = "t", name = "t", keyBindings = keyBindings)
    )

    private val t = 1000L

    private fun BridgeEngine.key(code: Int, down: Boolean, devid: Int = 1) =
        onLinuxEvent(devid, 1, code, if (down) 1 else 0, t)

    @Test
    fun `既定のキー割り当てが仕様どおり存在する`() {
        val b = defaultKeyBindings()
        assertEquals(PadAction.LS_UP, b[LinuxKeys.KEY_W]?.single())
        assertEquals(PadAction.LS_LEFT, b[LinuxKeys.KEY_A]?.single())
        assertEquals(PadAction.LS_DOWN, b[LinuxKeys.KEY_S]?.single())
        assertEquals(PadAction.LS_RIGHT, b[LinuxKeys.KEY_D]?.single())
        assertEquals(PadAction.A, b[LinuxKeys.KEY_SPACE]?.single())
        assertEquals(PadAction.B, b[LinuxKeys.KEY_Q]?.single())
        assertEquals(PadAction.X, b[LinuxKeys.KEY_E]?.single())
        assertEquals(PadAction.Y, b[LinuxKeys.KEY_R]?.single())
        assertEquals(PadAction.LB, b[LinuxKeys.KEY_LEFTSHIFT]?.single())
        assertEquals(PadAction.RB, b[LinuxKeys.KEY_F]?.single())
        assertEquals(PadAction.VIEW, b[LinuxKeys.KEY_TAB]?.single())
        assertEquals(PadAction.MENU, b[LinuxKeys.KEY_ESC]?.single())
        assertEquals(PadAction.LS_PRESS, b[LinuxKeys.KEY_LEFTCTRL]?.single())
        assertEquals(PadAction.RS_PRESS, b[LinuxKeys.KEY_Z]?.single())
        assertEquals(PadAction.LB, b[LinuxKeys.KEY_1]?.single())
        assertEquals(PadAction.RB, b[LinuxKeys.KEY_2]?.single())
    }

    @Test
    fun `単押しで左スティックが最大偏る`() {
        val e = engine()
        e.key(LinuxKeys.KEY_W, true)
        val s = e.tick(t)
        assertEquals(-32767, s.ly)
        assertEquals(0, s.lx)
    }

    @Test
    fun `斜め移動は正規化され最大値を超えない`() {
        val e = engine()
        e.key(LinuxKeys.KEY_W, true)
        e.key(LinuxKeys.KEY_D, true)
        val s = e.tick(t)
        // 1/sqrt(2) ≒ 0.7071
        assertEquals(23170.0, s.lx.toDouble(), 2.0)
        assertEquals(-23170.0, s.ly.toDouble(), 2.0)
        val len = Math.hypot(s.lx.toDouble(), s.ly.toDouble())
        assertTrue("正規化後の長さが軸最大値を超えない", len <= 32767.0 + 1.0)
    }

    @Test
    fun `4方向同時押しでも最大値を超えない`() {
        val e = engine()
        e.key(LinuxKeys.KEY_W, true)
        e.key(LinuxKeys.KEY_S, true)   // 反対方向 → 打ち消し
        e.key(LinuxKeys.KEY_A, true)
        e.key(LinuxKeys.KEY_D, true)
        val s = e.tick(t)
        assertEquals(0, s.lx)
        assertEquals(0, s.ly)
    }

    @Test
    fun `解放すると中立に戻る`() {
        val e = engine()
        e.key(LinuxKeys.KEY_A, true)
        assertTrue(e.tick(t).lx < 0)
        e.key(LinuxKeys.KEY_A, false)
        assertEquals(PadState.NEUTRAL, e.tick(t + 10))
    }

    @Test
    fun `自動リピートは無視される`() {
        val e = engine()
        e.key(LinuxKeys.KEY_SPACE, true)
        e.key(LinuxKeys.KEY_SPACE, true) // リピート
        e.key(LinuxKeys.KEY_SPACE, true)
        val s = e.tick(t)
        assertEquals(PadBits.A, s.buttons and PadBits.A)
        e.key(LinuxKeys.KEY_SPACE, false) // 1 回の解放で十分に解除できる
        assertEquals(0, e.tick(t + 5).buttons)
    }

    @Test
    fun `複数ボタン的同时押し`() {
        val e = engine()
        e.key(LinuxKeys.KEY_SPACE, true)  // A
        e.key(LinuxKeys.KEY_LEFTSHIFT, true) // LB
        e.key(LinuxKeys.KEY_2, true)      // RB (同じアクションに2キー)
        val s = e.tick(t)
        assertEquals(PadBits.A or PadBits.LB or PadBits.RB, s.buttons)
    }

    @Test
    fun `トリガーは full press で255`() {
        val e = engine(
            mapOf(
                LinuxKeys.KEY_C to setOf(PadAction.LT),
                LinuxKeys.KEY_V to setOf(PadAction.RT)
            )
        )
        e.key(LinuxKeys.KEY_C, true)
        e.key(LinuxKeys.KEY_V, true)
        val s = e.tick(t)
        assertEquals(255, s.lt)
        assertEquals(255, s.rt)
    }

    @Test
    fun `十字キーは hat 値になる`() {
        val e = engine()
        e.key(LinuxKeys.KEY_UP, true)
        e.key(LinuxKeys.KEY_RIGHT, true)
        val s = e.tick(t)
        assertEquals(1, s.hatX)
        assertEquals(-1, s.hatY)
        assertEquals(0, s.buttons and 0x7ff) // hat はボタンマスクに混入しない
    }

    @Test
    fun `デバイス切断時にそのデバイスの押下がすべて解除される`() {
        val e = engine()
        e.key(LinuxKeys.KEY_W, true, devid = 5)
        e.key(LinuxKeys.KEY_SPACE, true, devid = 5)
        assertTrue(e.tick(t).ly != 0)   // W は左スティック上 (ly が非 0)
        e.onDeviceRemoved(5)
        assertEquals(PadState.NEUTRAL, e.tick(t + 1))
    }

    @Test
    fun `未割り当てキーは無視される`() {
        val e = engine()
        assertFalse(e.key(LinuxKeys.KEY_M, true))
        assertEquals(PadState.NEUTRAL, e.tick(t))
    }

    @Test
    fun `releaseAll で全ての入力がクリアされる`() {
        val e = engine()
        e.key(LinuxKeys.KEY_W, true)
        e.key(LinuxKeys.KEY_SPACE, true)
        e.releaseAll()
        assertEquals(PadState.NEUTRAL, e.tick(t + 1))
        assertTrue(e.heldActions().isEmpty())
    }

    @Test
    fun `スティック押し込みボタン`() {
        val e = engine()
        e.key(LinuxKeys.KEY_LEFTCTRL, true)
        e.key(LinuxKeys.KEY_Z, true)
        val s = e.tick(t)
        assertEquals(PadBits.LS_PRESS or PadBits.RS_PRESS, s.buttons)
    }

    @Test
    fun `プロファイル変更は実行中に反映される`() {
        val e = engine(mapOf(LinuxKeys.KEY_M to setOf(PadAction.A)))
        e.key(LinuxKeys.KEY_M, true)
        assertEquals(PadBits.A, e.tick(t).buttons)
        e.releaseAll()
        e.updateProfile(
            MappingProfile(
                id = "t", name = "t",
                keyBindings = mapOf(LinuxKeys.KEY_M to setOf(PadAction.B)),
                mouse = MouseSettings()
            )
        )
        e.key(LinuxKeys.KEY_M, true)
        assertEquals(PadBits.B, e.tick(t + 1).buttons)
    }

    // ---- 重複割り当て (1キー → 複数アクション) ----

    @Test
    fun `1つのキーに複数アクションを割り当てると同時押しになる`() {
        val e = engine(mapOf(LinuxKeys.KEY_M to setOf(PadAction.A, PadAction.LB)))
        e.key(LinuxKeys.KEY_M, true)
        val s = e.tick(t)
        assertEquals(PadBits.A or PadBits.LB, s.buttons)
        e.key(LinuxKeys.KEY_M, false)
        assertEquals(0, e.tick(t + 5).buttons)
    }

    @Test
    fun `重複割り当てでも一方のみ変更できる`() {
        val e = engine(mapOf(LinuxKeys.KEY_M to setOf(PadAction.A, PadAction.X)))
        e.key(LinuxKeys.KEY_M, true)
        assertEquals(PadBits.A or PadBits.X, e.tick(t).buttons)
        // X を外しても A は残る
        val only = engine(mapOf(LinuxKeys.KEY_M to setOf(PadAction.A)))
        only.key(LinuxKeys.KEY_M, true)
        assertEquals(PadBits.A, only.tick(t).buttons)
    }
}
