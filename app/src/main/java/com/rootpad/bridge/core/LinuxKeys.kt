package com.rootpad.bridge.core

/**
 * Linux input subsystem のキーコード (linux/input-event-codes.h) と
 * Android KeyEvent のキーコードを相互変換する。
 * 変換エンジンは Linux コードで動作し、UI のキー割り当ては Android
 * コードを受け取って Linux コードへ変換する。
 */
object LinuxKeys {
    const val KEY_ESC = 1
    const val KEY_1 = 2
    const val KEY_2 = 3
    const val KEY_3 = 4
    const val KEY_4 = 5
    const val KEY_5 = 6
    const val KEY_6 = 7
    const val KEY_7 = 8
    const val KEY_8 = 9
    const val KEY_9 = 10
    const val KEY_0 = 11
    const val KEY_MINUS = 12
    const val KEY_EQUAL = 13
    const val KEY_BACKSPACE = 14
    const val KEY_TAB = 15
    const val KEY_Q = 16
    const val KEY_W = 17
    const val KEY_E = 18
    const val KEY_R = 19
    const val KEY_T = 20
    const val KEY_Y = 21
    const val KEY_U = 22
    const val KEY_I = 23
    const val KEY_O = 24
    const val KEY_P = 25
    const val KEY_LEFTBRACE = 26
    const val KEY_RIGHTBRACE = 27
    const val KEY_ENTER = 28
    const val KEY_LEFTCTRL = 29
    const val KEY_A = 30
    const val KEY_S = 31
    const val KEY_D = 32
    const val KEY_F = 33
    const val KEY_G = 34
    const val KEY_H = 35
    const val KEY_J = 36
    const val KEY_K = 37
    const val KEY_L = 38
    const val KEY_SEMICOLON = 39
    const val KEY_APOSTROPHE = 40
    const val KEY_GRAVE = 41
    const val KEY_LEFTSHIFT = 42
    const val KEY_BACKSLASH = 43
    const val KEY_Z = 44
    const val KEY_X = 45
    const val KEY_C = 46
    const val KEY_V = 47
    const val KEY_B = 48
    const val KEY_N = 49
    const val KEY_M = 50
    const val KEY_COMMA = 51
    const val KEY_DOT = 52
    const val KEY_SLASH = 53
    const val KEY_RIGHTSHIFT = 54
    const val KEY_LEFTALT = 56
    const val KEY_SPACE = 57
    const val KEY_CAPSLOCK = 58
    const val KEY_F1 = 59
    const val KEY_F2 = 60
    const val KEY_F3 = 61
    const val KEY_F4 = 62
    const val KEY_F5 = 63
    const val KEY_F6 = 64
    const val KEY_F7 = 65
    const val KEY_F8 = 66
    const val KEY_F9 = 67
    const val KEY_F10 = 68
    const val KEY_F11 = 87
    const val KEY_F12 = 88
    const val KEY_KPENTER = 96
    const val KEY_RIGHTCTRL = 97
    const val KEY_RIGHTALT = 100
    const val KEY_HOME = 102
    const val KEY_UP = 103
    const val KEY_PAGEUP = 104
    const val KEY_LEFT = 105
    const val KEY_RIGHT = 106
    const val KEY_END = 107
    const val KEY_DOWN = 108
    const val KEY_PAGEDOWN = 109
    const val KEY_INSERT = 110
    const val KEY_DELETE = 111
    const val KEY_PAUSE = 119
    const val KEY_LEFTMETA = 125
    const val KEY_RIGHTMETA = 126

    const val BTN_LEFT = 272
    const val BTN_RIGHT = 273
    const val BTN_MIDDLE = 274
    const val BTN_SIDE = 275
    const val BTN_EXTRA = 276

    /** Linux の英字キーはスキャンコード由来で連続しない (A..Z 順に並べた表) */
    private val LETTERS_A_TO_Z = intArrayOf(
        30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50,
        49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44
    )

    fun letter(code: Char): Int? =
        if (code in 'A'..'Z') LETTERS_A_TO_Z[code - 'A'] else null

    /** 表示名 (UI 用)。言語依存のマウス名などは [label] を使う。 */
    val LABELS: Map<Int, String> = buildMap {
        put(KEY_ESC, "Esc"); put(KEY_TAB, "Tab"); put(KEY_ENTER, "Enter")
        put(KEY_BACKSPACE, "Backspace"); put(KEY_SPACE, "Space")
        put(KEY_CAPSLOCK, "CapsLock"); put(KEY_GRAVE, "`")
        put(KEY_MINUS, "-"); put(KEY_EQUAL, "=")
        put(KEY_LEFTBRACE, "["); put(KEY_RIGHTBRACE, "]")
        put(KEY_BACKSLASH, "\\"); put(KEY_SEMICOLON, ";")
        put(KEY_APOSTROPHE, "'"); put(KEY_COMMA, ",")
        put(KEY_DOT, "."); put(KEY_SLASH, "/")
        put(KEY_UP, "↑"); put(KEY_DOWN, "↓")
        put(KEY_LEFT, "←"); put(KEY_RIGHT, "→")
        put(KEY_HOME, "Home"); put(KEY_END, "End")
        put(KEY_PAGEUP, "PageUp"); put(KEY_PAGEDOWN, "PageDown")
        put(KEY_INSERT, "Insert"); put(KEY_DELETE, "Delete")
        put(KEY_PAUSE, "Pause")
        for (c in 'A'..'Z') put(LETTERS_A_TO_Z[c - 'A'], c.toString())
        for (i in 1..9) put(KEY_1 + (i - 1), i.toString())
        put(KEY_0, "0")
        for (i in 1..12) {
            val code = if (i <= 10) KEY_F1 + (i - 1) else if (i == 11) KEY_F11 else KEY_F12
            put(code, "F$i")
        }
    }

    /** 言語依存のキー名 (修飾キー/マウスボタン) の上書きテーブル */
    private val KEY_OVERRIDE: Map<Int, String> = mapOf(
        KEY_LEFTCTRL to "key.LEFTCTRL",
        KEY_RIGHTCTRL to "key.RIGHTCTRL",
        KEY_LEFTSHIFT to "key.LEFTSHIFT",
        KEY_RIGHTSHIFT to "key.RIGHTSHIFT",
        KEY_LEFTALT to "key.LEFTALT",
        KEY_RIGHTALT to "key.RIGHTALT",
        KEY_LEFTMETA to "key.LEFTMETA",
        KEY_RIGHTMETA to "key.RIGHTMETA",
        BTN_LEFT to "key.mouse.left",
        BTN_RIGHT to "key.mouse.right",
        BTN_MIDDLE to "key.mouse.middle",
        BTN_SIDE to "key.mouse.side",
        BTN_EXTRA to "key.mouse.extra"
    )

    /** 表示名 (言語別)。未知コードは Key(code)。 */
    fun label(code: Int, lang: ResolvedLang): String {
        KEY_OVERRIDE[code]?.let { return Strings.get(lang, it) }
        return LABELS[code] ?: "Key($code)"
    }

    /** Android の KEYCODE_* → Linux KEY_* (未対応は null) */
    fun fromAndroidKeycode(androidKeycode: Int): Int? = when {
        androidKeycode in 29..54 -> LETTERS_A_TO_Z[androidKeycode - 29]  // A..Z
        androidKeycode in 8..16 -> KEY_1 + (androidKeycode - 8)          // 1..9
        androidKeycode == 7 -> KEY_0
        androidKeycode == 61 -> KEY_TAB
        androidKeycode == 62 -> KEY_SPACE
        androidKeycode == 66 -> KEY_ENTER
        androidKeycode == 67 -> KEY_BACKSPACE
        androidKeycode == 68 -> KEY_GRAVE
        androidKeycode == 69 -> KEY_MINUS
        androidKeycode == 70 -> KEY_EQUAL
        androidKeycode == 71 -> KEY_LEFTBRACE
        androidKeycode == 72 -> KEY_RIGHTBRACE
        androidKeycode == 73 -> KEY_BACKSLASH
        androidKeycode == 74 -> KEY_SEMICOLON
        androidKeycode == 75 -> KEY_APOSTROPHE
        androidKeycode == 76 -> KEY_SLASH
        androidKeycode == 111 -> KEY_ESC
        androidKeycode == 112 -> KEY_DELETE
        androidKeycode == 113 -> KEY_LEFTCTRL
        androidKeycode == 114 -> KEY_RIGHTCTRL
        androidKeycode == 115 -> KEY_CAPSLOCK
        androidKeycode == 57 -> KEY_LEFTALT
        androidKeycode == 58 -> KEY_RIGHTALT
        androidKeycode == 59 -> KEY_LEFTSHIFT
        androidKeycode == 60 -> KEY_RIGHTSHIFT
        androidKeycode == 19 -> KEY_UP
        androidKeycode == 20 -> KEY_DOWN
        androidKeycode == 21 -> KEY_LEFT
        androidKeycode == 22 -> KEY_RIGHT
        androidKeycode == 23 -> KEY_ENTER       // DPAD_CENTER
        androidKeycode == 92 -> KEY_PAGEUP
        androidKeycode == 93 -> KEY_PAGEDOWN
        androidKeycode == 122 -> KEY_HOME
        androidKeycode == 123 -> KEY_END
        androidKeycode == 124 -> KEY_INSERT
        androidKeycode in 131..142 -> KEY_F1 + (androidKeycode - 131)    // F1..F12
        androidKeycode == 117 -> KEY_LEFTMETA
        androidKeycode == 118 -> KEY_RIGHTMETA
        else -> null
    }
}

