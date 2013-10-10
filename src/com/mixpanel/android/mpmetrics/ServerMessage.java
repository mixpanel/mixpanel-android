package com.mixpanel.android.mpmetrics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
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

        Result ret = postHttpRequest(endpointUrl, nameValuePairs);
        Status status = ret.getStatus();
        if (status == Status.FAILED_RECOVERABLE && fallbackUrl != null) { // TODO MUST ALLOW NO FALLBACK (with test)
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
        HttpClient httpclient = new DefaultHttpClient(); // TODO use AndroidHttpClient
        HttpPost httppost = new HttpPost(endpointUrl);

        try {
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            HttpResponse httpResponse = httpclient.execute(httppost);
            HttpEntity entity = httpResponse.getEntity();

            if (entity != null) {
                response = StringUtils.inputStreamToString(entity.getContent());
                if (response.equals("1\n")) {
                    status = Status.SUCCEEDED;
                }
            }
        } catch (IOException e) {
            if (MPConfig.DEBUG) {
                Log.i(LOGTAG, "Cannot post message to Mixpanel Servers (May Retry)", e);
            }
            status = Status.FAILED_RECOVERABLE;
        } catch (OutOfMemoryError e) {
            Log.e(LOGTAG, "Cannot post message to Mixpanel Servers, will not retry.", e);
            status = Status.FAILED_UNRECOVERABLE;
        }

        return new Result(status, response);
    }

    private static final String LOGTAG = "MixpanelAPI";
}
