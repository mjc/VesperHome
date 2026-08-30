package com.sergioasenjo.vesperhome.launcher

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

internal object LauncherEmojiInitializer {
    fun reportFullyDrawnAndInitialize(activity: AppCompatActivity, root: View) {
        activity.reportFullyDrawn()
        root.postDelayed({ initialize(activity) }, EMOJI_INITIALIZATION_DELAY_MS)
    }

    private fun initialize(activity: AppCompatActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val config = DefaultEmojiCompatConfig.create(activity.applicationContext) ?: return
        config.setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_MANUAL)
        EmojiCompat.init(config)
        EmojiCompat.get().load()
    }

    private const val EMOJI_INITIALIZATION_DELAY_MS = 3_000L
}
