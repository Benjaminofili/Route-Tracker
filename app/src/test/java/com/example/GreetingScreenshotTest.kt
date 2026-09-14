package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.LiveRunState
import com.example.ui.components.LiveTrackerPane
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun liveTracker_screenshot() {
    val sampleState = LiveRunState(
        isTracking = true,
        distanceMeters = 3420.0,
        elapsedTimeSeconds = 1140L,
        currentPaceSecondsPerKm = 333,
        currentAccuracy = 4.0f
    )
    composeTestRule.setContent {
        MyApplicationTheme {
            LiveTrackerPane(
                liveRunState = sampleState,
                onPause = {},
                onResume = {},
                onStop = {},
                onSimulateStep = {}
            )
        }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/live_tracker.png")
  }
}

