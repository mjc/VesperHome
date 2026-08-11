package com.sergioasenjo.vesperhome.screensaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DreamStateTracker(context: Context) {
    private val mutableDreaming = MutableStateFlow(false)
    val dreaming = mutableDreaming.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_DREAMING_STARTED -> mutableDreaming.value = true
                Intent.ACTION_DREAMING_STOPPED -> mutableDreaming.value = false
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_DREAMING_STARTED)
                addAction(Intent.ACTION_DREAMING_STOPPED)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
