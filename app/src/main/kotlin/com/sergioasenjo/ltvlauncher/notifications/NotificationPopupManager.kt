package com.sergioasenjo.ltvlauncher.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class NotificationPopupManager(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var popup: View? = null
    private val removePopupRunnable = Runnable(::removePopup)

    fun show(notification: StatusBarNotification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !Settings.canDrawOverlays(context)) return
        handler.post {
            removePopup()
            val view = createPopup(notification)
            try {
                windowManager.addView(view, layoutParams())
                popup = view
                handler.postDelayed(removePopupRunnable, POPUP_DURATION_MS)
            } catch (_: WindowManager.BadTokenException) {
                popup = null
            } catch (_: SecurityException) {
                popup = null
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        removePopup()
    }

    private fun createPopup(statusBarNotification: StatusBarNotification): View {
        val notification = statusBarNotification.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val appLabel = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(statusBarNotification.packageName, 0)
            ).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            statusBarNotification.packageName
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xEE1E252D.toInt())
                setStroke(dp(1), 0x55FFFFFF)
            }
            addView(
                TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    this.text = listOf(appLabel, title).filter(String::isNotBlank).joinToString(" | ")
                }
            )
            if (text.isNotBlank()) {
                addView(
                    TextView(context).apply {
                        setTextColor(0xFFCCD5DF.toInt())
                        textSize = 14f
                        maxLines = 2
                        this.text = text
                    }
                )
            }
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(24)
        y = dp(24)
    }

    private fun removePopup() {
        handler.removeCallbacks(removePopupRunnable)
        popup?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The window was already removed by the system.
            }
        }
        popup = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val POPUP_DURATION_MS = 4_000L
    }
}
