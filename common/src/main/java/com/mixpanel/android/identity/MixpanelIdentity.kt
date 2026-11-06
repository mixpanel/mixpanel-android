package com.mixpanel.android.identity

/**
 * Names who events, profile updates and replays belong to, shared by the Mixpanel Android SDKs.
 *
 * Analytics owns the device id, so it reports a complete identity through
 * `MixpanelAPI.getIdentity()` and takes only [userId] and [companyId] when identifying. Session
 * Replay owns none of these values and takes the identity analytics reports, so its recordings
 * resolve to the same user as the app's events:
 *
 * ```kotlin
 * mixpanel.identify(MixpanelIdentity(userId = "user_123", companyId = "acme"))
 * MPSessionReplay.getInstance()?.identify(mixpanel.identity)
 * ```
 *
 * On a project that uses hierarchical groups, Mixpanel builds the user's distinct id from
 * `$company_id` and `$user_id`, so the company is part of the user's identity: the same user id in
 * two companies is two users. Other projects ignore [companyId].
 *
 * Every value is nullable so that a null from Java is reported by the SDK it is passed to rather
 * than thrown here. Instances are immutable.
 *
 * @property userId the user's id. On a hierarchical groups project it only needs to be unique
 *     within the company.
 * @property companyId the id of the user's top-level company, on a hierarchical groups project
 * @property deviceId the device id analytics assigned. Events and replays that carry it are
 *     attributed to the device until the user is known, then follow the user.
 * @constructor For internal use by the Mixpanel SDKs only, which own the device id. Apps create an
 *     identity with the constructor that takes only a user id and company id.
 */
class MixpanelIdentity(
    val userId: String?,
    val companyId: String?,
    val deviceId: String?
) {

    /**
     * Creates an identity for a user and, on a hierarchical groups project, their company.
     *
     * @param userId the user's id
     * @param companyId the id of the user's company, on a hierarchical groups project
     */
    @JvmOverloads
    constructor(userId: String?, companyId: String? = null) : this(userId, companyId, null)

    override fun equals(other: Any?): Boolean = other is MixpanelIdentity &&
        userId == other.userId &&
        companyId == other.companyId &&
        deviceId == other.deviceId

    override fun hashCode(): Int {
        var result = userId?.hashCode() ?: 0
        result = 31 * result + (companyId?.hashCode() ?: 0)
        result = 31 * result + (deviceId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = listOfNotNull(
        userId?.let { "userId=$it" },
        companyId?.let { "companyId=$it" },
        deviceId?.let { "deviceId=$it" }
    ).joinToString(prefix = "MixpanelIdentity(", postfix = ")")
}
