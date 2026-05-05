package com.mixpanel.android.sessionreplay.utils

import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.models.ReplayJSONTemplate
import com.mixpanel.android.sessionreplay.models.SessionEvent
import com.mixpanel.android.sessionreplay.models.SessionEventData
import com.mixpanel.android.sessionreplay.models.SessionTrackingData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

@Serializable // Make them serializable with kotlinx.serialization
data class IdentityInfo(
    val distinctId: String
)

@Serializable
data class PayloadInfo(
    val sessionEvents: List<SessionEvent>,
    val batchStartTime: Double, // TimeInterval translates to Double in Kotlin
    val seq: Int,
    val replayId: String,
    val replayLengthMs: Int,
    val replayStartTime: Double
)

object SessionReplayEncoder {
    private val json = Json {
        prettyPrint = true
    }

    fun serialize(data: SessionTrackingData): String? =
        try {
            json.encodeToString(data)
        } catch (e: Exception) {
            Logger.error("Error serializing JSON: ${e.message}")
            null
        }

    fun jsonPayload(payloadInfo: PayloadInfo): String? {
        val batchStartTimestamp = payloadInfo.sessionEvents.minOfOrNull { it.timestamp } ?: Date().time

        val metaEvent = SessionEvent(
            type = EventType.META,
            data = SessionEventData.DimensionData(DeviceInfo.screenWidth, DeviceInfo.screenHeight),
            timestamp = batchStartTimestamp
        )
        val allEvents = listOf(metaEvent) + payloadInfo.sessionEvents

        return try {
            val jsonString = json.encodeToString(allEvents)
            jsonString
        } catch (e: Exception) {
            println("Error serializing JSON: ${e.message}")
            Logger.error("Failed to encode/save JSON: ${e.message}")
            null
        }
    }

    fun deserialize(jsonString: String): SessionTrackingData? =
        try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            Logger.error("Error decoding JSON: ${e.message}")
            null
        }

    fun jsonSessionData(eventData: SessionTrackingData): String? =
        try {
            val jsonString = json.encodeToString(eventData)
            Logger.info("Serialized JSON string: $jsonString")
            jsonString
        } catch (e: Exception) {
            Logger.error("Error encoding JSON: ${e.message}")
            null
        }

    fun getCurrentTimestampMillis(): Long = Date().time

    fun incrementalSessionEvent(image: ByteArray): SessionEvent {
        val attributesData = SessionEventData.AttributesData(
            source = IncrementalSource.MUTATION,
            texts = emptyList(),
            attributes = listOf(
                SessionEventData.AttributesData.Attribute(
                    PayloadObjectId.MAIN_SNAPSHOT,
                    mapOf("src" to "data:image/jpeg;base64,${android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP)}")
                )
            ),
            removes = emptyList(),
            adds = emptyList()
        )

        return SessionEvent(
            type = EventType.INCREMENTAL_SNAPSHOT,
            data = attributesData,
            timestamp = getCurrentTimestampMillis()
        )
    }

    fun mainSessionEvent(image: ByteArray): SessionEvent? = try {
        val replayJson = ReplayJSONTemplate.mainEventJSON(
            imageBase64 = android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP),
            timestamp = getCurrentTimestampMillis()
        )
        val x = json.decodeFromString<SessionEvent>(replayJson).also { it.timestamp = getCurrentTimestampMillis() }
        x
    } catch (e: Exception) {
        Logger.error("Error decoding main event JSON: ${e.message}")
        null
    }
}
