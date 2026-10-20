package com.rootpad.bridge.service

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.rootpad.bridge.core.AndroidPadInfo

/**
 * 公開 InputDevice API から Android がどう仮想パッドを認識しているかを照会する。
 * これは「uinput に書いた結果」ではなく「Android の InputReader が最終的に
 * どう分類したか」を返すため、認識確認の一次情報になる。
 */
object AndroidInputProbe {

    data class LocalInputDevice(
        val id: Int,
        val name: String,
        val sources: Int,
        val isGamepad: Boolean,
        val isKeyboard: Boolean,
        val isMouse: Boolean
    )

    /** 変換で使っている仮想パッド (045E:028E) を探す */
    fun findVirtualPad(): AndroidPadInfo {
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (dev.vendorId == 0x045e && dev.productId == 0x028e) {
                val axes = mutableListOf<String>()
                for (axis in intArrayOf(
                    MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                    MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                    MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
                    MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
                )) {
                    val range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
                    if (range != null) axes.add(axisName(axis))
                }
                return AndroidPadInfo(
                    found = true,
                    name = dev.name,
                    vendor = dev.vendorId,
                    product = dev.productId,
                    sourcesGamepad = dev.sources and InputDevice.SOURCE_GAMEPAD != 0,
                    sourcesJoystick = dev.sources and InputDevice.SOURCE_JOYSTICK != 0,
                    motionAxes = axes
                )
            }
        }
        return AndroidPadInfo(found = false)
    }

    /** 接続中の物理キーボード/マウス (参考表示) */
    fun listLocalInput(): List<LocalInputDevice> {
        val result = mutableListOf<LocalInputDevice>()
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            val isKbd = dev.sources and InputDevice.SOURCE_KEYBOARD != 0 &&
                dev.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            val isMouse = dev.sources and InputDevice.SOURCE_MOUSE != 0
            val isPad = dev.sources and InputDevice.SOURCE_GAMEPAD != 0
            if (isKbd || isMouse || isPad) {
                result.add(
                    LocalInputDevice(
                        id = dev.id,
                        name = dev.name,
                        sources = dev.sources,
                        isGamepad = isPad,
                        isKeyboard = isKbd,
                        isMouse = isMouse
                    )
                )
            }
        }
        return result
    }

    private fun axisName(axis: Int): String = when (axis) {
        MotionEvent.AXIS_X -> "X"
        MotionEvent.AXIS_Y -> "Y"
        MotionEvent.AXIS_Z -> "Z(LT)"
        MotionEvent.AXIS_RZ -> "RZ(RT)"
        MotionEvent.AXIS_RX -> "RX"
        MotionEvent.AXIS_RY -> "RY"
        MotionEvent.AXIS_HAT_X -> "HAT_X"
        MotionEvent.AXIS_HAT_Y -> "HAT_Y"
        else -> "axis$axis"
    }

    /** InputDevice 照会はブロッキングし得るので時間計測して返す */
    fun timedFindPad(): Pair<AndroidPadInfo, Long> {
        val t0 = SystemClock.elapsedRealtime()
        val info = findVirtualPad()
        return info to (SystemClock.elapsedRealtime() - t0)
    }
}
