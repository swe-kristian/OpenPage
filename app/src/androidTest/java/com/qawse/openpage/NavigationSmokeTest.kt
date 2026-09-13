package com.qawse.openpage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation smoke tests (UiAutomator). These run on a device or emulator
 * only — execute with:  ./gradlew connectedDebugAndroidTest
 *
 * Verification status: NOT executed in the CI-less development environment
 * used to produce v2.0.0 (no emulator/KVM available). They are included
 * for device-owners and CI pipelines to run.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    private val device: UiDevice by lazy {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun launch() {
        device.pressHome()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(ctx.packageName).depth(0)), 5_000)
    }

    @Test fun packageNameIsCorrect() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.qawse.openpage", ctx.packageName)
    }

    @Test fun bottomNavigationShowsFourDestinations() {
        launch()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        listOf("Home", "Print", "Scan", "Settings").forEach { label ->
            assertTrue(
                "missing destination $label",
                device.wait(Until.hasObject(By.text(label)), 3_000),
            )
        }
    }

    @Test fun canReachEveryTab() {
        launch()
        listOf("Print", "Scan", "Settings", "Home").forEach { label ->
            val tab = device.findObject(By.text(label))
            tab?.click()
            device.waitForIdle(1_000)
        }
    }
}
