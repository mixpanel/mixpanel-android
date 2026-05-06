package com.mixpanel.android.sessionreplay.tracking

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for ScreenRecorder bitmap scaling logic.
 * Tests verify that the scaling calculation correctly converts physical pixels to logical pixels
 * by dividing by device density, and that canvas scaling is applied correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenRecorderTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    /**
     * Helper function to test dimension calculations without creating actual bitmaps.
     * This tests the mathematical logic: scaledWidth = (width / density).toInt()
     */
    private fun calculateScaledDimensions(
        width: Int,
        height: Int,
        density: Float
    ): Pair<Int, Int> {
        val scaledWidth = (width / density).toInt()
        val scaledHeight = (height / density).toInt()
        return Pair(scaledWidth, scaledHeight)
    }

    /**
     * Helper function to calculate the expected canvas scale factor.
     * The canvas is scaled by 1/density to fit the view into the smaller bitmap.
     */
    private fun calculateCanvasScaleFactor(density: Float): Float = 1f / density

    /**
     * Test 1: Verify correct dimension calculation for standard density (1.0)
     * For density 1.0, dimensions should remain the same (no scaling)
     */
    @Test
    fun testDimensionCalculation_standardDensity() {
        // Given a view with 320x480 dimensions and density 1.0
        val width = 320
        val height = 480
        val density = 1.0f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the dimensions should remain the same: 320/1.0 = 320, 480/1.0 = 480
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 2: Verify correct dimension calculation for 1.5x density (typical hdpi)
     * For density 1.5, dimensions should be scaled down to 1x
     */
    @Test
    fun testDimensionCalculation_hdpiDensity() {
        // Given a view with 480x720 dimensions and density 1.5
        val width = 480
        val height = 720
        val density = 1.5f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the bitmap dimensions should be: 480/1.5 = 320, 720/1.5 = 480
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 3: Verify correct dimension calculation for 2x density (typical xhdpi)
     */
    @Test
    fun testDimensionCalculation_xhdpiDensity() {
        // Given a view with 640x960 dimensions and density 2.0
        val width = 640
        val height = 960
        val density = 2.0f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the bitmap dimensions should be: 640/2.0 = 320, 960/2.0 = 480
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 4: Verify correct dimension calculation for 3x density (typical xxhdpi)
     */
    @Test
    fun testDimensionCalculation_xxhdpiDensity() {
        // Given a view with 960x1440 dimensions and density 3.0
        val width = 960
        val height = 1440
        val density = 3.0f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the bitmap dimensions should be: 960/3.0 = 320, 1440/3.0 = 480
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 5: Verify correct dimension calculation for 4x density (very high density xxxhdpi)
     */
    @Test
    fun testDimensionCalculation_xxxhdpiDensity() {
        // Given a view with 1280x1920 dimensions and density 4.0
        val width = 1280
        val height = 1920
        val density = 4.0f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the bitmap dimensions should be: 1280/4.0 = 320, 1920/4.0 = 480
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 6: Verify canvas scaling is properly calculated for density 2.0
     * The canvas should be scaled by 1/density to fit the view into the smaller bitmap
     */
    @Test
    fun testCanvasScaling_appliedCorrectly() {
        // Given density 2.0
        val density = 2.0f

        // When canvas scale factor is calculated
        val scaleFactor = calculateCanvasScaleFactor(density)

        // Then the scale factor should be 1/density = 0.5
        assertEquals(0.5f, scaleFactor, 0.0001f)
    }

    /**
     * Test 7: Verify canvas scaling for high density (3.0)
     */
    @Test
    fun testCanvasScaling_highDensity() {
        // Given density 3.0
        val density = 3.0f

        // When canvas scale factor is calculated
        val scaleFactor = calculateCanvasScaleFactor(density)

        // Then the scale factor should be 1/3.0 ≈ 0.333
        assertEquals(1f / 3f, scaleFactor, 0.0001f)
    }

    /**
     * Test 8: Edge case - Canvas scaling for standard density (1.0)
     * No scaling should be applied for density 1.0
     */
    @Test
    fun testCanvasScaling_standardDensity() {
        // Given density 1.0
        val density = 1.0f

        // When canvas scale factor is calculated
        val scaleFactor = calculateCanvasScaleFactor(density)

        // Then the scale factor should be 1.0 (no scaling)
        assertEquals(1.0f, scaleFactor, 0.0001f)
    }

    /**
     * Test 9: Edge case - Canvas scaling for 1.5x density
     */
    @Test
    fun testCanvasScaling_hdpiDensity() {
        // Given density 1.5
        val density = 1.5f

        // When canvas scale factor is calculated
        val scaleFactor = calculateCanvasScaleFactor(density)

        // Then the scale factor should be 1/1.5 ≈ 0.667
        assertEquals(1f / 1.5f, scaleFactor, 0.0001f)
    }

    /**
     * Test 10: Edge case - Canvas scaling for 4x density
     */
    @Test
    fun testCanvasScaling_xxxhdpiDensity() {
        // Given density 4.0
        val density = 4.0f

        // When canvas scale factor is calculated
        val scaleFactor = calculateCanvasScaleFactor(density)

        // Then the scale factor should be 1/4.0 = 0.25
        assertEquals(0.25f, scaleFactor, 0.0001f)
    }

    /**
     * Test 11: Edge case - Very high density device (5.0)
     * Should handle gracefully and calculate correct dimensions
     */
    @Test
    fun testEdgeCase_veryHighDensity() {
        // Given a view with very high density 5.0
        val width = 1600
        val height = 2400
        val density = 5.0f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the bitmap dimensions should be: 1600/5.0 = 320, 2400/5.0 = 480
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 12: Edge case - Fractional dimensions after scaling
     * When scaled dimensions result in fractional values, they should be converted to integers
     */
    @Test
    fun testEdgeCase_fractionalDimensionsAfterScaling() {
        // Given a view with dimensions that result in fractional scaled dimensions
        val width = 481 // 481/1.5 = 320.666...
        val height = 721 // 721/1.5 = 480.666...
        val density = 1.5f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the bitmap dimensions should be truncated to integers
        assertEquals(320, scaledWidth)
        assertEquals(480, scaledHeight)
    }

    /**
     * Test 13: Edge case - Very small dimensions at high density
     * Demonstrates that integer truncation can result in zero-dimension bitmaps
     */
    @Test
    fun testEdgeCase_minimumDimensions() {
        // Given a view with very small dimensions at high density
        val width = 3 // 3/4.0 = 0.75
        val height = 3 // 3/4.0 = 0.75
        val density = 4.0f

        // When dimensions are scaled
        val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

        // Then the scaled dimensions would be truncated to 0
        // Note: This would cause createBitmap to fail - the view dimension check catches this
        assertEquals(0, scaledWidth)
        assertEquals(0, scaledHeight)
    }

    /**
     * Test 14: Verify that scaled dimensions are calculated correctly for various density values
     */
    @Test
    fun testScaledDimensionsAreCalculatedCorrectly() {
        // Test various combinations to ensure scaled dimensions are calculated correctly
        val testCases = listOf(
            Triple(320, 480, 1.0f) to Pair(320, 480),
            Triple(640, 960, 2.0f) to Pair(320, 480),
            Triple(1080, 1920, 3.0f) to Pair(360, 640),
            Triple(1440, 2560, 4.0f) to Pair(360, 640),
            Triple(100, 200, 1.5f) to Pair(66, 133)
        )

        testCases.forEach { (input, expected) ->
            val (width, height, density) = input
            val (expectedWidth, expectedHeight) = expected

            // When dimensions are scaled
            val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

            // Then verify the dimensions are as expected
            assertEquals("Width mismatch for input: $input", expectedWidth, scaledWidth)
            assertEquals("Height mismatch for input: $input", expectedHeight, scaledHeight)
        }
    }

    /**
     * Test 15: Verify canvas scale factors for various densities
     */
    @Test
    fun testCanvasScaleFactorsForVariousDensities() {
        // Test canvas scale factors for common density values
        val testCases = mapOf(
            1.0f to 1.0f,
            1.5f to (1f / 1.5f),
            2.0f to 0.5f,
            3.0f to (1f / 3f),
            4.0f to 0.25f,
            5.0f to 0.2f
        )

        testCases.forEach { (density, expectedScale) ->
            // When canvas scale factor is calculated
            val scaleFactor = calculateCanvasScaleFactor(density)

            // Then verify the scale factor is correct
            assertEquals(
                "Scale factor mismatch for density: $density",
                expectedScale,
                scaleFactor,
                0.0001f
            )
        }
    }

    // --- Sub-window position tests (production getSubWindowInfo) ---

    private fun createMockView(
        width: Int,
        height: Int,
        density: Float,
        screenX: Int,
        screenY: Int
    ): View {
        val displayMetrics = DisplayMetrics().apply { this.density = density }
        val resources = mockk<Resources> { every { this@mockk.displayMetrics } returns displayMetrics }
        val viewContext = mockk<Context> { every { this@mockk.resources } returns resources }
        return mockk<View> {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
            every { this@mockk.context } returns viewContext
            every { getLocationOnScreen(any()) } answers {
                val arr = firstArg<IntArray>()
                arr[0] = screenX
                arr[1] = screenY
            }
        }
    }

    @Test
    fun testGetSubWindowInfo_noOffset_density3x() {
        val fullScreen = createMockView(1080, 1920, 3f, screenX = 0, screenY = 0)
        val dialog = createMockView(900, 600, 3f, screenX = 0, screenY = 600)

        val info = ScreenRecorder.shared.getSubWindowInfo(dialog, fullScreen)

        assertNotNull(info)
        assertEquals(0f, info!!.screenX, 0.01f)
        assertEquals(200f, info.screenY, 0.01f)
    }

    @Test
    fun testGetSubWindowInfo_withTopOffset_cameraCutout() {
        val fullScreen = createMockView(1080, 1920, 3f, screenX = 0, screenY = 145)
        val dialog = createMockView(900, 600, 3f, screenX = 0, screenY = 500)

        val info = ScreenRecorder.shared.getSubWindowInfo(dialog, fullScreen)

        assertNotNull(info)
        assertEquals(0f, info!!.screenX, 0.01f)
        assertEquals((500 - 145) / 3f, info.screenY, 0.01f)
    }

    @Test
    fun testGetSubWindowInfo_withLeftOffset_landscapeCutout() {
        val fullScreen = createMockView(1920, 1080, 2f, screenX = 100, screenY = 0)
        val dialog = createMockView(800, 600, 2f, screenX = 300, screenY = 200)

        val info = ScreenRecorder.shared.getSubWindowInfo(dialog, fullScreen)

        assertNotNull(info)
        assertEquals(100f, info!!.screenX, 0.01f)
        assertEquals(100f, info.screenY, 0.01f)
    }

    @Test
    fun testGetSubWindowInfo_withBothOffsets() {
        val fullScreen = createMockView(1080, 1920, 2f, screenX = 50, screenY = 100)
        val dialog = createMockView(800, 600, 2f, screenX = 250, screenY = 600)

        val info = ScreenRecorder.shared.getSubWindowInfo(dialog, fullScreen)

        assertNotNull(info)
        assertEquals(100f, info!!.screenX, 0.01f)
        assertEquals(250f, info.screenY, 0.01f)
    }

    @Test
    fun testGetSubWindowInfo_returnsNull_whenSameView() {
        val view = createMockView(1080, 1920, 3f, screenX = 0, screenY = 0)
        val info = ScreenRecorder.shared.getSubWindowInfo(view, view)
        assertNull(info)
    }

    @Test
    fun testGetSubWindowInfo_returnsNull_whenFullScreenViewNull() {
        val view = createMockView(1080, 1920, 3f, screenX = 0, screenY = 0)
        val info = ScreenRecorder.shared.getSubWindowInfo(view, null)
        assertNull(info)
    }

    /**
     * Test 16: Verify that scaling logic produces positive dimensions for typical device configurations
     */
    @Test
    fun testTypicalDeviceConfigurations() {
        // Test typical device configurations to ensure positive dimensions
        val configurations = listOf(
            // (width, height, density) -> expected (scaledWidth, scaledHeight)
            Triple(1080, 1920, 3.0f) to Pair(360, 640), // Common phone
            Triple(1440, 2560, 4.0f) to Pair(360, 640), // High-end phone
            Triple(800, 1280, 1.5f) to Pair(533, 853), // Tablet
            Triple(1200, 1920, 2.0f) to Pair(600, 960), // Large tablet
            Triple(720, 1280, 2.0f) to Pair(360, 640) // Mid-range phone
        )

        configurations.forEach { (input, expected) ->
            val (width, height, density) = input
            val (expectedWidth, expectedHeight) = expected

            // When dimensions are scaled
            val (scaledWidth, scaledHeight) = calculateScaledDimensions(width, height, density)

            // Then verify dimensions are positive and match expected values
            assertTrue("Width should be positive for $input", scaledWidth > 0)
            assertTrue("Height should be positive for $input", scaledHeight > 0)
            assertEquals("Width mismatch for $input", expectedWidth, scaledWidth)
            assertEquals("Height mismatch for $input", expectedHeight, scaledHeight)
        }
    }
}
