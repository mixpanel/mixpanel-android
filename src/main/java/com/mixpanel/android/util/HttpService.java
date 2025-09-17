package com.mixpanel.android.util;

import static com.mixpanel.android.util.MPConstants.URL.MIXPANEL_API;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/** An HTTP utility class for internal use in the Mixpanel library. Not thread-safe. */
public class HttpService implements RemoteService {

    private final boolean shouldGzipRequestPayload;
    private final MixpanelNetworkErrorListener networkErrorListener;
    private String mBackupHost;

    private static boolean sIsMixpanelBlocked;
    private static final int MIN_UNAVAILABLE_HTTP_RESPONSE_CODE =
            HttpURLConnection.HTTP_INTERNAL_ERROR;
    private static final int MAX_UNAVAILABLE_HTTP_RESPONSE_CODE = 599;

    public HttpService(
            boolean shouldGzipRequestPayload,
            MixpanelNetworkErrorListener networkErrorListener,
            String backupHost) {
        this.shouldGzipRequestPayload = shouldGzipRequestPayload;
        this.networkErrorListener = networkErrorListener;
        this.mBackupHost = backupHost;
    }

    public HttpService(
            boolean shouldGzipRequestPayload, MixpanelNetworkErrorListener networkErrorListener) {
        this(shouldGzipRequestPayload, networkErrorListener, null);
    }

    public HttpService() {
        this(false, null, null);
    }

    public void setBackupHost(String backupHost) {
        this.mBackupHost = backupHost;
    }

    @Override
    public void checkIsMixpanelBlocked() {
        Thread t =
                new Thread(
                        new Runnable() {
                            public void run() {
                                try {
                                    long startTimeNanos = System.nanoTime();
                                    String host = "api.mixpanel.com";
                                    InetAddress apiMixpanelInet = InetAddress.getByName(host);
                                    sIsMixpanelBlocked =
                                            apiMixpanelInet.isLoopbackAddress() || apiMixpanelInet.isAnyLocalAddress();
                                    if (sIsMixpanelBlocked) {
                                        MPLog.v(
                                                LOGTAG, "AdBlocker is enabled. Won't be able to use Mixpanel services.");
                                        onNetworkError(
                                                null,
                                                host,
                                                apiMixpanelInet.getHostAddress(),
                                                startTimeNanos,
                                                -1,
                                                -1,
                                                new IOException(host + " is blocked"));
                                    }
                                } catch (Exception e) {
                                }
                            }
                        });

        t.start();
    }

    @SuppressLint("MissingPermission")
    @SuppressWarnings("MissingPermission")
    @Override
    public boolean isOnline(Context context, OfflineMode offlineMode) {
        if (sIsMixpanelBlocked) return false;
        if (onOfflineMode(offlineMode)) return false;

        boolean isOnline;
        try {
            final ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (netInfo == null) {
                isOnline = true;
                MPLog.v(
                        LOGTAG,
                        "A default network has not been set so we cannot be certain whether we are offline");
            } else {
                isOnline = netInfo.isConnectedOrConnecting();
                MPLog.v(
                        LOGTAG, "ConnectivityManager says we " + (isOnline ? "are" : "are not") + " online");
            }
        } catch (final SecurityException e) {
            isOnline = true;
            MPLog.v(LOGTAG, "Don't have permission to check connectivity, will assume we are online");
        }
        return isOnline;
    }

    private boolean onOfflineMode(OfflineMode offlineMode) {
        boolean onOfflineMode;

        try {
            onOfflineMode = offlineMode != null && offlineMode.isOffline();
        } catch (Exception e) {
            onOfflineMode = false;
            MPLog.v(
                    LOGTAG, "Client State should not throw exception, will assume is not on offline mode", e);
        }

        return onOfflineMode;
    }

    /**
     * Performs an HTTP POST request. Handles either URL-encoded parameters OR a raw byte request
     * body. Includes support for custom headers and network error listening.
     * Each attempt tries primary host first, then backup host on ANY failure.
     * Retries up to 3 times if both primary and backup fail.
     */
    @Override
    public RequestResult performRequest(
            @NonNull String endpointUrl,
            @Nullable ProxyServerInteractor interactor,
            @Nullable Map<String, Object> params, // Use if requestBodyBytes is null
            @Nullable Map<String, String> headers,
            @Nullable byte[] requestBodyBytes, // If provided, send this as raw body
            @Nullable SSLSocketFactory socketFactory)
            throws ServiceUnavailableException, IOException {
        // Delegate to new method with POST as default for backward compatibility
        return performRequest(HttpMethod.POST, endpointUrl, interactor, params, headers, requestBodyBytes, socketFactory);
    }

    @Override
    public RequestResult performRequest(
            @NonNull HttpMethod method,
            @NonNull String endpointUrl,
            @Nullable ProxyServerInteractor interactor,
            @Nullable Map<String, Object> params,
            @Nullable Map<String, String> headers,
            @Nullable byte[] requestBodyBytes,
            @Nullable SSLSocketFactory socketFactory)
            throws ServiceUnavailableException, IOException {
        
        int retries = 0;
        Exception lastException = null;
        
        while (retries < 3) {
            // Try primary host first
            InternalRequestResult primaryResult = tryRequestWithHost(
                    method, endpointUrl, "Primary", interactor, params, headers, requestBodyBytes, socketFactory);
            
            if (primaryResult.success) {
                return RequestResult.success(primaryResult.response, primaryResult.requestUrl);
            }
            
            // If it was a client error, throw immediately without retrying
            if (primaryResult.isClientError) {
                throw (ClientErrorException) primaryResult.exception;
            }
            
            // Primary failed, update last exception
            lastException = primaryResult.exception;
            
            // If primary failed (and it wasn't a client error), try backup host if configured
            if (mBackupHost != null && !mBackupHost.isEmpty()) {
                String backupUrl = replaceHost(endpointUrl, mBackupHost);
                if (backupUrl.equals(endpointUrl)) {
                    // URL replacement failed, skip backup attempt
                    MPLog.w(LOGTAG, "Failed to replace host for backup, skipping backup attempt");
                } else {
                    MPLog.v(LOGTAG, "Primary failed, trying backup: " + backupUrl);
                    
                    InternalRequestResult backupResult = tryRequestWithHost(
                            method, backupUrl, "Backup", interactor, params, headers, requestBodyBytes, socketFactory);
                    
                    if (backupResult.success) {
                        return RequestResult.success(backupResult.response, backupResult.requestUrl);
                    }
                    
                    // If backup had client error, throw immediately
                    if (backupResult.isClientError) {
                        throw (ClientErrorException) backupResult.exception;
                    }
                    
                    // Backup also failed, update last exception
                    lastException = backupResult.exception;
                    MPLog.w(LOGTAG, "Backup also failed: " + backupResult.exception.getMessage());
                }
            }
            
            // Both primary and backup failed, increment retry counter
            retries++;
            if (retries < 3) {
                MPLog.d(LOGTAG, "Attempt " + retries + " failed, retrying...");
                // Add a small delay before retry to avoid hammering the servers
                try {
                    Thread.sleep(100 * retries); // Progressive backoff: 100ms, 200ms, 300ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // All retries exhausted
        MPLog.e(LOGTAG, method + " request to " + endpointUrl + " failed after " + retries + " attempts");
        
        // Throw the appropriate exception type
        if (lastException instanceof ServiceUnavailableException) {
            throw (ServiceUnavailableException) lastException;
        } else if (lastException instanceof IOException) {
            throw (IOException) lastException;
        } else if (lastException != null) {
            throw new IOException(method + " request failed after " + retries + " attempts", lastException);
        } else {
            throw new IOException(method + " request failed after " + retries + " attempts");
        }
    }


    private String replaceHost(String url, String newHost) {
        try {
            URL originalUrl = new URL(url);
            URL newUrl =
                    new URL(originalUrl.getProtocol(), newHost, originalUrl.getPort(), originalUrl.getFile());
            return newUrl.toString();
        } catch (Exception e) {
            MPLog.e(LOGTAG, "Failed to replace host", e);
            return url;
        }
    }

    /**
     * Attempts a single request to the specified URL and returns an InternalRequestResult.
     * This helper method encapsulates the try/catch logic for both primary and backup host attempts.
     * 
     * @param url The URL to send the request to
     * @param hostLabel A label for logging ("primary" or "backup")
     * @param interactor The proxy server interactor (can be null)
     * @param params The request parameters (can be null)
     * @param headers The request headers (can be null)
     * @param requestBodyBytes The raw request body (can be null)
     * @param socketFactory The SSL socket factory (can be null)
     * @return InternalRequestResult containing the response or error information
     */
    private InternalRequestResult tryRequestWithHost(
            @NonNull HttpMethod method,
            @NonNull String url,
            @NonNull String hostLabel,
            @Nullable ProxyServerInteractor interactor,
            @Nullable Map<String, Object> params,
            @Nullable Map<String, String> headers,
            @Nullable byte[] requestBodyBytes,
            @Nullable SSLSocketFactory socketFactory) {
        
        try {
            byte[] response = performSingleRequest(
                    method, url, interactor, params, headers, requestBodyBytes, socketFactory);
            
            // Treat null response as failure
            if (response == null) {
                MPLog.v(LOGTAG, hostLabel + " request returned null response");
                return InternalRequestResult.failure(
                        new IOException(hostLabel + " host returned null response"), 
                        false, url);
            } else {
                // Success! Return the valid response with the URL
                return InternalRequestResult.success(response, url);
            }
        } catch (ClientErrorException e) {
            // Client errors (4xx) should not trigger backup host failover
            MPLog.w(LOGTAG, "Client error from " + hostLabel + " host, not attempting backup: " + e.getMessage());
            return InternalRequestResult.failure(e, true, url);
        } catch (IOException e) {
            MPLog.v(LOGTAG, hostLabel + " request failed: " + e.getMessage());
            return InternalRequestResult.failure(e, false, url);
        } catch (Exception e) {
            MPLog.v(LOGTAG, hostLabel + " request failed with exception: " + e.getMessage());
            return InternalRequestResult.failure(e, false, url);
        }
    }


    private byte[] performSingleRequest(
            @NonNull HttpMethod method,
            @NonNull String endpointUrl,
            @Nullable ProxyServerInteractor interactor,
            @Nullable Map<String, Object> params,
            @Nullable Map<String, String> headers,
            @Nullable byte[] requestBodyBytes,
            @Nullable SSLSocketFactory socketFactory)
            throws ServiceUnavailableException, IOException {

        // Build URL with query parameters for GET requests or when POST has no body
        String fullUrl = endpointUrl;
        if (method == HttpMethod.GET && params != null && !params.isEmpty()) {
            StringBuilder queryBuilder = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) {
                    queryBuilder.append("&");
                }
                first = false;
                
                String key = URLEncoder.encode(entry.getKey(), "UTF-8");
                String value = URLEncoder.encode(entry.getValue().toString(), "UTF-8");
                queryBuilder.append(key).append("=").append(value);
            }
            
            // Append query string to URL
            String queryString = queryBuilder.toString();
            if (!queryString.isEmpty()) {
                fullUrl += (endpointUrl.contains("?") ? "&" : "?") + queryString;
            }
        }

        MPLog.v(
                LOGTAG,
                "Attempting " + method + " request to " + fullUrl
                        + (method == HttpMethod.POST && requestBodyBytes != null ? " (Raw Body)" : 
                           method == HttpMethod.POST && params != null ? " (URL params)" : ""));
        byte[] response = null;
        InputStream in = null;
        OutputStream out = null; // Raw output stream
        HttpURLConnection connection = null;

        // Variables for error listener reporting
        String targetIpAddress = null;
        long startTimeNanos = System.nanoTime();
        long uncompressedBodySize = -1;
        long compressedBodySize = -1; // Only set if gzip applied to params

        try {
                // --- Connection Setup ---
                final URL url = new URL(fullUrl);
                try { // Get IP Address for error reporting, but don't fail request if DNS fails here
                    InetAddress inetAddress = InetAddress.getByName(url.getHost());
                    targetIpAddress = inetAddress.getHostAddress();
                } catch (Exception e) {
                    MPLog.v(LOGTAG, "Could not resolve IP address for " + url.getHost(), e);
                    targetIpAddress = "N/A"; // Default if lookup fails
                }

                connection = (HttpURLConnection) url.openConnection();
                if (null != socketFactory && connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
                }
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod(method.toString());
                connection.setDoOutput(method == HttpMethod.POST && (requestBodyBytes != null || params != null));

                // --- Handle Content-Type for POST requests ---
                String contentType = null;
                if (method == HttpMethod.POST) {
                    // Default Content-Type for POST (can be overridden by headers map)
                    contentType = (requestBodyBytes != null)
                            ? "application/json; charset=utf-8" // Default for raw body
                            : "application/x-www-form-urlencoded; charset=utf-8"; // Default for params
                }

                // --- Apply Custom Headers (and determine final Content-Type) ---
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                        if (entry.getKey().equalsIgnoreCase("Content-Type")) {
                            contentType = entry.getValue(); // Use explicit content type
                        }
                    }
                }
                
                // Only set Content-Type for POST requests
                if (method == HttpMethod.POST && contentType != null) {
                    connection.setRequestProperty("Content-Type", contentType);
                }

                // Apply proxy headers AFTER custom headers
                if (interactor != null && isProxyRequest(endpointUrl)) {
                    /* ... Apply proxy headers ... */
                    Map<String, String> proxyHeaders = interactor.getProxyRequestHeaders();
                    if (proxyHeaders != null) {
                        for (Map.Entry<String, String> entry : proxyHeaders.entrySet()) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                }

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);

                // --- Prepare and Write Body (only for POST requests) ---
                if (method == HttpMethod.POST) {
                    byte[] bytesToWrite;
                    if (requestBodyBytes != null) {
                        // --- Use Raw Body ---
                        bytesToWrite = requestBodyBytes;
                        uncompressedBodySize = bytesToWrite.length;
                        connection.setFixedLengthStreamingMode(uncompressedBodySize);
                        MPLog.v(LOGTAG, "Sending raw body of size: " + uncompressedBodySize);
                    } else if (params != null) {
                        // --- Use URL Encoded Params ---
                        Uri.Builder builder = new Uri.Builder();
                        for (Map.Entry<String, Object> param : params.entrySet()) {
                            builder.appendQueryParameter(param.getKey(), param.getValue().toString());
                        }
                        String query = builder.build().getEncodedQuery();
                        byte[] queryBytes = Objects.requireNonNull(query).getBytes(StandardCharsets.UTF_8);
                        uncompressedBodySize = queryBytes.length;
                        MPLog.v(LOGTAG, "Sending URL params (raw size): " + uncompressedBodySize);

                        if (shouldGzipRequestPayload) {
                            // Apply GZIP specifically to the URL-encoded params
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                                gzipOut.write(queryBytes);
                            } // try-with-resources ensures close
                            bytesToWrite = baos.toByteArray();
                            compressedBodySize = bytesToWrite.length;
                            connection.setRequestProperty(CONTENT_ENCODING_HEADER, GZIP_CONTENT_TYPE_HEADER);
                            connection.setFixedLengthStreamingMode(compressedBodySize);
                            MPLog.v(LOGTAG, "Gzipping params, compressed size: " + compressedBodySize);
                        } else {
                            bytesToWrite = queryBytes;
                            connection.setFixedLengthStreamingMode(uncompressedBodySize);
                        }
                    } else {
                        // No body and no params
                        bytesToWrite = new byte[0];
                        uncompressedBodySize = 0;
                        connection.setFixedLengthStreamingMode(0);
                        MPLog.v(LOGTAG, "Sending POST request with empty body.");
                    }

                    // Write the prepared bytes
                    out = new BufferedOutputStream(connection.getOutputStream());
                    out.write(bytesToWrite);
                    out.flush();
                    out.close(); // Close output stream before getting response
                    out = null;
                }

                // --- Process Response ---
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage(); // Get message for logging/errors
                MPLog.v(LOGTAG, "Response Code: " + responseCode);
                if (interactor != null && isProxyRequest(endpointUrl)) {
                    interactor.onProxyResponse(endpointUrl, responseCode);
                }

                if (responseCode >= 200 && responseCode < 300) { // Success
                    in = connection.getInputStream();
                    response = slurp(in);
                } else if (responseCode >= MIN_UNAVAILABLE_HTTP_RESPONSE_CODE
                        && responseCode <= MAX_UNAVAILABLE_HTTP_RESPONSE_CODE) { // Server Error 5xx
                    MPLog.w(
                            LOGTAG,
                            "Server error " + responseCode + " (" + responseMessage + ") for URL: " + fullUrl);
                    ServiceUnavailableException serviceException = new ServiceUnavailableException(
                            "Service Unavailable: " + responseCode,
                            connection.getHeaderField("Retry-After"));
                    // Report error via listener
                    onNetworkError(
                            connection,
                            fullUrl,
                            targetIpAddress,
                            startTimeNanos,
                            uncompressedBodySize,
                            compressedBodySize,
                            serviceException);
                    // Throw exception to trigger backup host failover
                    throw serviceException;
                } else { // Other errors (4xx etc.)
                    MPLog.w(
                            LOGTAG,
                            "Client error " + responseCode + " (" + responseMessage + ") for URL: " + fullUrl);
                    String errorBody = null;
                    try {
                        in = connection.getErrorStream();
                        if (in != null) {
                            byte[] errorBytes = slurp(in);
                            errorBody = new String(errorBytes, StandardCharsets.UTF_8);
                            MPLog.w(LOGTAG, "Error Body: " + errorBody);
                        }
                    } catch (Exception e) {
                        MPLog.w(LOGTAG, "Could not read error stream.", e);
                    }
                    // Report error via listener
                    onNetworkError(
                            connection,
                            fullUrl,
                            targetIpAddress,
                            startTimeNanos,
                            uncompressedBodySize,
                            compressedBodySize,
                            new IOException(
                                    "HTTP error response: "
                                            + responseCode
                                            + " "
                                            + responseMessage
                                            + (errorBody != null ? " - Body: " + errorBody : "")));
                    // For client errors (4xx), throw exception immediately - backup host won't help
                    throw new ClientErrorException(responseCode, responseMessage);
                }

            } catch (final EOFException e) {
                // Report error
                onNetworkError(
                        connection,
                        fullUrl,
                        targetIpAddress,
                        startTimeNanos,
                        uncompressedBodySize,
                        compressedBodySize,
                        e);
                MPLog.d(LOGTAG, "EOFException, likely network issue for request to " + fullUrl);
                throw new IOException("EOFException during network request", e);
            } catch (final IOException e) { // Includes ServiceUnavailableException if thrown above
                // Report error via listener
                onNetworkError(
                        connection,
                        fullUrl,
                        targetIpAddress,
                        startTimeNanos,
                        uncompressedBodySize,
                        compressedBodySize,
                        e);
                // Re-throw the original exception
                throw e;
            } catch (final Exception e) { // Catch any other unexpected exceptions
                // Report error via listener
                onNetworkError(
                        connection,
                        fullUrl,
                        targetIpAddress,
                        startTimeNanos,
                        uncompressedBodySize,
                        compressedBodySize,
                        e);
                // Wrap and re-throw as IOException? Or handle differently?
                // Let's wrap in IOException for consistency with method signature.
                throw new IOException("Unexpected exception during network request", e);
            } finally {
                // Clean up resources
                if (null != out)
                    try {
                        out.close();
                    } catch (final IOException e) {
                        /* ignore */
                    }
                if (null != in)
                    try {
                        in.close();
                    } catch (final IOException e) {
                        /* ignore */
                    }
                if (null != connection) connection.disconnect();
            }

        return response;
    }

    private void onNetworkError(
            HttpURLConnection connection,
            String endpointUrl,
            String targetIpAddress,
            long startTimeNanos,
            long uncompressedBodySize,
            long compressedBodySize,
            Exception e) {
        if (this.networkErrorListener != null) {
            long endTimeNanos = System.nanoTime();
            long durationNanos = Math.max(0, endTimeNanos - startTimeNanos);
            long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
            int responseCode = -1;
            String responseMessage = "";
            if (connection != null) {
                try {
                    responseCode = connection.getResponseCode();
                    responseMessage = connection.getResponseMessage();
                } catch (Exception respExc) {
                    MPLog.w(LOGTAG, "Could not retrieve response code/message after error", respExc);
                }
            }
            String ip = (targetIpAddress == null) ? "N/A" : targetIpAddress;
            long finalUncompressedSize = Math.max(-1, uncompressedBodySize);
            long finalCompressedSize = Math.max(-1, compressedBodySize);
            try {
                this.networkErrorListener.onNetworkError(
                        endpointUrl,
                        ip,
                        durationMillis,
                        finalUncompressedSize,
                        finalCompressedSize,
                        responseCode,
                        responseMessage,
                        e);
            } catch (Exception listenerException) {
                MPLog.e(LOGTAG, "Network error listener threw an exception", listenerException);
            }
        }
    }

    private OutputStream getBufferedOutputStream(OutputStream out) throws IOException {
        if (shouldGzipRequestPayload) {
            return new GZIPOutputStream(new BufferedOutputStream(out), HTTP_OUTPUT_STREAM_BUFFER_SIZE);
        } else {
            return new BufferedOutputStream(out);
        }
    }

    private static boolean isProxyRequest(String endpointUrl) {
        return !endpointUrl.toLowerCase().contains(MIXPANEL_API.toLowerCase());
    }

    private static byte[] slurp(final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[8192];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    private static final String LOGTAG = "MixpanelAPI.Message";
    private static final int HTTP_OUTPUT_STREAM_BUFFER_SIZE = 8192;
    private static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
    private static final String GZIP_CONTENT_TYPE_HEADER = "gzip";

    /**
     * Internal helper class to encapsulate the result of a request attempt.
     * Used to avoid duplicate try/catch blocks for primary and backup host attempts.
     * This is separate from the public RequestResult in RemoteService interface.
     */
    private static class InternalRequestResult {
        final byte[] response;
        final Exception exception;
        final boolean isClientError;
        final boolean success;
        final String requestUrl;

        // Constructor for successful request
        private InternalRequestResult(byte[] response, String requestUrl) {
            this.response = response;
            this.requestUrl = requestUrl;
            this.exception = null;
            this.isClientError = false;
            this.success = true;
        }

        // Constructor for failed request
        private InternalRequestResult(Exception exception, boolean isClientError, String requestUrl) {
            this.response = null;
            this.requestUrl = requestUrl;
            this.exception = exception;
            this.isClientError = isClientError;
            this.success = false;
        }

        static InternalRequestResult success(byte[] response, String requestUrl) {
            return new InternalRequestResult(response, requestUrl);
        }

        static InternalRequestResult failure(Exception exception, boolean isClientError, String requestUrl) {
            return new InternalRequestResult(exception, isClientError, requestUrl);
        }
    }
}
