package com.sergioasenjo.ltvlauncher.screensaver

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sergioasenjo.ltvlauncher.LtvLauncherApplication
import com.sergioasenjo.ltvlauncher.databinding.ViewClockBinding
import kotlinx.coroutines.launch

class ClockActivity : AppCompatActivity() {
    private lateinit var binding: ViewClockBinding
    private lateinit var clockController: ClockViewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ViewClockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        clockController = ClockViewController(binding)
        val settingsRepository = (application as LtvLauncherApplication).container.launcherSettingsRepository
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepository.settings.collect { settings ->
                    clockController.render(settings.screensaver, settings.statusBar)
                }
            }
        }
    }

    override fun onDestroy() {
        clockController.release()
        super.onDestroy()
    }
}
