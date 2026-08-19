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
import com.mixpanel.android.autocapture.ComposeElementIdExtractor
import com.mixpanel.android.autocapture.ComposeElementInfo
import com.mixpanel.android.autocapture.ViewElementIdExtractor
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
 * End-to-end tests for [ComposeElementIdExtractor]: real taps on a Compose element, with and without
 * a host-app supplied extractor.
 *
 * Complements [com.mixpanel.android.autocapture.DefaultComposeElementIdExtractorTest], which covers
 * the priority matrix at the extractor level. These tests prove the wiring — [MixpanelOptions] to
 * [AutocaptureOptions] to the Compose semantics path — and that an identifier policy configured for
 * one path is never bypassed on the other.
 *
 * Mixpanel is initialized per test via [initMixpanel] rather than in `@Before`, because each test
 * needs different autocapture options.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ComposeElementIdExtractorInstrumentedTest {

    companion object {
        private const val TEST_TOKEN = "COMPOSE_EL_ID_EXTRACTOR_TEST_TOKEN"

        /** Matches the anonymous fallback: TagName_hexHash. */
        private val HASH_ID_PATTERN = Regex("[A-Za-z0-9_]+_-?[0-9a-f]+")
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

    // ============ Custom Compose extractor ============

    @Test
    fun testCustomComposeExtractorReplacesDefaultResolution() {
        initMixpanel(
            AutocaptureOptions.Builder()
                .composeElementIdExtractor { _ -> "custom_compose_el_id" }
                .build()
        )

        val properties = tapBothButtonAndAwaitClick()
        assert(properties.getString("\$el_id") == "custom_compose_el_id") {
            "Expected the custom id, got: ${properties.getString("\$el_id")}"
        }
    }

    @Test
    fun testCustomComposeExtractorReceivesElementSemantics() {
        // The extractor is handed the tapped element's semantics, so it can key off the app's own
        // test tags.
        initMixpanel(
            AutocaptureOptions.Builder()
                .composeElementIdExtractor { element: ComposeElementInfo ->
                    if (element.testTag == ElementIdComposeTestActivity.TEST_TAG) {
                        "saw_${element.role}_${element.contentDescription}"
                    } else {
                        null
                    }
                }
                .build()
        )

        val properties = tapBothButtonAndAwaitClick()
        assert(properties.getString("\$el_id") == "saw_Button_Compose Both Label") {
            "Extractor should see testTag, role and contentDescription, got: " +
                properties.getString("\$el_id")
        }
    }

    @Test
    fun testCustomComposeExtractorReturningNullFallsBackToAnonymousId() {
        // Returning null means "report nothing identifying" — the SDK must not quietly fall back to
        // the semantics the developer declined to expose.
        initMixpanel(
            AutocaptureOptions.Builder()
                .composeElementIdExtractor { _ -> null }
                .build()
        )

        assertAnonymousElementId(tapBothButtonAndAwaitClick())
    }

    @Test
    fun testThrowingComposeExtractorDoesNotBreakTracking() {
        initMixpanel(
            AutocaptureOptions.Builder()
                .composeElementIdExtractor { _ -> throw IllegalStateException("host app bug") }
                .build()
        )

        // The event still lands, with an anonymous id — a host-app bug must never drop events or
        // crash the app.
        assertAnonymousElementId(tapBothButtonAndAwaitClick())
    }

    // ============ Cross-path policy ============

    @Test
    fun testViewExtractorAloneMakesComposeIdsAnonymous() {
        // An app that took control of View identifiers must not have Compose semantics reported
        // instead: the Compose path has no View to hand the extractor, so it reports the anonymous
        // id rather than testTag or contentDescription.
        initMixpanel(
            AutocaptureOptions.Builder()
                .viewElementIdExtractor(ViewElementIdExtractor { _ -> "custom_view_el_id" })
                .build()
        )

        val properties = tapBothButtonAndAwaitClick()
        val elId = properties.getString("\$el_id")
        assertAnonymousElementId(properties)
        assert(!elId.contains(ElementIdComposeTestActivity.TEST_TAG)) {
            "testTag must not leak when only a View extractor is configured. Got: $elId"
        }
        assert(!elId.contains("Compose Both Label")) {
            "contentDescription must not leak when only a View extractor is configured. Got: $elId"
        }
    }

    @Test
    fun testBothExtractorsGovernTheirOwnPaths() {
        initMixpanel(
            AutocaptureOptions.Builder()
                .viewElementIdExtractor(ViewElementIdExtractor { _ -> "custom_view_el_id" })
                .composeElementIdExtractor { _ -> "custom_compose_el_id" }
                .build()
        )

        val properties = tapBothButtonAndAwaitClick()
        assert(properties.getString("\$el_id") == "custom_compose_el_id") {
            "The Compose extractor should govern Compose taps, got: ${properties.getString("\$el_id")}"
        }
    }

    // ============ Options plumbing ============

    @Test
    fun testOptionsDefaultToNoComposeExtractor() {
        assert(AutocaptureOptions.Builder().build().composeElementIdExtractor == null)
    }

    @Test
    fun testOptionsRetainAndCopyTheComposeExtractor() {
        val extractor = ComposeElementIdExtractor { _ -> "id" }
        val options = AutocaptureOptions.Builder().composeElementIdExtractor(extractor).build()
        assert(options.composeElementIdExtractor === extractor)

        val copy = AutocaptureOptions.Builder(options).build()
        assert(copy.composeElementIdExtractor === extractor) {
            "Builder(source) must carry the Compose extractor over"
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

    private fun assertAnonymousElementId(properties: JSONObject) {
        val elId = properties.getString("\$el_id")
        assert(HASH_ID_PATTERN.matches(elId)) {
            "Expected the anonymous <TagName>_<hash> id, got: $elId"
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
