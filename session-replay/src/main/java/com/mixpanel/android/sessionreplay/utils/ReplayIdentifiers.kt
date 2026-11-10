package com.mixpanel.android.sessionreplay.utils

import com.mixpanel.android.identity.MixpanelIdentity

/**
 * Who a replay is attributed to: either a distinct id the app passed directly, or an identity.
 * Kept as one value so a flush never pairs a distinct id with a different identity. Each case
 * knows the query parameters that name its user.
 */
internal sealed class ReplayIdentifiers {
    /** The distinct id the replay is attributed to. */
    abstract val distinctId: String

    /** The parameters that tell the record endpoint who the replay belongs to. */
    abstract fun recordParams(): List<Pair<String, String>>

    /** The parameters that tell the replay redirect whose profile to open. */
    abstract fun redirectParams(): List<Pair<String, String>>

    /** A distinct id passed directly. */
    class DistinctId(override val distinctId: String) : ReplayIdentifiers() {
        override fun recordParams() = listOf("distinct_id" to distinctId)

        override fun redirectParams() = listOf("distinct_id" to distinctId)
    }

    /**
     * An identity and the distinct id analytics sends for it. Made with [of]. Empty ids are treated
     * as unset, so a replay never sends a key that would resolve to a different user than the
     * app's events.
     */
    class Identity private constructor(
        val identity: MixpanelIdentity,
        override val distinctId: String
    ) : ReplayIdentifiers() {
        private val userId = identity.userId.orNullIfEmpty()
        private val companyId = identity.companyId.orNullIfEmpty()
        private val deviceId = identity.deviceId.orNullIfEmpty()

        // With both a user and a company, Mixpanel builds the user from $user_id and $company_id,
        // as it does for events, so no distinct id is sent
        private val namedByUserAndCompany = userId != null && companyId != null

        override fun recordParams() = listOfNotNull(
            ("distinct_id" to distinctId).takeUnless { namedByUserAndCompany },
            userId?.let { "\$user_id" to it },
            companyId?.let { "\$company_id" to it },
            deviceId?.let { "\$device_id" to it }
        )

        override fun redirectParams() =
            if (namedByUserAndCompany) {
                listOf("\$user_id" to userId!!, "\$company_id" to companyId!!)
            } else {
                listOf("distinct_id" to distinctId)
            }

        companion object {
            /**
             * The identity with the distinct id analytics sends on its events: the user once
             * identified, otherwise `$device:` and the device id. Null when it names neither.
             */
            fun of(identity: MixpanelIdentity): Identity? {
                val distinctId = identity.userId.orNullIfEmpty()
                    ?: identity.deviceId.orNullIfEmpty()?.let { "\$device:$it" }
                    ?: return null
                return Identity(identity, distinctId)
            }
        }
    }
}

private fun String?.orNullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }
