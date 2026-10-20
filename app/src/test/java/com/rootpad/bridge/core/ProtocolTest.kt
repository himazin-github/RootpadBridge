package com.rootpad.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun `PAD メッセージの往復変換`() {
        val s = PadState(
            buttons = PadBits.A or PadBits.RB,
            lx = -1234, ly = 2345, rx = -32768, ry = 32767,
            lt = 128, rt = 255, hatX = -1, hatY = 1
        )
        val line = s.encode()
        assertTrue(line.startsWith("PAD "))
        val decoded = PadState.decode(line)
        assertEquals(s, decoded)
    }

    @Test
    fun `中立状態のエンコード`() {
        val line = PadState.NEUTRAL.encode()
        assertEquals("PAD 0 0 0 0 0 0 0 0 0", line)
        assertEquals(PadState.NEUTRAL, PadState.decode(line))
        assertTrue(PadState.NEUTRAL.isNeutral())
    }

    @Test
    fun `不正な行は null`() {
        assertEquals(null, PadState.decode("EV 3 1 30 1"))
        assertEquals(null, PadState.decode("PAD 1 2 3"))
        assertEquals(null, PadState.decode("PAD a b c d e f g h i"))
    }

    @Test
    fun `ボタンビットは daemon 定義と一致`() {
        assertEquals(1 shl 0, PadBits.A)
        assertEquals(1 shl 1, PadBits.B)
        assertEquals(1 shl 2, PadBits.X)
        assertEquals(1 shl 3, PadBits.Y)
        assertEquals(1 shl 4, PadBits.LB)
        assertEquals(1 shl 5, PadBits.RB)
        assertEquals(1 shl 6, PadBits.VIEW)
        assertEquals(1 shl 7, PadBits.MENU)
        assertEquals(1 shl 8, PadBits.GUIDE)
        assertEquals(1 shl 9, PadBits.LS_PRESS)
        assertEquals(1 shl 10, PadBits.RS_PRESS)
    }

    @Test
    fun `PadAction からボタンビットへ`() {
        assertEquals(PadBits.A, PadBits.of(PadAction.A))
        assertEquals(PadBits.MENU, PadBits.of(PadAction.MENU))
        assertEquals(0, PadBits.of(PadAction.LS_UP))
        assertEquals(0, PadBits.of(PadAction.NONE))
    }

    @Test
    fun `範囲外値はクランプされる`() {
        val d = PadState.decode("PAD 0 99999 -99999 0 0 999 -5 5 -9")
        assertNotNull(d)
        // 仮想パッドの軸範囲は -32768..32767 (uinput_pad.c と一致)
        assertEquals(32767, d!!.lx)
        assertEquals(-32768, d.ly)
        assertEquals(255, d.lt)
        assertEquals(0, d.rt)
        assertEquals(1, d.hatX)
        assertEquals(-1, d.hatY)
    }
}
