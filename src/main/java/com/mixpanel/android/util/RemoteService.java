package com.mixpanel.android.util;

import android.content.Context;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;


public interface RemoteService {
    enum HttpMethod {
        GET, POST
    }

    boolean isOnline(Context context, OfflineMode offlineMode);

    void checkIsServerBlocked();

    /**
     * Performs an HTTP POST request. Handles either URL-encoded parameters OR a raw byte request body.
     *
     * @param endpointUrl      The target URL.
     * @param interactor       Optional proxy interactor.
     * @param params           URL parameters to be URL-encoded and sent (used if requestBodyBytes is null).
     * @param headers          Optional map of custom headers (e.g., Authorization, Content-Type).
     * @param requestBodyBytes Optional raw byte array for the request body. If non-null, this is sent directly,
     * and the 'params' map is ignored for the body content. Ensure Content-Type header is set.
     * @param socketFactory    Optional custom SSLSocketFactory.
     * @return A RequestResult containing the response body, actual URL used, and success status.
     * @throws ServiceUnavailableException If the server returned a 5xx error with a Retry-After header.
     * @throws IOException                For network errors or non-5xx HTTP errors where reading failed.
     */
    RequestResult performRequest(
            @NonNull String endpointUrl,
            @Nullable ProxyServerInteractor interactor,
            @Nullable Map<String, Object> params, // Used only if requestBodyBytes is null
            @Nullable Map<String, String> headers,
            @Nullable byte[] requestBodyBytes, // If provided, send this as raw body
            @Nullable SSLSocketFactory socketFactory)
            throws ServiceUnavailableException, IOException;

    /**
     * Performs an HTTP request with the specified method. For GET requests, parameters go in query string.
     * For POST requests, parameters can go in body (URL-encoded or JSON) or query string based on requestBodyBytes.
     *
     * @param method           The HTTP method to use (GET or POST).
     * @param endpointUrl      The target URL.
     * @param interactor       Optional proxy interactor.
     * @param params           Parameters (query string for GET, body for POST if requestBodyBytes is null).
     * @param headers          Optional map of custom headers (e.g., Authorization, Content-Type).
     * @param requestBodyBytes Optional raw byte array for POST request body. If non-null, params are ignored for body.
     * @param socketFactory    Optional custom SSLSocketFactory.
     * @return A RequestResult containing the response body, actual URL used, and success status.
     * @throws ServiceUnavailableException If the server returned a 5xx error with a Retry-After header.
     * @throws IOException                For network errors or non-5xx HTTP errors where reading failed.
     */
    RequestResult performRequest(
            @NonNull HttpMethod method,
            @NonNull String endpointUrl,
            @Nullable ProxyServerInteractor interactor,
            @Nullable Map<String, Object> params,
            @Nullable Map<String, String> headers,
            @Nullable byte[] requestBodyBytes,
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
    
    /**
     * Exception thrown when the server returns a 4xx client error.
     * These errors should not trigger backup host failover since they
     * indicate issues with the request itself, not server availability.
     */
    class ClientErrorException extends IOException {
        private final int responseCode;
        
        public ClientErrorException(int responseCode, String message) {
            super("Client error " + responseCode + ": " + message);
            this.responseCode = responseCode;
        }
        
        public int getResponseCode() {
            return responseCode;
        }
    }
    
    /**
     * Encapsulates the result of a request attempt.
     * Used to track which URL succeeded and provide response data.
     */
    class RequestResult {
        private final byte[] response;
        private final Exception exception;
        private final boolean isClientError;
        private final boolean success;
        private final String requestUrl;

        // Constructor for successful request
        public RequestResult(byte[] response, String requestUrl) {
            this.response = response;
            this.requestUrl = requestUrl;
            this.exception = null;
            this.isClientError = false;
            this.success = true;
        }

        // Constructor for failed request
        public RequestResult(Exception exception, boolean isClientError, String requestUrl) {
            this.response = null;
            this.requestUrl = requestUrl;
            this.exception = exception;
            this.isClientError = isClientError;
            this.success = false;
        }

        public static RequestResult success(byte[] response, String requestUrl) {
            return new RequestResult(response, requestUrl);
        }

        public static RequestResult failure(Exception exception, boolean isClientError, String requestUrl) {
            return new RequestResult(exception, isClientError, requestUrl);
        }

        public byte[] getResponse() {
            return response;
        }

        public Exception getException() {
            return exception;
        }

        public boolean isClientError() {
            return isClientError;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getRequestUrl() {
            return requestUrl;
        }
    }
}
