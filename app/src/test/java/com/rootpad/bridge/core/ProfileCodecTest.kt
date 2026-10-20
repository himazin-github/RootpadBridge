package com.rootpad.bridge.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `プロファイルの JSON 往復変換`() {
        val p = MappingProfile(
            id = "abc123",
            name = "FPS 設定",
            keyBindings = defaultKeyBindings(),
            mouse = MouseSettings(
                sensitivityX = 1.5f,
                invertY = true,
                curve = CurveType.SQUARE,
                buttonBindings = mapOf(
                    LinuxKeys.BTN_LEFT to PadAction.RT,
                    LinuxKeys.BTN_RIGHT to PadAction.LT
                )
            ),
            grabDevices = false,
            installKeylayout = true,
            autoStartOnBoot = true
        )
        val text = json.encodeToString(MappingProfile.serializer(), p)
        val back = json.decodeFromString(MappingProfile.serializer(), text)
        assertEquals(p, back)
        assertEquals("FPS 設定", back.name)
        assertEquals(CurveType.SQUARE, back.mouse.curve)
        assertEquals(PadAction.RT, back.mouse.buttonBindings[LinuxKeys.BTN_LEFT])
    }

    @Test
    fun `既定プロファイルは仕様の初期値を持つ`() {
        val p = defaultProfile()
        assertEquals(PadAction.LS_UP, p.keyBindings[LinuxKeys.KEY_W]?.single())
        assertEquals(PadAction.A, p.keyBindings[LinuxKeys.KEY_SPACE]?.single())
        assertEquals(PadAction.RT, p.mouse.buttonBindings[LinuxKeys.BTN_LEFT])
        assertEquals(PadAction.LT, p.mouse.buttonBindings[LinuxKeys.BTN_RIGHT])
        assertTrue(p.grabDevices)
    }

    @Test
    fun `旧形式の単一割り当てを読み込める`() {
        val text = """{"id":"x","name":"n","futureField":123,"keyBindings":{"17":"A"}}"""
        val p = json.decodeFromString(MappingProfile.serializer(), text)
        assertEquals("x", p.id)
        assertEquals(setOf(PadAction.A), p.keyBindings[LinuxKeys.KEY_W])
        assertNotNull(p.mouse)
    }

    @Test
    fun `新形式の複数割り当てを読み書きできる`() {
        val text = """{"id":"x","name":"n","keyBindings":{"17":["A","LB"]}}"""
        val p = json.decodeFromString(MappingProfile.serializer(), text)
        assertEquals(setOf(PadAction.A, PadAction.LB), p.keyBindings[LinuxKeys.KEY_W])
        val again = json.decodeFromString(
            MappingProfile.serializer(),
            json.encodeToString(MappingProfile.serializer(), p)
        )
        assertEquals(p, again)
    }

    @Test
    fun `不正なアクション名は除外される`() {
        val text = """{"id":"x","name":"n","keyBindings":{"17":"NOT_EXISTS"}}"""
        val p = json.decodeFromString(MappingProfile.serializer(), text)
        // 不正なアクションは NONE 扱いとなり保存されない
        assertTrue(p.keyBindings[LinuxKeys.KEY_W].isNullOrEmpty())
        assertEquals(PadAction.NONE, PadAction.fromName("NOPE"))
    }

    @Test
    fun `ホイール割り当ても JSON 往復できる`() {
        val p = defaultProfile().let {
            it.copy(mouse = it.mouse.copy(
                scrollBindings = mapOf(
                    ScrollDirection.UP to PadAction.DPAD_UP,
                    ScrollDirection.LEFT to PadAction.LB
                )
            ))
        }
        val back = json.decodeFromString(
            MappingProfile.serializer(),
            json.encodeToString(MappingProfile.serializer(), p)
        )
        assertEquals(PadAction.DPAD_UP, back.mouse.scrollBindings[ScrollDirection.UP])
        assertEquals(PadAction.LB, back.mouse.scrollBindings[ScrollDirection.LEFT])
    }
}
