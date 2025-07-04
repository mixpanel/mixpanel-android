package com.mixpanel.android.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;

public interface RemoteService {
  boolean isOnline(Context context, OfflineMode offlineMode);

  void checkIsMixpanelBlocked();

  /**
   * Performs an HTTP POST request. Handles either URL-encoded parameters OR a raw byte request
   * body.
   *
   * @param endpointUrl The target URL.
   * @param interactor Optional proxy interactor.
   * @param params URL parameters to be URL-encoded and sent (used if requestBodyBytes is null).
   * @param headers Optional map of custom headers (e.g., Authorization, Content-Type).
   * @param requestBodyBytes Optional raw byte array for the request body. If non-null, this is sent
   *     directly, and the 'params' map is ignored for the body content. Ensure Content-Type header
   *     is set.
   * @param socketFactory Optional custom SSLSocketFactory.
   * @return The response body as a byte array, or null if the request failed with a non-retriable
   *     HTTP error code.
   * @throws ServiceUnavailableException If the server returned a 5xx error with a Retry-After
   *     header.
   * @throws IOException For network errors or non-5xx HTTP errors where reading failed.
   */
  byte[] performRequest(
      @NonNull String endpointUrl,
      @Nullable ProxyServerInteractor interactor,
      @Nullable Map<String, Object> params, // Used only if requestBodyBytes is null
      @Nullable Map<String, String> headers,
      @Nullable byte[] requestBodyBytes, // If provided, send this as raw body
      @Nullable SSLSocketFactory socketFactory)
      throws ServiceUnavailableException, IOException;

  class ServiceUnavailableException extends Exception {
    public ServiceUnavailableException(String message, String strRetryAfter) {
      super(message);
      int retry;
      try {
        retry = Integer.parseInt(strRetryAfter);
      } catch (NumberFormatException e) {
        retry = 0;
      }
      mRetryAfter = retry;
    }

    public int getRetryAfter() {
      return mRetryAfter;
    }

    private final int mRetryAfter;
  }
}
