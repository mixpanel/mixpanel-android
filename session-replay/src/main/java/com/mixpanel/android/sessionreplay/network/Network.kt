package com.mixpanel.android.sessionreplay.network

import com.mixpanel.android.sessionreplay.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class RequestMethod {
    GET,
    POST
}

data class APIRequest(
    val endPoint: String,
    val method: RequestMethod,
    val requestBody: ByteArray? = null,
    val queryItems: List<Pair<String, String>>? = null,
    val headers: Map<String, String>,
    val timeout: Long? = null
)

open class Network {
    open suspend fun performAPIRequest(apiRequest: APIRequest): Result<Unit> =
        executeAPIRequest(apiRequest) { _ -> Result.success(Unit) }

    open suspend fun performAPIRequestWithResponse(apiRequest: APIRequest): Result<String> =
        executeAPIRequest(apiRequest) { connection ->
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
            Result.success(response)
        }

    private suspend fun <T> executeAPIRequest(
        apiRequest: APIRequest,
        onSuccess: (HttpURLConnection) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val url = buildURL(apiRequest)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = apiRequest.method.name

            apiRequest.timeout?.let {
                connection.connectTimeout = it.toInt()
                connection.readTimeout = it.toInt()
            }

            apiRequest.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            apiRequest.requestBody?.let {
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(it)
                }
            }

            Logger.debug(message = "Fetching URL")
            Logger.debug(message = connection.url.toString())

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                onSuccess(connection)
            } else {
                val errorStream = BufferedReader(InputStreamReader(connection.errorStream))
                val errorMessage = errorStream.readText()
                Result.failure(Exception("Error: ${connection.responseCode} - $errorMessage"))
            }
        } catch (e: Exception) {
            Logger.error(message = "Error in network request: ${e.message}")
            Result.failure(e)
        }
    }

    private fun buildURL(apiRequest: APIRequest): URL {
        val urlBuilder = StringBuilder(apiRequest.endPoint)

        apiRequest.queryItems?.let {
            if (it.isNotEmpty()) {
                urlBuilder.append("?")
                it.forEachIndexed { index, (key, value) ->
                    urlBuilder
                        .append(URLEncoder.encode(key, "UTF-8"))
                        .append("=")
                        .append(URLEncoder.encode(value, "UTF-8"))
                    if (index < it.size - 1) {
                        urlBuilder.append("&")
                    }
                }
            }
        }
        return URL(urlBuilder.toString())
    }
}
