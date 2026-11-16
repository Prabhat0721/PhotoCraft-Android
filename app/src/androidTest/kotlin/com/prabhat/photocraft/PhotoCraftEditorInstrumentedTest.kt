package com.prabhat.photocraft

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
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
import com.prabhat.photocraft.ui.CropPreviewView
import com.prabhat.photocraft.ui.ResettableSeekBar
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
            deliverPickedPhoto(activity, photoUri)
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

    @Test
    fun pinchGestureZoomsThePhotoPreview() {
        loadTestPhoto()
        var zoomAfterPinch = 1f
        activityRule.scenario.onActivity { activity ->
            val preview = activity.findViewById<CropPreviewView>(R.id.previewImage)
            dispatchPinch(preview)
            zoomAfterPinch = preview.currentZoomScale
        }
        assertTrue("Pinch should zoom the preview", zoomAfterPinch > 1.2f)
    }

    @Test
    fun brushStrokeCreatesAVisibleMaskSelection() {
        loadTestPhoto()
        onView(withId(R.id.maskingCategory)).perform(click())
        onView(withId(R.id.maskAddButton)).perform(scrollTo(), click())

        var selectionReady = false
        activityRule.scenario.onActivity { activity ->
            val preview = activity.findViewById<CropPreviewView>(R.id.previewImage)
            val x = preview.width / 2f
            val y = preview.height / 2f
            dispatchTapAt(preview, x, y)
            selectionReady = activity.findViewById<android.widget.TextView>(
                R.id.maskStatusText
            ).text.contains(activity.getString(R.string.mask_selection_ready))
        }
        assertTrue("A brush stroke should create a mask selection", selectionReady)
    }

    @Test
    fun doubleTapResetsSliderToItsConfiguredDefault() {
        var progressAfterDoubleTap = -1
        activityRule.scenario.onActivity { activity ->
            val slider = ResettableSeekBar(activity).apply {
                max = 100
                progress = 75
                setDefaultProgress(30)
                layout(0, 0, 240, 64)
            }
            val start = SystemClock.uptimeMillis()
            dispatchTap(slider, start)
            dispatchTap(slider, start + 120L)
            progressAfterDoubleTap = slider.progress
        }
        assertEquals(30, progressAfterDoubleTap)
    }

    private fun loadTestPhoto() {
        val photoUri = Uri.parse("content://com.prabhat.photocraft.test.photo/sample")
        activityRule.scenario.onActivity { activity ->
            deliverPickedPhoto(activity, photoUri)
        }
        waitForEnabled(activityRule.scenario, R.id.colorCategory)
    }

    private fun deliverPickedPhoto(activity: MainActivity, uri: Uri) {
        val callback = MainActivity::class.java.getDeclaredMethod(
            "onActivityResult",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Intent::class.java
        )
        callback.isAccessible = true
        callback.invoke(activity, 1001, Activity.RESULT_OK, Intent().setData(uri))
    }

    private fun dispatchTap(view: View, downTime: Long) {
        val upTime = downTime + 50L
        dispatchTapAt(view, 100f, 32f, downTime, upTime)
    }

    private fun dispatchTapAt(
        view: View,
        x: Float,
        y: Float,
        downTime: Long = SystemClock.uptimeMillis(),
        upTime: Long = downTime + 50L
    ) {
        dispatch(view, singlePointerEvent(downTime, downTime, MotionEvent.ACTION_DOWN, x, y))
        dispatch(view, singlePointerEvent(downTime, upTime, MotionEvent.ACTION_UP, x, y))
    }

    private fun dispatchPinch(view: View) {
        val centerX = view.width / 2f
        val centerY = view.height / 2f
        val downTime = SystemClock.uptimeMillis()
        val firstX = centerX - 24f
        val secondX = centerX + 24f
        dispatch(view, singlePointerEvent(
            downTime, downTime, MotionEvent.ACTION_DOWN, firstX, centerY
        ))
        dispatch(view, twoPointerEvent(
            downTime,
            downTime + 20L,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            PointF(firstX, centerY),
            PointF(secondX, centerY)
        ))
        dispatch(view, twoPointerEvent(
            downTime,
            downTime + 50L,
            MotionEvent.ACTION_MOVE,
            PointF(centerX - 70f, centerY),
            PointF(centerX + 70f, centerY)
        ))
        dispatch(view, twoPointerEvent(
            downTime,
            downTime + 70L,
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            PointF(centerX - 70f, centerY),
            PointF(centerX + 70f, centerY)
        ))
        dispatch(view, singlePointerEvent(
            downTime, downTime + 90L, MotionEvent.ACTION_UP, centerX - 70f, centerY
        ))
    }

    private fun dispatch(view: View, event: MotionEvent) {
        try {
            view.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun singlePointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ): MotionEvent = MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        1,
        arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }),
        arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }),
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0
    )

    private fun twoPointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        first: PointF,
        second: PointF
    ): MotionEvent = MotionEvent.obtain(
        downTime,
        eventTime,
        action,
        2,
        arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        ),
        arrayOf(first, second).map { point ->
            MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = 1f
                size = 1f
            }
        }.toTypedArray(),
        0,
        0,
        1f,
        1f,
        0,
        0,
        InputDevice.SOURCE_TOUCHSCREEN,
        0
    )

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
