package me.nimd.etbridge.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code paths used at startup and on the main screens so they are AOT-compiled at
 * install time. Run with: ./gradlew :app:generateBaselineProfile
 *
 * Known issue: on Android 17 (API 37) builds ART records no runtime profile for the target, so
 * the rule fails with "Generated Profile is empty". Generate on an API 33–36 device or emulator.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        criticalUserJourney()
    }
}
