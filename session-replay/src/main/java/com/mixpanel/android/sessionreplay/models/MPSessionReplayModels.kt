package com.mixpanel.android.sessionreplay.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SessionTrackingData(
    @SerialName("distinct_id") val distinctId: String,
    @SerialName("\$device_id") val deviceId: String,
    val seq: Int,
    val events: List<SessionEvent>,
    @SerialName("batch_start_time") val batchStartTime: Double,
    @SerialName("replay_id") val replayId: String,
    @SerialName("replay_length_ms") val replayLengthMs: Int,
    @SerialName("replay_start_time") val replayStartTime: Double
)

@Serializable
data class SessionEvent(
    val type: Int,
    @Serializable(with = SessionEventDataSerializer::class) // Use the custom serializer
    val data: SessionEventData?,
    var timestamp: Long
)

@Serializable
sealed class SessionEventData {
    abstract val discriminator: String

    @Serializable
    data class DimensionData(
        val width: Int,
        val height: Int,
        override val discriminator: String = "dimension"
    ) : SessionEventData()

    @Serializable
    data class NodeData(
        val node: SessionNode,
        override val discriminator: String = "node"
    ) : SessionEventData()

    @Serializable
    data class PositionData(
        val source: Int,
        val positions: List<SessionPosition>,
        override val discriminator: String = "position"
    ) : SessionEventData()

    @Serializable
    data class DetailedData(
        val source: Int,
        val type: Int,
        val id: Int,
        val x: Int,
        val y: Int,
        override val discriminator: String = "detailed"
    ) : SessionEventData()

    @Serializable
    data class AttributesData(
        val source: Int,
        val texts: List<String>,
        val attributes: List<Attribute>,
        val removes: List<String>,
        val adds: List<String>,
        override val discriminator: String = "attributes"
    ) : SessionEventData() {
        @Serializable
        data class Attribute(
            val id: Int,
            val attributes: Map<String, String>
        )
    }
}

@OptIn(ExperimentalSerializationApi::class)
object SessionEventDataSerializer : KSerializer<SessionEventData> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("SessionEventData") {
            element<String>("discriminator")
        }

    override fun serialize(
        encoder: Encoder,
        value: SessionEventData
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("This class can be saved only by JSON")
        val jsonElement =
            when (value) {
                is SessionEventData.DimensionData ->
                    jsonEncoder.json.encodeToJsonElement(
                        SessionEventData.DimensionData.serializer(),
                        value
                    )

                is SessionEventData.NodeData ->
                    jsonEncoder.json.encodeToJsonElement(
                        SessionEventData.NodeData.serializer(),
                        value
                    )

                is SessionEventData.PositionData ->
                    jsonEncoder.json.encodeToJsonElement(
                        SessionEventData.PositionData.serializer(),
                        value
                    )

                is SessionEventData.DetailedData ->
                    jsonEncoder.json.encodeToJsonElement(
                        SessionEventData.DetailedData.serializer(),
                        value
                    )

                is SessionEventData.AttributesData ->
                    jsonEncoder.json.encodeToJsonElement(
                        SessionEventData.AttributesData.serializer(),
                        value
                    )
            }

        val jsonObject = jsonElement.jsonObject.toMutableMap()
        jsonObject["discriminator"] = JsonPrimitive(value.discriminator)
        jsonEncoder.encodeJsonElement(JsonObject(jsonObject))
    }

    override fun deserialize(decoder: Decoder): SessionEventData {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("This class can be loaded only by JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val discriminator =
            jsonObject["discriminator"]?.jsonPrimitive?.content
                ?: throw SerializationException("Missing discriminator")

        return when (discriminator) {
            "dimension" ->
                jsonDecoder.json.decodeFromJsonElement(
                    SessionEventData.DimensionData.serializer(),
                    jsonObject
                )

            "node" ->
                jsonDecoder.json.decodeFromJsonElement(
                    SessionEventData.NodeData.serializer(),
                    jsonObject
                )

            "position" ->
                jsonDecoder.json.decodeFromJsonElement(
                    SessionEventData.PositionData.serializer(),
                    jsonObject
                )

            "detailed" ->
                jsonDecoder.json.decodeFromJsonElement(
                    SessionEventData.DetailedData.serializer(),
                    jsonObject
                )

            "attributes" ->
                jsonDecoder.json.decodeFromJsonElement(
                    SessionEventData.AttributesData.serializer(),
                    jsonObject
                )

            else -> throw SerializationException("Unknown discriminator: $discriminator")
        }
    }
}

@Serializable
data class SessionNode(
    val type: Int,
    val name: String? = null,
    val publicId: String? = null,
    val systemId: String? = null,
    val tagName: String? = null,
    val attributes: Map<String, String>? = null,
    val textContent: String? = null,
    val isStyle: Boolean? = null,
    val childNodes: List<SessionNode>? = null,
    val id: Int
)

@Serializable
data class SessionPosition(
    val x: Double,
    val y: Double,
    val id: Int,
    @SerialName("time_offset") val timeOffset: Int
)
