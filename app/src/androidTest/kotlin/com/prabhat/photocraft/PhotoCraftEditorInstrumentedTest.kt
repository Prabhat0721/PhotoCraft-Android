package com.prabhat.photocraft

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoCraftEditorInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun homeScreenShowsPhotoPickerButtonAndAccessibleEmptyState() {
        onView(withId(R.id.emptyState)).check(matches(isDisplayed()))
        onView(withId(R.id.openPhotoButton))
            .check(matches(isDisplayed()))
            .check(matches(withText(R.string.open_photo)))
        onView(withId(R.id.editorPanel)).check(matches(org.hamcrest.Matchers.not(isDisplayed())))
    }

    @Test
    fun loadingPhotoOpensEditingPanelsAndAddsMaskLayer() {
        val photoUri = Uri.parse("content://com.prabhat.photocraft.test.photo/sample")
        activityRule.scenario.onActivity { activity ->
            activity.onActivityResult(
                1001,
                Activity.RESULT_OK,
                Intent().setData(photoUri)
            )
        }
        waitForEnabled(activityRule.scenario, R.id.colorCategory)

        onView(withId(R.id.colorCategory)).perform(click())
        onView(withId(R.id.colorPanel)).check(matches(isDisplayed()))

        onView(withId(R.id.effectsCategory)).perform(click())
        onView(withId(R.id.filterPanel)).check(matches(isDisplayed()))

        onView(withId(R.id.maskingCategory)).perform(click())
        onView(withId(R.id.maskingPanel)).check(matches(isDisplayed()))
        var layersBefore = 0
        activityRule.scenario.onActivity { activity ->
            layersBefore = activity.findViewById<android.widget.LinearLayout>(
                R.id.maskLayerList
            ).childCount
        }
        onView(withId(R.id.maskAddButton)).perform(scrollTo(), click())

        var layersAfter = 0
        activityRule.scenario.onActivity { activity ->
            layersAfter = activity.findViewById<android.widget.LinearLayout>(
                R.id.maskLayerList
            ).childCount
        }
        assertEquals(layersBefore + 1, layersAfter)
        onView(withId(R.id.maskStatusText)).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withId(R.id.undoButton)).check(matches(isEnabled())).perform(click())
        var layersAfterUndo = -1
        activityRule.scenario.onActivity { activity ->
            layersAfterUndo = activity.findViewById<android.widget.LinearLayout>(
                R.id.maskLayerList
            ).childCount
        }
        assertEquals(layersBefore, layersAfterUndo)

        onView(withId(R.id.redoButton)).check(matches(isEnabled())).perform(click())
        var layersAfterRedo = -1
        activityRule.scenario.onActivity { activity ->
            layersAfterRedo = activity.findViewById<android.widget.LinearLayout>(
                R.id.maskLayerList
            ).childCount
        }
        assertEquals(layersBefore + 1, layersAfterRedo)
    }

    private fun waitForEnabled(scenario: ActivityScenario<MainActivity>, viewId: Int) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        var enabled = false
        while (!enabled && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                enabled = activity.findViewById<android.view.View>(viewId).isEnabled
            }
            if (!enabled) Thread.sleep(25L)
        }
        assertTrue("Timed out waiting for the photo to finish loading", enabled)
    }
}
