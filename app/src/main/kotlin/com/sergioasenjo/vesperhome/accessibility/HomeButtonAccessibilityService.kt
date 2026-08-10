package com.sergioasenjo.vesperhome.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.sergioasenjo.vesperhome.launcher.LauncherActivity

class HomeButtonAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return super.onKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            startActivity(
                Intent(this, LauncherActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            )
        }
        return true
    }
}
