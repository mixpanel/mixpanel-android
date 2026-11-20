package com.mixpanel.android.mpmetrics

import androidx.test.core.app.ApplicationProvider
import com.mixpanel.android.identity.MixpanelIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** Kotlin callers use named arguments and property syntax against the Java API. */
@RunWith(RobolectricTestRunner::class)
class HierarchicalGroupsKotlinTest {

    @Test
    fun identifyWithNamedArguments() {
        val mixpanel = newApi(hierarchicalGroups = true)

        mixpanel.identify(MixpanelIdentity(userId = "user_123", companyId = "acme"))

        assertEquals(
            MixpanelIdentity(userId = "user_123", companyId = "acme", deviceId = mixpanel.anonymousId),
            mixpanel.identity,
        )
    }

    @Test
    fun companyIsNullBeforeIdentify() {
        val mixpanel = newApi(hierarchicalGroups = true)

        val company: MixpanelAPI.Group? = mixpanel.company
        assertNull(company)
        mixpanel.company?.set("tier", "enterprise")
    }

    @Test
    fun companyIsNullWhenDisabled() {
        val mixpanel = newApi(hierarchicalGroups = false)
        mixpanel.identify(MixpanelIdentity(userId = "user_123", companyId = "acme"))

        assertNull(mixpanel.company)
    }

    @Test
    fun companySetWithSafeCall() {
        val mixpanel = newApi(hierarchicalGroups = true)
        mixpanel.identify(MixpanelIdentity(userId = "user_123", companyId = "acme"))

        mixpanel.company?.set("tier", "enterprise")

        assertSame(mixpanel.getGroup("\$company_id", "acme"), mixpanel.company)
    }

    private fun newApi(hierarchicalGroups: Boolean): MixpanelAPI {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val options = MixpanelOptions.Builder().hierarchicalGroups(hierarchicalGroups).build()
        return TestUtils.CleanMixpanelAPI(
            context,
            TestUtils.EmptyPreferences(context),
            UUID.randomUUID().toString(),
            options,
        )
    }
}
