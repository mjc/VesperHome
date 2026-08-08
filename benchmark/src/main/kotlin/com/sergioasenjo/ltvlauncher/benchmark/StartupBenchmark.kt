package com.sergioasenjo.ltvlauncher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() {
        measureStartup(StartupMode.COLD, CompilationMode.None())
    }

    @Test
    fun coldStartupFullCompilation() {
        measureStartup(StartupMode.COLD, CompilationMode.Full())
    }

    @Test
    fun warmStartup() {
        measureStartup(StartupMode.WARM, CompilationMode.Full())
    }

    private fun measureStartup(startupMode: StartupMode, compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = ITERATIONS,
            setupBlock = {
                device.executeShellCommand("am start -a android.settings.SETTINGS")
                device.waitForIdle()
            },
            measureBlock = { startActivityAndWait() }
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.sergioasenjo.ltvlauncher"
        const val ITERATIONS = 5
    }
}
