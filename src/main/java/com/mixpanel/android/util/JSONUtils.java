package com.mixpanel.android.util;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONUtils {

    /***
     * Workaround for broken JSONObject.
     *
     *     JSONObject wat = new JSONObject("{\"k\":null}");
     *     assert("null".equals(wat.optString("k")));
     *
     * Just let that sink in for a sec. I'll wait.
     *
     * @param o a JSONObject
     * @param k a key
     */
    public static String optionalStringKey(JSONObject o, String k)
            throws JSONException {
        if (o.has(k) && !o.isNull(k)) {
            return o.getString(k);
        }

        return null;
    }
}
