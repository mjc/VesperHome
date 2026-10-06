package com.sergioasenjo.vesperhome.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupNavigationAndSettings() {
        // Keep profile capture separate from ordinary startup benchmark runs.
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("androidx.benchmark.enabledRules") == "BaselineProfile"
        )
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "applicationsRow")), 5_000))
            repeat(6) {
                device.pressDPadRight()
                device.waitForIdle()
            }
            repeat(4) {
                device.pressDPadLeft()
                device.waitForIdle()
            }
            // A populated profile fixture should include both row and grid categories.
            repeat(8) {
                device.pressDPadDown()
                device.waitForIdle()
            }
            repeat(4) {
                device.pressDPadRight()
                device.waitForIdle()
            }
            device.findObject(By.res(TARGET_PACKAGE, "openLauncherSettings")).click()
            check(device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "sortApplications")), 5_000))
            device.pressBack()
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.sergioasenjo.vesperhome.profile"
    }
}
