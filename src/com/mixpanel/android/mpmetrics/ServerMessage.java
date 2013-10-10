package com.mixpanel.android.mpmetrics;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;

import android.util.Log;

import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.StringUtils;

/* package */ class ServerMessage {

    // TODO keep HttpClient around? Check lifetime of ServerMessage

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
        /* package */ Result(Status status, String response) {
            mStatus = status;
            mResponse = response;
        }

        public Status getStatus() {
            return mStatus;
        }

        public String getResponse() {
            return mResponse;
        }

        private final String mResponse;
        private final Status mStatus;
    }

    public Result postData(String rawMessage, String endpointUrl, String fallbackUrl) {
        String encodedData = Base64Coder.encodeString(rawMessage);

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("data", encodedData));
        if (MPConfig.DEBUG) {
            nameValuePairs.add(new BasicNameValuePair("verbose", "1"));
        }

        Result ret = postHttpRequest(endpointUrl, nameValuePairs);
        Status status = ret.getStatus();
        if (status == Status.FAILED_RECOVERABLE && fallbackUrl != null) {
            if (MPConfig.DEBUG) Log.i(LOGTAG, "Retrying post with new URL: " + fallbackUrl);
            ret = postHttpRequest(fallbackUrl, nameValuePairs);
            status = ret.getStatus();
            if (status != Status.SUCCEEDED) {
                Log.e(LOGTAG, "Could not post data to Mixpanel");
            }
        }

        return ret;
    }

    private Result postHttpRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
        Status status = Status.FAILED_UNRECOVERABLE;
        String response = null;
        InputStream in = null;
        OutputStream out = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(endpointUrl);
            connection = (HttpURLConnection) url.openConnection();
            UrlEncodedFormEntity form = new UrlEncodedFormEntity(nameValuePairs, "UTF-8");
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setFixedLengthStreamingMode((int)form.getContentLength());
            out = new BufferedOutputStream(connection.getOutputStream());
            form.writeTo(out);
            out.close();
            out = null;
            in = new BufferedInputStream(connection.getInputStream());
            response = StringUtils.inputStreamToString(in);
            in.close();
            in = null;
        } catch (MalformedURLException e) {
            Log.e(LOGTAG, "Cannot iterpret " + endpointUrl + " as a URL", e);
            status = Status.FAILED_UNRECOVERABLE;
        } catch (IOException e) {
            if (MPConfig.DEBUG) {
                Log.i(LOGTAG, "Cannot post message to Mixpanel Servers (May Retry)", e);
            }
            status = Status.FAILED_RECOVERABLE;
        } catch (OutOfMemoryError e) {
            Log.e(LOGTAG, "Cannot post message to Mixpanel Servers, will not retry.", e);
            status = Status.FAILED_UNRECOVERABLE;
        } finally {
            if (null != in) {
                try { in.close(); } catch (IOException e) { ; }
            }
            if (null != out) {
                try { out.close(); } catch (IOException e) { ; }
            }
            if (null != connection) {
                connection.disconnect();
            }
        }

        if (null != response) {
            if (MPConfig.DEBUG) {
                Log.d(LOGTAG, "Request returned:\n" + response);
                // This Regex is only acceptable because we're in debug mode.
                if (response.matches(".*\\b\"status\"\\s*:\\s*1\\b.*")) {
                    status = Status.SUCCEEDED;
                }
            }
            if (response.equals("1\n")) {
                status = Status.SUCCEEDED;
            }
        }

        return new Result(status, response);
    }

    private static final String LOGTAG = "MixpanelAPI";
}
