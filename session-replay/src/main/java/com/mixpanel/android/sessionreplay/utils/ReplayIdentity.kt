package com.mixpanel.android.sessionreplay.utils

import com.mixpanel.android.identity.MixpanelIdentity

/**
 * How a [MixpanelIdentity] reaches the record endpoint. Empty values are treated as unset, so a
 * replay never sends a key that would resolve to a different user than the app's events.
 */
internal object ReplayIdentity {

    /**
     * The distinct id analytics sends on events for this identity: the user once identified,
     * otherwise `$device:` and the device id. Null when the identity names neither.
     */
    fun distinctId(identity: MixpanelIdentity): String? = identity.userId.orNullIfEmpty()
        ?: identity.deviceId.orNullIfEmpty()?.let { "\$device:$it" }

    /** The identity keys Mixpanel resolves a replay's user from, alongside `distinct_id`. */
    fun queryItems(identity: MixpanelIdentity?): List<Pair<String, String>> {
        if (identity == null) return emptyList()
        return listOfNotNull(
            identity.userId.orNullIfEmpty()?.let { "\$user_id" to it },
            identity.companyId.orNullIfEmpty()?.let { "\$company_id" to it },
            identity.deviceId.orNullIfEmpty()?.let { "\$device_id" to it }
        )
    }

    private fun String?.orNullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }
}
