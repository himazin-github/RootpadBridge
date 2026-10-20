package com.rootpad.bridge.core

/**
 * 仮想ゲームパッドの状態と daemon とのプロトコル。
 * daemon (daemon_main.c) とビット割り当てが一致している必要がある。
 */
data class PadState(
    val buttons: Int = 0,
    val lx: Int = 0,
    val ly: Int = 0,
    val rx: Int = 0,
    val ry: Int = 0,
    val lt: Int = 0,
    val rt: Int = 0,
    val hatX: Int = 0,
    val hatY: Int = 0
) {
    /** PAD 送信用の 1 行 (daemon は応答しない片方向メッセージ) */
    fun encode(): String = "PAD $buttons $lx $ly $rx $ry $lt $rt $hatX $hatY"

    fun isNeutral(): Boolean = this == NEUTRAL

    companion object {
        val NEUTRAL = PadState()

        /** daemon からの応答やテスト用の復元 */
        fun decode(line: String): PadState? {
            val parts = line.trim().split(" ")
            if (parts.size < 10 || parts[0] != "PAD") return null
            return try {
                PadState(
                    buttons = parts[1].toInt(),
                    lx = parts[2].toInt().coerceIn(-32768, 32767),
                    ly = parts[3].toInt().coerceIn(-32768, 32767),
                    rx = parts[4].toInt().coerceIn(-32768, 32767),
                    ry = parts[5].toInt().coerceIn(-32768, 32767),
                    lt = parts[6].toInt().coerceIn(0, 255),
                    rt = parts[7].toInt().coerceIn(0, 255),
                    hatX = parts[8].toInt().coerceIn(-1, 1),
                    hatY = parts[9].toInt().coerceIn(-1, 1)
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

/** ボタンマスクのビット定義 (rootpad.h と同一) */
object PadBits {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val LB = 1 shl 4
    const val RB = 1 shl 5
    const val VIEW = 1 shl 6
    const val MENU = 1 shl 7
    const val GUIDE = 1 shl 8
    const val LS_PRESS = 1 shl 9
    const val RS_PRESS = 1 shl 10

    fun of(action: PadAction): Int = when (action) {
        PadAction.A -> A
        PadAction.B -> B
        PadAction.X -> X
        PadAction.Y -> Y
        PadAction.LB -> LB
        PadAction.RB -> RB
        PadAction.VIEW -> VIEW
        PadAction.MENU -> MENU
        PadAction.GUIDE -> GUIDE
        PadAction.LS_PRESS -> LS_PRESS
        PadAction.RS_PRESS -> RS_PRESS
        else -> 0
    }
}

object Proto {
    const val VERSION = 1
    const val SOCKET_NAME = "rootpad.bridge.daemon"
    const val PAD_NAME = "Microsoft X-Box 360 pad"
    const val PAD_VENDOR = 0x045e
    const val PAD_PRODUCT = 0x028e
}
