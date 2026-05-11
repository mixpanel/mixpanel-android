package com.mixpanel.android.sessionreplay.network

import android.content.Context
import com.mixpanel.android.sessionreplay.logging.Logger
import com.mixpanel.android.sessionreplay.utils.APIConstants
import com.mixpanel.android.sessionreplay.utils.EndPoints
import com.mixpanel.android.sessionreplay.utils.PayloadInfo
import com.mixpanel.android.sessionreplay.utils.SessionReplayEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

open class FlushRequest(
    private val token: String,
    @Volatile internal var distinctId: String,
    private val serverUrl: String = EndPoints.DEFAULT_BASE_URL,
    private val network: Network = Network()
) {
    open var networkRequestsAllowedAfterTime = 0.0
    open var networkConsecutiveFailures = 0
    private val headers = mutableMapOf<String, String>()
    private val endPointUrl: String = EndPoints.record(serverUrl)

    internal fun updateDistinctId(newDistinctId: String) {
        distinctId = newDistinctId
    }

    init {
        val authString = "$token:" // Add ':' after the token
        val authHeader = "Basic ${android.util.Base64.encodeToString(authString.toByteArray(), android.util.Base64.NO_WRAP)}"
        headers["Authorization"] = authHeader
        headers["Content-Type"] = "application/octet-stream"
    }

    open suspend fun sendRequest(payloadInfo: PayloadInfo): Boolean =
        withContext(Dispatchers.IO) {
            if (requestNotAllowed()) {
                Logger.warn("Request not allowed due to exponential backoff. Will retry later.")
                return@withContext false
            }

            val requestJSONString = SessionReplayEncoder.jsonPayload(payloadInfo) ?: return@withContext false
            var requestResult = false

            try {
                val requestDataRaw = requestJSONString.toByteArray()
                val requestDataZip = requestDataRaw.gzipCompress()

                // saveToLocalFilesystem(MPSessionReplay.getInstance()?.getContext()!!, requestDataZip, "replay-${System.currentTimeMillis()}.zip")

                val queryParams = buildQueryItems(payloadInfo)

                val apiRequest = APIRequest(
                    endPoint = endPointUrl,
                    method = RequestMethod.POST,
                    requestBody = requestDataZip,
                    queryItems = queryParams,
                    headers = headers
                )

                val result = network.performAPIRequest(apiRequest)

                if (result.isSuccess) {
                    networkConsecutiveFailures = 0
                    updateRetryDelay()
                    requestResult = true
                    Logger.info("Replay batch was ingested successfully")
                } else {
                    val exception = result.exceptionOrNull()
                    val responseMessage = exception?.message ?: "Unknown error"
                    Logger.error("Error sending request: ${exception?.cause}")
                    Logger.error("Response message: $responseMessage")
                    networkConsecutiveFailures++
                    updateRetryDelay()
                }
            } catch (e: Exception) {
                Logger.error("Error sending request: ${e.message}")
            }

            requestResult
        }

    private fun saveToLocalFilesystem(
        context: Context,
        imageData: ByteArray,
        filename: String
    ) {
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            out.write(imageData)
        }
    }

    private fun buildQueryItems(payloadInfo: PayloadInfo): List<Pair<String, String>> =
        listOf(
            "format" to "gzip",
            "distinct_id" to distinctId,
            "seq" to payloadInfo.seq.toString(),
            "batch_start_time" to payloadInfo.batchStartTime.toString(),
            "replay_id" to payloadInfo.replayId,
            "replay_length_ms" to payloadInfo.replayLengthMs.toString(),
            "replay_start_time" to payloadInfo.replayStartTime.toString(),
            "\$lib_version" to APIConstants.currentLibVersion,
            "mp_lib" to APIConstants.currentMpLib
        )

    private fun updateRetryDelay() {
        var retryTime = 0.0

        if (networkConsecutiveFailures >= APIConstants.FAILURES_TILL_BACKOFF) {
            retryTime = max(retryTime, retryBackOffTimeWithConsecutiveFailures(networkConsecutiveFailures))
        }
        val retryDate = Date(Date().time + (retryTime * 1000).toLong()) // Convert to milliseconds
        networkRequestsAllowedAfterTime = retryDate.time / 1000.0 // Convert back to seconds
    }

    private fun retryBackOffTimeWithConsecutiveFailures(failureCount: Int): Double {
        // Note: Kotlin doesn't have a direct equivalent to arc4random_uniform,
        // so we're using a simpler random number generation for demonstration
        val random = Random().nextInt(30)
        val time = 2.0.pow(failureCount - 1) * 60 + random
        return min(max(APIConstants.MIN_RETRY_BACKOFF, time), APIConstants.MAX_RETRY_BACKOFF)
    }

    private fun requestNotAllowed(): Boolean {
        val currentTime = Date().time / 1000.0
        val timeRemaining = networkRequestsAllowedAfterTime - currentTime
        if (timeRemaining > 0) {
            Logger.warn("Request not allowed. Time remaining: $timeRemaining seconds.")
            return true
        }
        return false
    }

    fun isInBackoff(): Boolean = requestNotAllowed()

    private fun ByteArray.gzipCompress(): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter().use { it.write(this.toString(Charsets.UTF_8)) }
        return bos.toByteArray()
    }
}
