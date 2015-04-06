package com.mixpanel.android.util;

import android.content.Context;

import org.apache.http.NameValuePair;

import java.io.IOException;
import java.util.List;

/**
 * Created by joe on 4/2/15.
 */
public interface RemoteService {
    boolean isOnline(Context context);

    byte[] performRequest(String endpointUrl, List<NameValuePair> params) throws ServiceUnavailableException, IOException;

    public static class ServiceUnavailableException extends Exception {
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
