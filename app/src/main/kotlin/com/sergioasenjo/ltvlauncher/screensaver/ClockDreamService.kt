package com.sergioasenjo.ltvlauncher.screensaver

import android.service.dreams.DreamService
import android.view.LayoutInflater
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.databinding.ViewClockBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClockDreamService : DreamService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clockController: ClockViewController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val binding = ViewClockBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        clockController = ClockViewController(binding)
        val settingsRepository = (application as LtvLauncherApplication).container.launcherSettingsRepository
        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                clockController?.render(settings.screensaver, settings.statusBar)
            }
        }
    }

    override fun onDetachedFromWindow() {
        clockController?.release()
        clockController = null
        serviceScope.cancel()
        super.onDetachedFromWindow()
    }
}
