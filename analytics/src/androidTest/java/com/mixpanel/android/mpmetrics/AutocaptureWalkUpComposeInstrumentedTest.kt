package com.mixpanel.android.mpmetrics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Instrumentation tests for walk-up-to-clickable-parent behavior in Compose UIs.
 *
 * Compose uses a different path (findNodeAtPosition in accessibility tree) that
 * implicitly prefers clickable parents over non-clickable children. These tests
 * verify that tapping non-interactive text inside clickable containers correctly
 * resolves to the clickable parent's identity.
 *
 * Uses sendPointerSync for clicks because compose-ui-test's performTouchInput
 * bypasses Window.Callback, which is where autocapture's touch interceptor lives.
 * The compose test rule is used only to find element bounds.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AutocaptureWalkUpComposeInstrumentedTest {

    companion object {
        private const val TEST_TOKEN = "WALKUP_COMPOSE_TEST_TOKEN"
    }

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val mEvents: BlockingQueue<JSONObject> = LinkedBlockingQueue()
    private lateinit var mMixpanel: MixpanelAPI
    private lateinit var mMockAdapter: MPDbAdapter
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

        mMockAdapter = object : MPDbAdapter(mContext, config) {
            override fun addJSON(message: JSONObject, token: String, table: Table): Int {
                if (table == Table.EVENTS) {
                    try {
                        val eventName = message.optString("event", "")
                        if (eventName.startsWith("\$mp_")) {
                            mEvents.add(message)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("WalkUpComposeTest", "Error capturing event", e)
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
     * Uses sendPointerSync so the event goes through Window.Callback -> touch interceptor.
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
     * Tapping plain text inside a clickable Row should resolve to the Row's
     * contentDescription via Compose's implicit walk-up in findNodeAtPosition.
     */
    @Test
    fun testWalkUp_NonInteractiveText_GetsClickableParentId() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithText("Plain text inside clickable row"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_card") {
                "Expected compose_card as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    /**
     * Tapping a Text with its own contentDescription inside a clickable Row should
     * resolve to the clickable parent's contentDescription, not the leaf's.
     */
    @Test
    fun testWalkUp_NonInteractiveTextWithContentDescription_GetsParentId() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_leaf_label"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_parent_of_labeled") {
                "Expected compose_parent_of_labeled as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    /**
     * Tapping a clickable Button with contentDescription inside a clickable Box should
     * resolve to the Button's own contentDescription (no walk-up for clickable nodes).
     */
    @Test
    fun testNoWalkUp_ClickableButtonWithIdentity_GetsOwnId() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_inner_btn"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_inner_btn") {
                "Expected compose_inner_btn as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    /**
     * Tapping text inside nested clickables (outer > inner > text) should resolve to
     * the innermost clickable parent's contentDescription.
     */
    @Test
    fun testWalkUp_NestedClickables_StopsAtInner() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithText("Leaf inside nested clickables"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_inner") {
                "Expected compose_inner as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    /**
     * Tapping a clickable element with no contentDescription or testTag inside a clickable
     * parent should resolve to the inner clickable (no walk-up for clickable nodes).
     * Falls back to hash-based $el_id (Button_<hex> since clickable elements get "Button" tag).
     */
    @Test
    fun testNoWalkUp_ClickableLeafNoIdentity_GetsOwnHash() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithText("Clickable row, no identity"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            val elId = properties.getString("\$el_id")
            assert(elId.matches(Regex("Button_[0-9a-f]+"))) {
                "\$el_id should use hash fallback format Button_<hex>, got: $elId"
            }
        }
    }

    /**
     * Tapping a non-interactive Text with no contentDescription and no clickable ancestor
     * should fall back to hash-based $el_id (Text_<hex> since text elements get "Text" tag).
     */
    @Test
    fun testNoWalkUp_NonInteractiveTextNoClickableAncestor_HashFallback() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithText("Orphan text, no clickable ancestor"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            val elId = properties.getString("\$el_id")
            assert(elId.matches(Regex("Text_[0-9a-f]+"))) {
                "\$el_id should use hash fallback format Text_<hex>, got: $elId"
            }
        }
    }

    /**
     * Tapping a non-interactive Text with contentDescription but no clickable ancestor
     * should use the leaf's own contentDescription as $el_id.
     */
    @Test
    fun testNoWalkUp_NonInteractiveTextWithIdentityNoClickableAncestor_GetsOwnId() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_orphan_label"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_orphan_label") {
                "Expected compose_orphan_label as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    /**
     * Tapping a non-interactive Text 8 levels deep inside a clickable ancestor should
     * successfully resolve to the clickable parent's identity.
     *
     * Note: Compose's findNodeAtPosition recurses top-down (max depth 20), unlike XML's
     * walk-up which searches bottom-up (max depth 10). The effective behavior is the same:
     * deep non-interactive leaves resolve to their clickable ancestor.
     */
    @Test
    fun testWalkUp_DeepNesting_FindsClickableParent() {
        ActivityScenario.launch(WalkUpComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            tapNode(
                composeTestRule.onNodeWithText("Deep leaf in compose"),
                scenario
            )

            val event = mEvents.poll(10, TimeUnit.SECONDS)
            assert(event != null) { "Click event should be captured" }
            event!!

            assert(event.getString("event") == "\$mp_click")

            val properties = event.getJSONObject("properties")
            assert(properties.getString("\$el_id") == "compose_deep_parent") {
                "Expected compose_deep_parent as \$el_id, got: ${properties.getString("\$el_id")}"
            }
        }
    }

    // Tests 9 & 10 from XML (disabled clickable parent) are intentionally skipped for Compose.
    // In Compose, Modifier.clickable(enabled = false) removes the OnClick action from the
    // semantics tree, making the element non-clickable — unlike XML where setClickable(true) +
    // setEnabled(false) keeps isClickable() == true. A disabled Compose element does not
    // consume pointer events, so the parent's click handler fires instead. This makes the
    // disabled-parent scenario equivalent to test case 1 (non-interactive leaf inside
    // clickable parent), so separate tests would be redundant.
}
