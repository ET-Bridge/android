package me.nimd.etbridge.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Package of the benchmark/profile build (see the variant applicationId in app/build.gradle.kts). */
const val TARGET_PACKAGE = "me.nimd.etbridge.bench"

private const val TIMEOUT = 8_000L

private fun UiDevice.waitText(text: String) = wait(Until.hasObject(By.text(text)), TIMEOUT)

/** Cold-launches the target app from the shell and waits for its first frame. */
fun UiDevice.launchTarget() {
    executeShellCommand("am start -W -n $TARGET_PACKAGE/me.nimd.etbridge.MainActivity")
    wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), TIMEOUT)
}

/** Signs into demo mode on a fresh install; no-op if already signed in. */
fun UiDevice.ensureDemoSession() {
    wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), TIMEOUT)
    wait(Until.findObject(By.text("Explore the demo")), 3_000)?.click()
    waitText("Overall attendance")
}

private fun UiDevice.tab(label: String, screenMarker: String) {
    findObject(By.text(label))?.click()
    waitText(screenMarker)
    waitForIdle()
}

private fun UiDevice.flingMainList() {
    val list = findObject(By.scrollable(true)) ?: return
    list.setGestureMargin(displayWidth / 5)
    list.fling(Direction.DOWN)
    waitForIdle()
    list.fling(Direction.UP)
    waitForIdle()
}

fun UiDevice.dashboardJourney() {
    waitText("Overall attendance")
    flingMainList()
}

fun UiDevice.attendanceJourney() {
    tab("Attendance", "Semester overall")
    flingMainList()
    // Open a subject (shared-element transition) and come back.
    findObject(By.textContains("hrs"))?.click()
    waitText("What if…")
    waitForIdle()
    pressBack()
    waitText("Semester overall")
}

fun UiDevice.timetableJourney() {
    tab("Timetable", "Timetable")
    val w = displayWidth
    val h = displayHeight
    repeat(2) { swipe(w * 4 / 5, h / 2, w / 5, h / 2, 20); waitForIdle() }
    repeat(2) { swipe(w / 5, h / 2, w * 4 / 5, h / 2, 20); waitForIdle() }
}

fun UiDevice.academicsJourney() {
    tab("Academics", "Exams")
    listOf("Assignments", "Materials", "Results", "Exams").forEach { t ->
        findObject(By.text(t))?.click()
        waitForIdle()
    }
}

fun UiDevice.moreJourney() {
    tab("More", "Courses")
    findObject(By.text("Academic analysis"))?.click()
    waitText("Cumulative GPA")
    pressBack()
    tab("Home", "Overall attendance")
}

/** The full user journey used for profile generation and the scroll benchmark. */
fun UiDevice.criticalUserJourney() {
    ensureDemoSession()
    dashboardJourney()
    attendanceJourney()
    timetableJourney()
    academicsJourney()
    moreJourney()
}

// MacrobenchmarkScope conveniences
fun MacrobenchmarkScope.ensureDemoSession() = device.ensureDemoSession()
fun MacrobenchmarkScope.dashboardJourney() = device.dashboardJourney()
fun MacrobenchmarkScope.attendanceJourney() = device.attendanceJourney()
fun MacrobenchmarkScope.timetableJourney() = device.timetableJourney()
fun MacrobenchmarkScope.criticalUserJourney() = device.criticalUserJourney()
