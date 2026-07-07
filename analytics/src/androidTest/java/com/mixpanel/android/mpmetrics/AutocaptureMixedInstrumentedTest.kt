package com.mixpanel.android.mpmetrics

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * Tests for cross-framework (mixed Compose/XML) dead click detection.
 *
 * Verifies that a Compose button click which modifies only XML views is
 * correctly detected as a UI change (not a dead click).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AutocaptureMixedInstrumentedTest {

    companion object {
        private const val TEST_TOKEN = "MIXED_AUTOCAPTURE_TEST_TOKEN"
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
                        android.util.Log.e("MixedAutocaptureTest", "Error capturing event", e)
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

    private fun getNodeScreenCenter(
        node: SemanticsNodeInteraction,
        scenario: ActivityScenario<*>
    ): FloatArray {
        val semanticsNode = node.fetchSemanticsNode()
        val bounds = semanticsNode.boundsInRoot
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2

        val composeViewOffset = intArrayOf(0, 0)
        scenario.onActivity { activity ->
            val contentView =
                activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            findComposeView(contentView)?.getLocationOnScreen(composeViewOffset)
        }

        return floatArrayOf(
            composeViewOffset[0] + centerX,
            composeViewOffset[1] + centerY
        )
    }

    private fun findComposeView(view: android.view.View): android.view.View? {
        if (view.javaClass.name.contains("ComposeView")) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
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

    private fun tapNode(node: SemanticsNodeInteraction, scenario: ActivityScenario<*>) {
        val (screenX, screenY) = getNodeScreenCenter(node, scenario)
        sendTap(screenX, screenY)
    }

    /**
     * Verifies that a Compose button click which modifies only an XML TextView
     * is NOT reported as a dead click.
     *
     * In mixed Compose/XML layouts, ComposeUiChangeMonitor now also monitors the
     * XML view hierarchy (via ViewTreeObserver listeners and content hash comparison),
     * so cross-framework UI changes are correctly detected.
     */
    @Test
    fun testComposeClickUpdatingXmlView_notDeadClick() {
        ActivityScenario.launch(AutocaptureMixedTestActivity::class.java).use { scenario ->
            Thread.sleep(1000)

            // Tap the Compose button — its onClick changes the XML TextView
            tapNode(
                composeTestRule.onNodeWithContentDescription("compose_updates_xml_btn"),
                scenario
            )

            // Should get a click event
            val clickEvent = mEvents.poll(2, TimeUnit.SECONDS)
            assert(clickEvent != null) { "Click event should be captured" }
            assert(clickEvent!!.getString("event") == "\$mp_click")

            // Should NOT get a dead click — the XML TextView changed
            val deadClickEvent = mEvents.poll(3, TimeUnit.SECONDS)
            assert(deadClickEvent == null) {
                "No dead click should be reported — the Compose click caused an XML UI change"
            }
        }
    }
}
