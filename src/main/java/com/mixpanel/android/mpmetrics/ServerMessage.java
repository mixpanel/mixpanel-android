package com.mixpanel.android.mpmetrics;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.mixpanel.android.util.Base64Coder;

/* package */ class ServerMessage {

    public static enum Status {
        // The post was sent and understood by the Mixpanel service.
        SUCCEEDED,

        // The post couldn't be sent (for example, because there was no connectivity)
        // but might work later.
        FAILED_RECOVERABLE,

        // The post itself is bad/unsendable (for example, too big for system memory)
        // and shouldn't be retried.
        FAILED_UNRECOVERABLE
    };

    public static class Result {
        /* package */ Result(Status status, byte[] responseBytes) {
            mStatus = status;
            mResponseBytes = responseBytes;
        }

        public Status getStatus() {
            return mStatus;
        }

        public byte[] getResponseBytes() {
            return mResponseBytes;
        }

        public String getResponse() {
            if (null == mResponseBytes) {
                return null;
            }
            try {
                return new String(mResponseBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("UTF not supported on this platform?", e);
            }
        }

        private final byte[] mResponseBytes;
        private final Status mStatus;
    }

    public boolean isOnline(Context context) {
        boolean isOnline;
        try {
            final ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo netInfo = cm.getActiveNetworkInfo();
            isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
            if (MPConfig.DEBUG) Log.d(LOGTAG, "ConnectivityManager says we " + (isOnline ? "are" : "are not") + " online");
        } catch (final SecurityException e) {
            isOnline = true;
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Don't have permission to check connectivity, assuming online");
        }
        return isOnline;
    }

    public Result postData(Context context, String rawMessage, String endpointUrl, String fallbackUrl) {
        if (! isOnline(context)) {
            return OFFLINE_RESULT;
        }

        Status status = Status.FAILED_UNRECOVERABLE;
        final String encodedData = Base64Coder.encodeString(rawMessage);

        final List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("data", encodedData));
        if (MPConfig.DEBUG) {
            nameValuePairs.add(new BasicNameValuePair("verbose", "1"));
        }

        Result result = performRequest(endpointUrl, nameValuePairs);
        final Status baseStatus = result.getStatus();
        if (baseStatus == Status.SUCCEEDED) {
            final String baseResponse = result.getResponse();
            // Could still be a failure if the application successfully
            // returned an error message...
            if (MPConfig.DEBUG) {
                try {
                    final JSONObject verboseResponse = new JSONObject(baseResponse);
                    if (verboseResponse.optInt("status") == 1) {
                        status = Status.SUCCEEDED;
                    }
                } catch (final JSONException e) {
                    status = Status.FAILED_UNRECOVERABLE;
                }
            }
            else if (baseResponse.equals("1\n")) {
                status = Status.SUCCEEDED;
            }
        }

        if (baseStatus == Status.FAILED_RECOVERABLE && fallbackUrl != null) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Retrying post with new URL: " + fallbackUrl);
            result = postData(context, rawMessage, fallbackUrl, null);
            final Status retryStatus = result.getStatus();
            if (retryStatus != Status.SUCCEEDED) {
                Log.e(LOGTAG, "Could not post data to Mixpanel");
            } else {
                status = Status.SUCCEEDED;
            }
        }

        return new Result(status, result.getResponseBytes());
    }

    public Result get(Context context, String endpointUrl, String fallbackUrl) {
        if (! isOnline(context)) {
            return OFFLINE_RESULT;
        }

        Result ret = performRequest(endpointUrl, null);
        if (ret.getStatus() == Status.FAILED_RECOVERABLE && fallbackUrl != null) {
            ret = get(context, fallbackUrl, null);
        }
        return ret;
    }

    /**
     * Considers *any* response a SUCCESS, callers should check Result.getResponse() for errors
     * and craziness.
     *
     * Will POST if nameValuePairs is not null.
     *
     * Package access for testing only.
     */
    /* package */ Result performRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
        Status status = Status.FAILED_UNRECOVERABLE;
        byte[] response = null;
        try {
            // the while(retries) loop is a workaround for a bug in some Android HttpURLConnection
            // libraries- The underlying library will attempt to reuse stale connections,
            // meaning the second (or every other) attempt to connect fails with an EOFException.
            // Apparently this nasty retry logic is the current state of the workaround art.
            int retries = 0;
            boolean succeeded = false;
            while (retries < 3 && !succeeded) {
                InputStream in = null;
                OutputStream out = null;
                BufferedOutputStream bout = null;
                HttpURLConnection connection = null;

                try {
                    final URL url = new URL(endpointUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(10000);
                    if (null != nameValuePairs) {
                        connection.setDoOutput(true);
                        final UrlEncodedFormEntity form = new UrlEncodedFormEntity(nameValuePairs, "UTF-8");
                        connection.setRequestMethod("POST");
                        connection.setFixedLengthStreamingMode((int)form.getContentLength());
                        out = connection.getOutputStream();
                        bout = new BufferedOutputStream(out);
                        form.writeTo(bout);
                        bout.close();
                        bout = null;
                        out.close();
                        out = null;
                    }
                    in = connection.getInputStream();
                    response = slurp(in);
                    in.close();
                    in = null;
                    succeeded = true;
                } catch (final EOFException e) {
                    if (MPConfig.DEBUG) Log.d(LOGTAG, "Failure to connect, likely caused by a known issue with Android lib. Retrying.");
                    retries = retries + 1;
                } finally {
                    if (null != bout)
                        try { bout.close(); } catch (final IOException e) { ; }
                    if (null != out)
                        try { out.close(); } catch (final IOException e) { ; }
                    if (null != in)
                        try { in.close(); } catch (final IOException e) { ; }
                    if (null != connection)
                        connection.disconnect();
                }
            }// while
        } catch (final MalformedURLException e) {
            Log.e(LOGTAG, "Cannot interpret " + endpointUrl + " as a URL", e);
            status = Status.FAILED_UNRECOVERABLE;
        } catch (final IOException e) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Cannot post message to Mixpanel Servers (ok, can retry.)", e);
            status = Status.FAILED_RECOVERABLE;
        } catch (final OutOfMemoryError e) {
            Log.e(LOGTAG, "Cannot post message to Mixpanel Servers, will not retry.", e);
            status = Status.FAILED_UNRECOVERABLE;
        }

        if (null != response) {
            status = Status.SUCCEEDED;
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Request returned:\n" + response);
        }

        return new Result(status, response);
    }

    // Does not close input streamq
    private byte[] slurp(final InputStream inputStream)
        throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[8192];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    private static final String LOGTAG = "MixpanelAPI";
    private static final Result OFFLINE_RESULT = new Result(Status.FAILED_RECOVERABLE, null);
}
