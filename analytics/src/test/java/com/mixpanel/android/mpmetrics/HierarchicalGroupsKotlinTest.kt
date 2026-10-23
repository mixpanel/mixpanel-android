package com.mixpanel.android.mpmetrics

import androidx.test.core.app.ApplicationProvider
import com.mixpanel.android.identity.MixpanelIdentity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** Kotlin callers use named arguments and property syntax against the Java API. */
@RunWith(RobolectricTestRunner::class)
class HierarchicalGroupsKotlinTest {

    @Test
    fun identifyWithNamedArguments() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val options = MixpanelOptions.Builder().hierarchicalGroups(true).build()
        val mixpanel = TestUtils.CleanMixpanelAPI(
            context,
            TestUtils.EmptyPreferences(context),
            UUID.randomUUID().toString(),
            options,
        )

        mixpanel.identify(MixpanelIdentity(userId = "user_123", companyId = "acme"))

        assertEquals(
            MixpanelIdentity(userId = "user_123", companyId = "acme", deviceId = mixpanel.anonymousId),
            mixpanel.identity,
        )
    }
}
