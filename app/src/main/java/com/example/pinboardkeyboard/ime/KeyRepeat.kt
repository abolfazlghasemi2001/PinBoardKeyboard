package com.example.pinboardkeyboard.ime

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View

private const val INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 55L

/**
 * Fires [action] on press and keeps firing it while the key stays pressed.
 * Used for backspace so holding it deletes continuously, like a real keyboard.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun View.setOnRepeatableClickListener(action: () -> Unit) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var repeater: Runnable? = null

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                action()
                val runnable = object : Runnable {
                    override fun run() {
                        action()
                        handler.postDelayed(this, REPEAT_INTERVAL_MS)
                    }
                }
                repeater = runnable
                handler.postDelayed(runnable, INITIAL_DELAY_MS)
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                repeater?.let(handler::removeCallbacks)
                repeater = null
                if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                true
            }

            else -> false
        }
    }
}
