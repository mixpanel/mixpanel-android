package com.mixpanel.android.mpmetrics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for dead click detection in mixed-framework UI scenarios.
 *
 * Tests verify that clicks producing cross-framework UI changes do NOT trigger
 * false dead clicks ($mp_dead_click). Each test clicks a button that updates
 * a counter text in either the same or different framework.
 *
 * Test matrix (4 combinations × 2 screen types = 8 tests):
 *
 * In XML-based screen (AutocaptureXmlTestActivity with ComposeView):
 * 1. XML button → XML text change
 * 2. XML button → Compose text change
 * 3. Compose button → XML text change
 * 4. Compose button → Compose text change
 *
 * In Compose-based screen (AutocaptureComposeTestActivity with AndroidView):
 * 5. XML button → XML text change
 * 6. XML button → Compose text change
 * 7. Compose button → XML text change
 * 8. Compose button → Compose text change
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AutocaptureMixedFrameworkInstrumentedTest {

    companion object {
        private const val TEST_TOKEN = "MIXED_FRAMEWORK_TEST_TOKEN"
    }

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val mEvents: BlockingQueue<JSONObject> = LinkedBlockingQueue()
    private lateinit var mMixpanel: MixpanelAPI
    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().context

        val messages = AnalyticsMessages.getInstance(
            mContext,
            MPConfig.getInstance(mContext, null)
        )
        messages.hardKill()
        var attempts = 0
        while (!messages.isDead && attempts++ < 40) Thread.sleep(50)

        mEvents.clear()

        val mockPreferences = TestUtils.EmptyPreferences(mContext)
        val config = MPConfig.getInstance(mContext, null)

        val mockAdapter = object : MPDbAdapter(mContext, config) {
            override fun addJSON(message: JSONObject, token: String, table: Table): Int {
                if (table == Table.EVENTS) {
                    try {
                        val eventName = message.optString("event", "")
                        if (eventName.startsWith("\$mp_")) {
                            mEvents.add(message)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MixedFrameworkTest", "Error capturing event", e)
                    }
                }
                return super.addJSON(message, token, table)
            }
        }

        val customMessages = object : AnalyticsMessages(mContext, config) {
            override fun makeDbAdapter(context: Context): MPDbAdapter = mockAdapter
        }

        val autocaptureOptions = AutocaptureOptions.Builder().build()
        val options = MixpanelOptions.Builder()
            .autocaptureOptions(autocaptureOptions)
            .build()

        mMixpanel = object : MixpanelAPI(
            mContext, mockPreferences, TEST_TOKEN, config, options, true
        ) {
            override fun getAnalyticsMessages(): AnalyticsMessages = customMessages

            override fun getPersistentIdentity(
                context: Context,
                referrerPreferences: Future<SharedPreferences>,
                token: String,
                instanceName: String?,
                deviceIdProvider: DeviceIdProvider?
            ): PersistentIdentity {
                val instanceKey = instanceName ?: token
                val prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_$instanceKey"
                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().clear().commit()

                val timePrefsName =
                    "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_$instanceKey"
                context.getSharedPreferences(timePrefsName, Context.MODE_PRIVATE)
                    .edit().clear().commit()

                val mpPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel"
                context.getSharedPreferences(mpPrefsName, Context.MODE_PRIVATE)
                    .edit().clear().putBoolean(token, true)
                    .putBoolean("has_launched", true).apply()

                return super.getPersistentIdentity(
                    context, referrerPreferences, token, instanceName, deviceIdProvider
                )
            }
        }
    }

    @After
    fun tearDown() {
        if (::mMixpanel.isInitialized) {
            mMixpanel.clearSuperProperties()
            mMixpanel.flush()
        }
        mEvents.clear()
        mContext.deleteDatabase("mixpanel")
        TestUtils.cleanUpMixpanelData(mContext)
    }

    // ==================== XML Screen (with ComposeView) ====================

    @Test
    fun testXmlScreen_xmlButtonChangesXmlText_noDeadClick() {
        ActivityScenario.launch(AutocaptureXmlTestActivity::class.java).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(AutocaptureXmlTestActivity.ID_XML_BTN_XML_TEXT)).perform(scrollTo(), click())

            assertClickWithoutDeadClick("xml_btn_xml_text")
        }
    }

    @Test
    fun testXmlScreen_xmlButtonChangesComposeText_noDeadClick() {
        ActivityScenario.launch(AutocaptureXmlTestActivity::class.java).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(AutocaptureXmlTestActivity.ID_XML_BTN_COMPOSE_TEXT)).perform(scrollTo(), click())

            assertClickWithoutDeadClick("xml_btn_compose_text")
        }
    }

    @Test
    fun testXmlScreen_composeButtonChangesXmlText_noDeadClick() {
        ActivityScenario.launch(AutocaptureXmlTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapComposeNode(
                composeTestRule.onNodeWithContentDescription("compose_btn_xml_text"),
                scenario
            )

            assertClickWithoutDeadClick("compose_btn_xml_text")
        }
    }

    @Test
    fun testXmlScreen_composeButtonChangesComposeText_noDeadClick() {
        ActivityScenario.launch(AutocaptureXmlTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapComposeNode(
                composeTestRule.onNodeWithContentDescription("compose_btn_compose_text"),
                scenario
            )

            assertClickWithoutDeadClick("compose_btn_compose_text")
        }
    }

    // ==================== Compose Screen (with AndroidView) ====================

    @Test
    fun testComposeScreen_xmlButtonChangesXmlText_noDeadClick() {
        ActivityScenario.launch(AutocaptureComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapViewByContentDescription(
                "xml_btn_xml_text_in_compose",
                scenario
            )

            assertClickWithoutDeadClick("xml_btn_xml_text_in_compose")
        }
    }

    @Test
    fun testComposeScreen_xmlButtonChangesComposeText_noDeadClick() {
        ActivityScenario.launch(AutocaptureComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapViewByContentDescription(
                "xml_btn_compose_text_in_compose",
                scenario
            )

            assertClickWithoutDeadClick("xml_btn_compose_text_in_compose")
        }
    }

    @Test
    fun testComposeScreen_composeButtonChangesXmlText_noDeadClick() {
        ActivityScenario.launch(AutocaptureComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapComposeNode(
                composeTestRule.onNodeWithContentDescription("compose_btn_xml_text_in_compose"),
                scenario
            )

            assertClickWithoutDeadClick("compose_btn_xml_text_in_compose")
        }
    }

    @Test
    fun testComposeScreen_composeButtonChangesComposeText_noDeadClick() {
        ActivityScenario.launch(AutocaptureComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapComposeNode(
                composeTestRule.onNodeWithContentDescription("compose_btn_compose_text_in_compose"),
                scenario
            )

            assertClickWithoutDeadClick("compose_btn_compose_text_in_compose")
        }
    }

    // ==================== Helpers ====================

    /**
     * Asserts that a $mp_click event was captured and NO $mp_dead_click follows
     * within the dead click timeout window.
     *
     * @param description Human-readable test case description for error messages.
     */
    private fun assertClickWithoutDeadClick(description: String) {
        // First: should get $mp_click
        val clickEvent = mEvents.poll(10, TimeUnit.SECONDS)
        assert(clickEvent != null) { "Should capture \$mp_click event for: $description" }
        assert(clickEvent!!.getString("event") == "\$mp_click") {
            "Expected \$mp_click, got: ${clickEvent.getString("event")} for: $description"
        }

        // Second: should NOT get $mp_dead_click (wait longer than dead click timeout)
        val nextEvent = mEvents.poll(3, TimeUnit.SECONDS)
        if (nextEvent != null) {
            assert(nextEvent.getString("event") != "\$mp_dead_click") {
                "\$mp_dead_click should NOT be emitted when button produces UI change. " +
                    "Case: $description"
            }
        }
        // null means no dead click — correct behavior
    }

    /**
     * Sends a real touch event at the center of a Compose node.
     * Scrolls the target into view first (handles both XML ScrollView parents
     * and Compose verticalScroll containers), then uses boundsInWindow for
     * accurate screen coordinates.
     * Calls waitForIdle() after tap to ensure Compose recomposition completes
     * before the dead click detector's timeout fires.
     */
    private fun tapComposeNode(node: SemanticsNodeInteraction, scenario: ActivityScenario<*>) {
        // Scroll into view — handles both activity types:
        // 1. XML activity: scroll the parent ScrollView to show the ComposeView
        scrollComposeViewIntoView(scenario)
        // 2. Compose activity: performScrollTo scrolls the Compose Column
        //    (no-op catch for XML activities where Compose node has no scrollable parent)
        try { node.performScrollTo() } catch (_: AssertionError) { }
        composeTestRule.waitForIdle()

        // Use boundsInRoot + ComposeView screen offset for accurate coordinates.
        // boundsInWindow can be inaccurate for ComposeViews embedded in XML ScrollViews
        // and causes sendPointerSync UID errors on API 34 when coordinates land outside
        // the app window.
        val semanticsNode = node.fetchSemanticsNode()
        val bounds = semanticsNode.boundsInRoot
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2

        // Get the ComposeView's screen position
        val composeViewOffset = intArrayOf(0, 0)
        scenario.onActivity { activity ->
            // Find the ComposeView ancestor of this semantics node
            val composeView = findComposeView(activity.window.decorView)
            composeView?.getLocationOnScreen(composeViewOffset)
        }

        val screenX = composeViewOffset[0] + centerX
        val screenY = composeViewOffset[1] + centerY

        sendTap(screenX, screenY)

        // Wait for Compose recomposition to complete. Without this, the test
        // framework's clock control may prevent recomposition from running,
        // causing the dead click detector's semantic snapshot to see stale state.
        composeTestRule.waitForIdle()
    }

    /**
     * Sends a real touch event at the center of an XML view found by contentDescription.
     * Scrolls the view into the visible area first to handle small CI emulator screens.
     */
    private fun tapViewByContentDescription(
        desc: String,
        scenario: ActivityScenario<*>
    ) {
        val location = floatArrayOf(0f, 0f)
        scenario.onActivity { activity ->
            val view = findViewByContentDescription(
                activity.window.decorView, desc
            ) ?: throw AssertionError("View with contentDescription '$desc' not found")

            // Scroll the view into visible area — it may be below the fold
            view.parent?.requestChildFocus(view, view)

            val loc = intArrayOf(0, 0)
            view.getLocationOnScreen(loc)
            location[0] = loc[0] + view.width / 2f
            location[1] = loc[1] + view.height / 2f
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        sendTap(location[0], location[1])
    }

    private fun findViewByContentDescription(
        root: android.view.View,
        desc: String
    ): android.view.View? {
        if (root.contentDescription?.toString() == desc) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewByContentDescription(root.getChildAt(i), desc)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Finds the first ComposeView (AndroidComposeView) in the view hierarchy.
     * Used to get screen coordinates for Compose nodes via boundsInRoot + screen offset.
     */
    private fun findComposeView(root: android.view.View): android.view.View? {
        if (root.javaClass.name.contains("AndroidComposeView")) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findComposeView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Scrolls the ComposeView into the visible area for XML-based activities.
     * On small emulator screens the mixed-framework elements sit below the fold.
     */
    private fun scrollComposeViewIntoView(scenario: ActivityScenario<*>) {
        scenario.onActivity { activity ->
            val composeView = activity.findViewById<android.view.View>(
                AutocaptureXmlTestActivity.ID_COMPOSE_VIEW
            ) ?: return@onActivity
            // Walk up to find the ScrollView ancestor (ComposeView → LinearLayout → ScrollView)
            var parent = composeView.parent
            while (parent != null && parent !is android.widget.ScrollView) {
                parent = (parent as? android.view.View)?.parent
            }
            if (parent is android.widget.ScrollView) {
                // scrollTo with the view's top relative to the ScrollView's direct child
                parent.scrollTo(0, composeView.top)
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun sendTap(screenX: Float, screenY: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, screenX, screenY, 0
        )
        val up = MotionEvent.obtain(
            downTime, downTime + 50, MotionEvent.ACTION_UP, screenX, screenY, 0
        )
        instrumentation.sendPointerSync(down)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
    }
}
