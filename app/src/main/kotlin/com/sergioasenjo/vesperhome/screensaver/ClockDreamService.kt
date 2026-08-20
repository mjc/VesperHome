package com.sergioasenjo.vesperhome.screensaver

import android.service.dreams.DreamService
import android.view.LayoutInflater
import com.sergioasenjo.vesperhome.VesperHomeApplication
import com.sergioasenjo.vesperhome.databinding.ViewClockBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClockDreamService : DreamService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clockController: ClockViewController? = null
    private var standbyJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val binding = ViewClockBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        clockController = ClockViewController(binding)
        val settingsRepository = (application as VesperHomeApplication).container.launcherSettingsRepository
        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                clockController?.render(settings.screensaver, settings.statusBar)
                scheduleStandby(settings.screensaver.standbyDelay)
            }
        }
    }

    override fun onDetachedFromWindow() {
        clockController?.release()
        clockController = null
        standbyJob?.cancel()
        serviceScope.cancel()
        super.onDetachedFromWindow()
    }

    private fun scheduleStandby(delaySetting: ScreensaverStandbyDelay) {
        standbyJob?.cancel()
        isScreenBright = true
        val delayMillis = delaySetting.milliseconds ?: return
        standbyJob = serviceScope.launch {
            delay(delayMillis)
            isScreenBright = false
        }
    }
}
