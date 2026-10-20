package com.rootpad.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinuxKeysTest {

    @Test
    fun `英字キーの Linux コードが正しい`() {
        assertEquals(17, LinuxKeys.KEY_W)
        assertEquals(30, LinuxKeys.KEY_A)
        assertEquals(31, LinuxKeys.KEY_S)
        assertEquals(32, LinuxKeys.KEY_D)
        assertEquals(33, LinuxKeys.KEY_F)
        assertEquals(16, LinuxKeys.KEY_Q)
        assertEquals(18, LinuxKeys.KEY_E)
        assertEquals(19, LinuxKeys.KEY_R)
        assertEquals(44, LinuxKeys.KEY_Z)
        assertEquals(50, LinuxKeys.KEY_M)
        assertEquals(48, LinuxKeys.KEY_B)
    }

    @Test
    fun `Android キーコードから正しい Linux コードへ変換される`() {
        // Android: A=29..Z=54 連続 / Linux: キーボード行順のスキャンコード
        assertEquals(30, LinuxKeys.fromAndroidKeycode(29))   // A
        assertEquals(17, LinuxKeys.fromAndroidKeycode(51))   // W
        assertEquals(32, LinuxKeys.fromAndroidKeycode(32))   // D
        assertEquals(44, LinuxKeys.fromAndroidKeycode(54))   // Z
        assertEquals(16, LinuxKeys.fromAndroidKeycode(45))   // Q
        assertEquals(57, LinuxKeys.fromAndroidKeycode(62))   // Space
        assertEquals(15, LinuxKeys.fromAndroidKeycode(61))   // Tab
        assertEquals(1, LinuxKeys.fromAndroidKeycode(111))   // Esc
        assertEquals(42, LinuxKeys.fromAndroidKeycode(59))   // 左Shift
        assertEquals(29, LinuxKeys.fromAndroidKeycode(113))  // 左Ctrl
        assertEquals(2, LinuxKeys.fromAndroidKeycode(8))     // 1
        assertEquals(11, LinuxKeys.fromAndroidKeycode(7))    // 0
        assertEquals(103, LinuxKeys.fromAndroidKeycode(19))  // ↑
        assertEquals(59, LinuxKeys.fromAndroidKeycode(131))  // F1
        assertEquals(68, LinuxKeys.fromAndroidKeycode(140))  // F10
    }

    @Test
    fun `未対応キーは null`() {
        assertNull(LinuxKeys.fromAndroidKeycode(0))
        assertNull(LinuxKeys.fromAndroidKeycode(9999))
    }

    @Test
    fun `ラベル表示`() {
        val ja = ResolvedLang.JA
        val en = ResolvedLang.EN
        assertEquals("W", LinuxKeys.label(LinuxKeys.KEY_W, ja))
        assertEquals("Esc", LinuxKeys.label(LinuxKeys.KEY_ESC, ja))
        assertEquals("↑", LinuxKeys.label(LinuxKeys.KEY_UP, ja))
        assertEquals("マウス左", LinuxKeys.label(LinuxKeys.BTN_LEFT, ja))
        assertEquals("Mouse left", LinuxKeys.label(LinuxKeys.BTN_LEFT, en))
        assertEquals("左Ctrl", LinuxKeys.label(LinuxKeys.KEY_LEFTCTRL, ja))
        assertEquals("Left Ctrl", LinuxKeys.label(LinuxKeys.KEY_LEFTCTRL, en))
        assertEquals("Key(9999)", LinuxKeys.label(9999, ja))
    }

    @Test
    fun `アクションとカーブのラベルが言語別に切り替わる`() {
        assertEquals("A", PadAction.A.label(ResolvedLang.JA))
        assertEquals("左スティック 上", PadAction.LS_UP.label(ResolvedLang.JA))
        assertEquals("Left stick up", PadAction.LS_UP.label(ResolvedLang.EN))
        assertEquals("未割当", PadAction.NONE.label(ResolvedLang.JA))
        assertEquals("Unassigned", PadAction.NONE.label(ResolvedLang.EN))
        assertEquals("リニア", CurveType.LINEAR.label(ResolvedLang.JA))
        assertEquals("Linear", CurveType.LINEAR.label(ResolvedLang.EN))
        assertEquals("ホイール 上", ScrollDirection.UP.label(ResolvedLang.JA))
        assertEquals("Wheel up", ScrollDirection.UP.label(ResolvedLang.EN))
    }

    @Test
    fun `AUTO 言語は端末ロケールから解決される`() {
        // 言語テーブルが両言語とも全キーを持つことを保証する
        for (key in listOf(
            "nav.home", "common.unassigned", "btn.LS_UP", "curve.SMOOTH",
            "scroll.RIGHT", "key.mouse.extra", "profile.default_name"
        )) {
            assert(!Strings.get(ResolvedLang.JA, key).contains(".")) { "JA missing: $key" }
            assert(!Strings.get(ResolvedLang.EN, key).contains(".")) { "EN missing: $key" }
        }
    }

    @Test
    fun `正規化ヘルパー`() {
        val n = BridgeEngine.normalize(1f, 1f)
        assertEquals(0.7071f, n.first, 1e-3f)
        assertEquals(0.7071f, n.second, 1e-3f)
        val s = BridgeEngine.normalize(0.3f, 0f)
        assertEquals(0.3f, s.first, 1e-6f)
        assertEquals(0f, s.second, 1e-6f)
    }
}
