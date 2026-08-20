package com.mixpanel.android.mpmetrics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * End-to-end test for Compose `$el_id` resolution: a real tap on a Compose element, resolved by the
 * SDK's built-in rules.
 *
 * Complements [com.mixpanel.android.autocapture.DefaultComposeElementIdExtractorTest], which covers
 * the priority matrix directly. This test proves the wiring — [MixpanelOptions] to
 * [AutocaptureOptions] to the Compose semantics path — end to end on a real tap.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ComposeElementIdInstrumentedTest {

    companion object {
        private const val TEST_TOKEN = "COMPOSE_EL_ID_TEST_TOKEN"
    }

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val mEvents: BlockingQueue<JSONObject> = LinkedBlockingQueue()
    private var mMixpanel: MixpanelAPI? = null
    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().context

        val messages = AnalyticsMessages.getInstance(mContext, MPConfig.getInstance(mContext, null))
        messages.hardKill()
        var attempts = 0
        while (!messages.isDead && attempts++ < 40) Thread.sleep(50)

        mEvents.clear()
    }

    @After
    fun tearDown() {
        mMixpanel?.let {
            it.clearSuperProperties()
            it.flush()
        }
        mMixpanel = null
        mEvents.clear()
        mContext.deleteDatabase("mixpanel")
        TestUtils.cleanUpMixpanelData(mContext)
    }

    @Test
    fun testDefaultResolution_TestTagWinsOverContentDescription() {
        // testTag is developer-assigned and never user-visible; contentDescription is user-facing
        // accessibility text that can carry personal data. The safer source must win.
        initMixpanel(AutocaptureOptions.Builder().build())

        val properties = tapBothButtonAndAwaitClick()
        val elId = properties.getString("\$el_id")
        assert(elId == ElementIdComposeTestActivity.TEST_TAG) {
            "Expected the testTag to win, got: $elId"
        }
    }

    // ============ Helpers ============

    /** Taps the testTag + contentDescription button and returns the `$mp_click` properties. */
    private fun tapBothButtonAndAwaitClick(): JSONObject {
        ActivityScenario.launch(ElementIdComposeTestActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            tapNode(composeTestRule.onNodeWithTag(ElementIdComposeTestActivity.TEST_TAG), scenario)

            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
            while (System.currentTimeMillis() < deadline) {
                val event = mEvents.poll(10, TimeUnit.SECONDS) ?: break
                if (event.optString("event") == "\$mp_click") {
                    return event.getJSONObject("properties")
                }
            }
            throw AssertionError("No \$mp_click event captured")
        }
    }

    /**
     * Sends a real touch (DOWN + UP) at the center of a Compose node. sendPointerSync is used
     * because compose-ui-test's performTouchInput bypasses Window.Callback, where autocapture's
     * touch interceptor lives.
     */
    private fun tapNode(node: SemanticsNodeInteraction, scenario: ActivityScenario<*>) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val composeViewOffset = intArrayOf(0, 0)
        scenario.onActivity { activity ->
            val contentView = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            contentView.getChildAt(0).getLocationOnScreen(composeViewOffset)
        }
        val screenX = composeViewOffset[0] + (bounds.left + bounds.right) / 2
        val screenY = composeViewOffset[1] + (bounds.top + bounds.bottom) / 2

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

    /** Builds a MixpanelAPI whose events land in [mEvents], using the given options. */
    private fun initMixpanel(autocaptureOptions: AutocaptureOptions) {
        val mockPreferences = TestUtils.EmptyPreferences(mContext)
        val config = MPConfig.getInstance(mContext, null)

        val mockAdapter = object : MPDbAdapter(mContext, config) {
            override fun addJSON(message: JSONObject, token: String, table: Table): Int {
                if (table == Table.EVENTS && message.optString("event", "").startsWith("\$mp_")) {
                    mEvents.add(message)
                }
                return super.addJSON(message, token, table)
            }
        }

        val customMessages = object : AnalyticsMessages(mContext, config) {
            override fun makeDbAdapter(context: Context): MPDbAdapter = mockAdapter
        }

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
                context.getSharedPreferences(
                    "com.mixpanel.android.mpmetrics.MixpanelAPI_$instanceKey",
                    Context.MODE_PRIVATE
                ).edit().clear().commit()
                context.getSharedPreferences(
                    "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_$instanceKey",
                    Context.MODE_PRIVATE
                ).edit().clear().commit()
                context.getSharedPreferences(
                    "com.mixpanel.android.mpmetrics.Mixpanel",
                    Context.MODE_PRIVATE
                ).edit().clear().putBoolean(token, true).putBoolean("has_launched", true).apply()

                return super.getPersistentIdentity(
                    context, referrerPreferences, token, instanceName, deviceIdProvider
                )
            }
        }
    }
}
