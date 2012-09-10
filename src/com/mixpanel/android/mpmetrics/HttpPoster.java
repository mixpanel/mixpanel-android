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

    // Will return true only if the request was successful
    public boolean postData(String rawMessage, String endpointUrl) {
        boolean sent = false;

        String encodedData = Base64Coder.encodeString(rawMessage);

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair("data", encodedData));

        sent = postHttpRequest(endpointUrl, nameValuePairs);
        return sent;
    }

    // Will return true only if the request was successful.
    public boolean postHttpRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
        boolean ret = false;
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(endpointUrl);

        try {
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String result = StringUtils.inputStreamToString(entity.getContent());
                ret = result.equals("1\n");
            }
        } catch (IOException e) {
            Log.e(LOGTAG, "Cannot post message to Mixpanel Servers", e);
        } catch (OutOfMemoryError e) {
            Log.e(LOGTAG, "Cannot post message to Mixpanel Servers", e);
        }

        return ret;
    }

    private static final String LOGTAG = "MixpanelAPI";
}
