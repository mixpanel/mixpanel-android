package com.mixpanel.android.mpmetrics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
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
 * Instrumentation tests for autocapture on Jetpack Compose UIs.
 *
 * Uses sendPointerSync for clicks because compose-ui-test's performTouchInput
 * bypasses Window.Callback, which is where autocapture's TouchInterceptor lives.
 * The compose test rule is used only to find element bounds.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ComposeAutocaptureInstrumentedTest {

    companion object {
        private const val TEST_TOKEN = "COMPOSE_AUTOCAPTURE_TEST_TOKEN"
    }

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val mEvents: BlockingQueue<JSONObject> = LinkedBlockingQueue()
    private lateinit var mMixpanel: MixpanelAPI
    private lateinit var mMockAdapter: MPDbAdapter
    private lateinit var mContext: Context
    private var mDecorViewOffset = intArrayOf(0, 0)

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().context

        val messages = AnalyticsMessages.getInstance(
            mContext,
            MPConfig.getInstance(mContext, null)
        )
        messages.hardKill()
        Thread.sleep(2000)

        mEvents.clear()

        val mockPreferences = TestUtils.EmptyPreferences(mContext)
        val config = MPConfig.getInstance(mContext, null)

        mMockAdapter = object : MPDbAdapter(mContext, config) {
            override fun addJSON(message: JSONObject, token: String, table: Table): Int {
                if (table == Table.EVENTS) {
                    try {
                        val eventName = message.optString("event", "")
                        if (eventName.startsWith("\$mp_")) {
                            mEvents.add(message)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ComposeAutocaptureTest", "Error capturing event", e)
                    }
                }
                return super.addJSON(message, token, table)
            }
        }

        val customMessages = object : AnalyticsMessages(mContext, config) {
            override fun makeDbAdapter(context: Context): MPDbAdapter = mMockAdapter
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

    /**
     * Gets the screen coordinates of the center of a Compose node.
     * boundsInRoot is relative to the AndroidComposeView, so we need its screen position.
     */
    private fun getNodeScreenCenter(
        node: SemanticsNodeInteraction,
        scenario: ActivityScenario<*>
    ): FloatArray {
        val semanticsNode = node.fetchSemanticsNode()
        val bounds = semanticsNode.boundsInRoot
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2

        // Get the AndroidComposeView's screen position (not decor view)
        val composeViewOffset = intArrayOf(0, 0)
        scenario.onActivity { activity ->
            val contentView =
                activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val composeView = contentView.getChildAt(0)
            composeView.getLocationOnScreen(composeViewOffset)
        }

        return floatArrayOf(
            composeViewOffset[0] + centerX,
            composeViewOffset[1] + centerY
        )
    }

    /**
     * Sends a real touch event (DOWN + UP) at the center of a Compose node.
     * Uses sendPointerSync so the event goes through Window.Callback → TouchInterceptor.
     */
    private fun tapNode(node: SemanticsNodeInteraction, scenario: ActivityScenario<*>) {
        val (screenX, screenY) = getNodeScreenCenter(node, scenario)

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

    /**
     * Sends N rapid taps at the center of a Compose node (for rage click testing).
     */
    private fun rapidTapNode(
        node: SemanticsNodeInteraction,
        scenario: ActivityScenario<*>,
        count: Int
    ) {
        val (screenX, screenY) = getNodeScreenCenter(node, scenario)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (i in 0 until count) {
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, screenX, screenY, 0
            )
            val up = MotionEvent.obtain(
                downTime, downTime + 10, MotionEvent.ACTION_UP, screenX, screenY, 0
            )
            instrumentation.sendPointerSync(down)
            instrumentation.sendPointerSync(up)
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun testComposeClickEventBasic() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_rule1_btn"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_rule1_btn")
            assert(properties.getString("\$el_tag_name") == "Button")
            assert(properties.getDouble("\$x") >= 0)
            assert(properties.getDouble("\$y") >= 0)
        }
    }

    @Test
    fun testComposeElementIdFromTestTag() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithTag("compose_rule2_btn"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Event should be captured" }
            event!!

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_rule2_btn") {
                "Expected testTag as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    @Test
    fun testComposeElementIdHashFallback() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithText("Rule 3 - hash fallback"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Event should be captured" }
            event!!

            val properties = event.getJSONObject("properties")
            val elId = properties.getString("\$el_id")
            assert(elId.matches(Regex("Button_view_-?[0-9a-f]+"))) {
                "\$el_id should use hash fallback format, got: $elId"
            }
        }
    }

    @Test
    fun testComposeRageClickDetection() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            rapidTapNode(
                composeTestRule.onNodeWithContentDescription("compose_rage_zone"),
                scenario,
                4
            )

            val events = mutableListOf<JSONObject>()
            var event: JSONObject?
            while (mEvents.poll(2, TimeUnit.SECONDS).also { event = it } != null) {
                events.add(event!!)
            }

            val rageClickEvent = events.find { it.getString("event") == "\$mp_rage_click" }
            assert(rageClickEvent != null) { "Rage click event should be captured" }

            val properties = rageClickEvent!!.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_rage_zone")
        }
    }

    @Test
    fun testComposeDeadClickDetection() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_dead_btn"),
                scenario
            )

            val clickEvent = mEvents.poll(2, TimeUnit.SECONDS)
            assert(clickEvent != null) { "Click event should be captured first" }
            assert(clickEvent!!.getString("event") == "\$mp_click")

            val deadClickEvent = mEvents.poll(3, TimeUnit.SECONDS)
            assert(deadClickEvent != null) { "Dead click event should be captured" }
            assert(deadClickEvent!!.getString("event") == "\$mp_dead_click")

            val properties = deadClickEvent.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_dead_btn")
        }
    }

    @Test
    fun testComposePrivacyFilterBlocksEvents() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithContentDescription("mp-sensitive"),
                scenario
            )

            val event = mEvents.poll(2, TimeUnit.SECONDS)
            assert(event == null) { "Sensitive element should not emit any event" }
        }
    }

    @Test
    fun testComposeMultipleClicksGenerateMultipleEvents() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_rule1_btn"),
                scenario
            )
            Thread.sleep(200)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_dead_btn"),
                scenario
            )
            Thread.sleep(200)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_rage_zone"),
                scenario
            )
            Thread.sleep(200)

            val clickEvents = mutableListOf<JSONObject>()
            var event: JSONObject?
            while (mEvents.poll(2, TimeUnit.SECONDS).also { event = it } != null) {
                if (event!!.getString("event") == "\$mp_click") {
                    clickEvents.add(event!!)
                }
            }

            assert(clickEvents.size == 3) {
                "Should capture exactly 3 click events, got: ${clickEvents.size}"
            }
        }
    }

    @Test
    fun testComposeClickEventCapturesElText() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_rule1_btn"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Event should be captured" }
            event!!

            val properties = event.getJSONObject("properties")
            assert(properties.has("\$el_text")) { "\$el_text should exist" }
            assert(properties.getString("\$el_text") == "Rule 1 - contentDescription") {
                "Expected button text, got: ${properties.getString("\$el_text")}"
            }
        }
    }

    @Test
    fun testComposeClickEventHasTokenProperty() {
        ActivityScenario.launch(ComposeAutocaptureTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_rule1_btn"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Event should be captured" }
            event!!

            val properties = event.getJSONObject("properties")
            assert(properties.has("distinct_id")) { "distinct_id should exist" }
            assert(properties.getString("token") == TEST_TOKEN) {
                "Token should match, got: ${properties.getString("token")}"
            }
        }
    }
}
