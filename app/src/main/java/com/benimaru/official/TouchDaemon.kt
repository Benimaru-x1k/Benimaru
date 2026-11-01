package com.benimaru.official

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object TouchDaemon {

    private var target: Any? = null
    private var injectMethod: Method? = null
    private var threeArgs = false
    private var deviceId = 0

    @JvmStatic
    fun main(args: Array<String>) {
        if (!initInjector()) {
            println("FAIL")
            System.out.flush()
            System.exit(1)
            return
        }
        deviceId = findTouchDevice()
        println("READY")
        System.out.flush()

        val reader = BufferedReader(InputStreamReader(System.`in`))
        loop@ while (true) {
            val line = reader.readLine() ?: break@loop
            val p = line.trim().split(' ')
            try {
                when (p[0]) {
                    "T" -> tap(p[1].toFloat(), p[2].toFloat(), p[3].toLong())
                    "Q" -> break@loop
                }
            } catch (_: Throwable) {
            }
        }
        System.exit(0)
    }

    private fun tap(x: Float, y: Float, holdMs: Long) {
        val downTime = SystemClock.uptimeMillis()
        inject(motion(downTime, downTime, MotionEvent.ACTION_DOWN, x, y))
        if (holdMs > 0) Thread.sleep(holdMs)
        inject(motion(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y))
    }

    private fun motion(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        return MotionEvent.obtain(
            downTime, eventTime, action, 1, props, coords,
            0, 0, 1f, 1f, deviceId, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
    }

    private fun inject(ev: MotionEvent) {
        try {
            val m = injectMethod!!
            if (threeArgs) m.invoke(target, ev, 0, -1) else m.invoke(target, ev, 0)
        } finally {
            ev.recycle()
        }
    }

    private fun findTouchDevice(): Int = try {
        InputDevice.getDeviceIds().firstOrNull { id ->
            val d = InputDevice.getDevice(id)
            d != null && (d.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
        } ?: 0
    } catch (_: Throwable) {
        0
    }

    private fun bind(cls: Class<*>, instance: Any?): Boolean {
        if (instance == null) return false
        val intT = Int::class.javaPrimitiveType!!
        for (three in booleanArrayOf(false, true)) {
            try {
                val m = if (three) cls.getMethod("injectInputEvent", InputEvent::class.java, intT, intT)
                else cls.getMethod("injectInputEvent", InputEvent::class.java, intT)
                m.isAccessible = true
                target = instance
                injectMethod = m
                threeArgs = three
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private fun initInjector(): Boolean {
        try {
            val c = Class.forName("android.hardware.input.InputManagerGlobal")
            if (bind(c, c.getMethod("getInstance").invoke(null))) return true
        } catch (_: Throwable) {
        }
        try {
            val c = Class.forName("android.hardware.input.InputManager")
            if (bind(c, c.getMethod("getInstance").invoke(null))) return true
        } catch (_: Throwable) {
        }
        try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "input") as IBinder
            val iface = Class.forName("android.hardware.input.IInputManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
            if (bind(Class.forName("android.hardware.input.IInputManager"), iface)) return true
        } catch (_: Throwable) {
        }
        return false
    }
}
