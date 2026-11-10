package com.mixpanel.android.sessionreplay.utils

import com.mixpanel.android.identity.MixpanelIdentity

/**
 * Who a replay is attributed to: either a distinct id the app passed directly, or an identity.
 * Kept as one value so a flush never pairs a distinct id with a different identity.
 */
internal sealed class ReplayIdentifiers {
    /** The distinct id the replay is attributed to. */
    abstract val distinctId: String

    /** The identity the distinct id was derived from; null for a distinct id passed directly. */
    abstract val identity: MixpanelIdentity?

    /** A distinct id passed directly. Requests carry it and no identity keys. */
    class DistinctId(override val distinctId: String) : ReplayIdentifiers() {
        override val identity: MixpanelIdentity?
            get() = null
    }

    /** An identity and the distinct id analytics sends for it. Made with [of]. */
    class Identity private constructor(
        override val identity: MixpanelIdentity,
        override val distinctId: String
    ) : ReplayIdentifiers() {
        companion object {
            /** Null when the identity names neither a user nor a device. */
            fun of(identity: MixpanelIdentity): Identity? =
                ReplayIdentity.distinctId(identity)?.let { Identity(identity, it) }
        }
    }
}
