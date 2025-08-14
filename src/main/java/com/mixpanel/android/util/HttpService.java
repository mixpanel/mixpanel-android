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
  private String backupHost;

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
    this.backupHost = backupHost;
  }

  public HttpService(
      boolean shouldGzipRequestPayload, MixpanelNetworkErrorListener networkErrorListener) {
    this(shouldGzipRequestPayload, networkErrorListener, null);
  }

  public HttpService() {
    this(false, null, null);
  }

  public void setBackupHost(String backupHost) {
    this.backupHost = backupHost;
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
   */
  @Override
  public byte[] performRequest(
      @NonNull String endpointUrl,
      @Nullable ProxyServerInteractor interactor,
      @Nullable Map<String, Object> params, // Use if requestBodyBytes is null
      @Nullable Map<String, String> headers,
      @Nullable byte[] requestBodyBytes, // If provided, send this as raw body
      @Nullable SSLSocketFactory socketFactory)
      throws ServiceUnavailableException, IOException {
    // Try primary URL first
    byte[] response =
        performRequestInternal(
            endpointUrl, interactor, params, headers, requestBodyBytes, socketFactory);

    // On failure, try backup if configured
    if (response == null && backupHost != null && !backupHost.isEmpty()) {
      String backupUrl = replaceHost(endpointUrl, backupHost);
      MPLog.v(LOGTAG, "Primary request failed, trying backup: " + backupUrl);
      response =
          performRequestInternal(
              backupUrl, interactor, params, headers, requestBodyBytes, socketFactory);
    }

    return response;
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

  private byte[] performRequestInternal(
      @NonNull String endpointUrl,
      @Nullable ProxyServerInteractor interactor,
      @Nullable Map<String, Object> params,
      @Nullable Map<String, String> headers,
      @Nullable byte[] requestBodyBytes,
      @Nullable SSLSocketFactory socketFactory)
      throws ServiceUnavailableException, IOException {

    MPLog.v(
        LOGTAG,
        "Attempting request to "
            + endpointUrl
            + (requestBodyBytes == null ? " (URL params)" : " (Raw Body)"));
    byte[] response = null;
    int retries = 0;
    boolean succeeded = false;

    while (retries < 3 && !succeeded) {
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
        final URL url = new URL(endpointUrl);
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
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // --- Default Content-Type (can be overridden by headers map) ---
        String contentType =
            (requestBodyBytes != null)
                ? "application/json; charset=utf-8" // Default for raw body
                : "application/x-www-form-urlencoded; charset=utf-8"; // Default for params

        // --- Apply Custom Headers (and determine final Content-Type) ---
        if (headers != null) {
          for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
            if (entry.getKey().equalsIgnoreCase("Content-Type")) {
              contentType = entry.getValue(); // Use explicit content type
            }
          }
        }
        connection.setRequestProperty("Content-Type", contentType);

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

        // --- Prepare and Write Body ---
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
          succeeded = true;
        } else if (responseCode >= MIN_UNAVAILABLE_HTTP_RESPONSE_CODE
            && responseCode <= MAX_UNAVAILABLE_HTTP_RESPONSE_CODE) { // Server Error 5xx
          // Report error via listener before throwing
          onNetworkError(
              connection,
              endpointUrl,
              targetIpAddress,
              startTimeNanos,
              uncompressedBodySize,
              compressedBodySize,
              new ServiceUnavailableException(
                  "Service Unavailable: " + responseCode,
                  connection.getHeaderField("Retry-After")));
          // Now throw the exception
          throw new ServiceUnavailableException(
              "Service Unavailable: " + responseCode, connection.getHeaderField("Retry-After"));
        } else { // Other errors (4xx etc.)
          MPLog.w(
              LOGTAG,
              "HTTP error " + responseCode + " (" + responseMessage + ") for URL: " + endpointUrl);
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
              endpointUrl,
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
          response = null; // Indicate failure with null response
          succeeded = true; // Mark as succeeded to stop retry loop for definitive HTTP errors
        }

      } catch (final EOFException e) {
        // Report error BEFORE retry attempt
        onNetworkError(
            connection,
            endpointUrl,
            targetIpAddress,
            startTimeNanos,
            uncompressedBodySize,
            compressedBodySize,
            e);
        MPLog.d(LOGTAG, "EOFException, likely network issue. Retrying request to " + endpointUrl);
        retries++;
      } catch (final IOException e) { // Includes ServiceUnavailableException if thrown above
        // Report error via listener
        onNetworkError(
            connection,
            endpointUrl,
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
            endpointUrl,
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
    } // End while loop

    if (!succeeded) {
      MPLog.e(
          LOGTAG,
          "Could not complete request to " + endpointUrl + " after " + retries + " retries.");
      // Optionally report final failure via listener here if desired, though individual errors were
      // already reported
      throw new IOException("Request failed after multiple retries."); // Indicate final failure
    }

    return response; // Can be null if a non-retriable HTTP error occurred
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
}
