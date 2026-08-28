package com.mixpanel.android.sessionreplay.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the meta event's viewport, which the replay player scales every frame to fit.
 *
 * On a non-edge-to-edge activity the system display metrics exclude the navigation bar while the
 * screenshot is captured from the full window, so a viewport taken from the metrics is shorter than
 * the frame and the player crops the bottom of the app UI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CapturedViewportTest {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val REPLAY = "test"
        const val NEXT_REPLAY = "next-replay"
    }

    private fun emptyPayload(replayId: String = REPLAY) = PayloadInfo(emptyList(), 1.0, 1, replayId, 1, 1.0)

    private fun metaDimensions(payload: String): Pair<Int, Int> {
        val meta = json.parseToJsonElement(payload).jsonArray.first().jsonObject
        assertEquals(EventType.META, meta["type"]!!.jsonPrimitive.int)
        val data = meta["data"]!!.jsonObject
        return data["width"]!!.jsonPrimitive.int to data["height"]!!.jsonPrimitive.int
    }

    @Before
    fun setUp() {
        CapturedViewport.reset()
        CapturedViewport.setCurrentReplay(REPLAY)
    }

    @After
    fun tearDown() {
        CapturedViewport.reset()
    }

    @Test
    fun `meta viewport matches a frame taller than the system metrics report`() {
        val systemHeight = DeviceInfo.screenHeight
        // A fixed bottom tab bar's worth of UI, the slice the player was cropping.
        val capturedHeight = systemHeight + 49
        CapturedViewport.record(DeviceInfo.screenWidth, capturedHeight)

        val (_, height) = metaDimensions(SessionReplayEncoder.jsonPayload(emptyPayload())!!)

        assertEquals(capturedHeight, height)
        assertNotEquals(systemHeight, height)
    }

    @Test
    fun `meta viewport matches the captured frame width`() {
        CapturedViewport.record(411, 915)

        val (width, height) = metaDimensions(SessionReplayEncoder.jsonPayload(emptyPayload())!!)

        assertEquals(411, width)
        assertEquals(915, height)
    }

    @Test
    fun `meta viewport falls back to system metrics before the first capture`() {
        val (width, height) = metaDimensions(SessionReplayEncoder.jsonPayload(emptyPayload())!!)

        assertEquals(DeviceInfo.screenWidth, width)
        assertEquals(DeviceInfo.screenHeight, height)
    }

    @Test
    fun `a failed capture does not describe the viewport`() {
        CapturedViewport.record(411, 915)

        CapturedViewport.record(0, 0)
        CapturedViewport.record(-1, 915)

        assertEquals(CapturedViewport.Size(411, 915), CapturedViewport.current(REPLAY))
    }

    @Test
    fun `the latest capture wins so rotation is reflected`() {
        CapturedViewport.record(411, 915)
        CapturedViewport.record(915, 411)

        assertEquals(CapturedViewport.Size(915, 411), CapturedViewport.current(REPLAY))
    }

    @Test
    fun `nothing is reported before a capture`() {
        assertNull(CapturedViewport.current(REPLAY))
    }

    @Test
    fun `a replay that has not captured yet does not inherit the previous replay's viewport`() {
        CapturedViewport.record(411, 915)

        CapturedViewport.setCurrentReplay(NEXT_REPLAY)

        assertNull(CapturedViewport.current(NEXT_REPLAY))
        val (width, height) = metaDimensions(SessionReplayEncoder.jsonPayload(emptyPayload(NEXT_REPLAY))!!)
        assertEquals(DeviceInfo.screenWidth, width)
        assertEquals(DeviceInfo.screenHeight, height)
    }

    @Test
    fun `a replay still in flight keeps its own viewport after the next replay starts`() {
        CapturedViewport.record(411, 915)

        CapturedViewport.setCurrentReplay(NEXT_REPLAY)

        // stopRecording() flushes the finished replay asynchronously, so its final batch can be
        // built after the next replay has started; it must still describe its own frames.
        val (width, height) = metaDimensions(SessionReplayEncoder.jsonPayload(emptyPayload())!!)
        assertEquals(411, width)
        assertEquals(915, height)
    }

    @Test
    fun `captures before a replay starts are ignored`() {
        CapturedViewport.reset()

        CapturedViewport.record(411, 915)

        assertNull(CapturedViewport.current(REPLAY))
    }
}
