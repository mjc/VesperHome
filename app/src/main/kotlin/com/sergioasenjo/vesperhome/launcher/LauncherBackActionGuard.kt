package com.sergioasenjo.vesperhome.launcher

import android.os.SystemClock
import android.view.KeyEvent

internal class LauncherBackActionGuard {
    private var readyAt = Long.MAX_VALUE
    private var keyStartedInLauncher = false

    fun onResume() {
        keyStartedInLauncher = false
        readyAt = SystemClock.uptimeMillis() + RESUME_GUARD_MS
    }

    fun onStop() {
        keyStartedInLauncher = false
        readyAt = Long.MAX_VALUE
    }

    fun onKeyEvent(event: KeyEvent, hasWindowFocus: Boolean) {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return
        when (event.action) {
            KeyEvent.ACTION_DOWN ->
                keyStartedInLauncher = event.repeatCount == 0 &&
                    hasWindowFocus &&
                    SystemClock.uptimeMillis() >= readyAt

            KeyEvent.ACTION_UP -> Unit
        }
    }

    fun consumeIntentionalBack(): Boolean = keyStartedInLauncher.also { keyStartedInLauncher = false }

    private companion object {
        const val RESUME_GUARD_MS = 500L
    }
}
