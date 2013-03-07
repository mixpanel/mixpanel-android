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

/* package */ class HttpPoster {

    public static enum PostResult {
        // The post was sent and understood by the Mixpanel service.
        SUCCEEDED,

        // The post couldn't be sent (for example, because there was no connectivity)
        // but might work later.
        FAILED_RECOVERABLE,

        // The post itself is bad/unsendable (for example, too big for system memory)
        // and shouldn't be retried.
        FAILED_UNRECOVERABLE
    };

    public HttpPoster(String defaultHost, String fallbackHost) {
        mDefaultHost = defaultHost;
        mFallbackHost = fallbackHost;
    }

    // Will return true only if the request was successful
    public PostResult postData(String rawMessage, String endpointPath) {
        String encodedData = Base64Coder.encodeString(rawMessage);

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("data", encodedData));

        String defaultUrl = mDefaultHost + endpointPath;
        PostResult ret = postHttpRequest(defaultUrl, nameValuePairs);
        if (ret == PostResult.FAILED_RECOVERABLE && mFallbackHost != null) {
            String fallbackUrl = mFallbackHost + endpointPath;
            if (MPConfig.DEBUG) Log.i(LOGTAG, "Retrying post with new URL: " + fallbackUrl);
            ret = postHttpRequest(fallbackUrl, nameValuePairs);
        }

        return ret;
    }

    private PostResult postHttpRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
        PostResult ret = PostResult.FAILED_UNRECOVERABLE;
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(endpointUrl);

        try {
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String result = StringUtils.inputStreamToString(entity.getContent());
                if (result.equals("1\n")) {
                    ret = PostResult.SUCCEEDED;
                }
            }
        } catch (IOException e) {
            Log.i(LOGTAG, "Cannot post message to Mixpanel Servers (May Retry)", e);
            ret = PostResult.FAILED_RECOVERABLE;
        } catch (OutOfMemoryError e) {
            Log.e(LOGTAG, "Cannot post message to Mixpanel Servers, will not retry.", e);
            ret = PostResult.FAILED_UNRECOVERABLE;
        }

        return ret;
    }

    private final String mDefaultHost;
    private final String mFallbackHost;

    private static final String LOGTAG = "MixpanelAPI";
}
