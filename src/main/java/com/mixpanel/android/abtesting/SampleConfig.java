package com.mixpanel.android.abtesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by josh on 6/27/14.
 */
public class SampleConfig {
    public static JSONObject get() throws JSONException {
        JSONObject config = new JSONObject();
        JSONArray classes = new JSONArray();

        JSONObject button = new JSONObject();
        button.put("name", "android.widget.Button");
        JSONArray properties = new JSONArray();

        JSONObject property = new JSONObject();


        property.put("name", "text");

        JSONObject getter = new JSONObject();
        getter.put("selector", "getText");
        JSONObject returnVal = new JSONObject();
        returnVal.put("type", "java.lang.CharSequence");
        getter.put("result", returnVal);
        property.put("get", getter);

        JSONObject setter = new JSONObject();
        setter.put("selector", "setText");
        JSONArray parameters = new JSONArray();
        JSONObject parameter = new JSONObject();
        parameter.put("name", "text");
        parameter.put("type", "java.lang.CharSequence");
        parameters.put(parameter);
        setter.put("parameters", parameters);
        property.put("set", setter);

        properties.put(property);

        button.put("properties", properties);
        classes.put(button);
        config.put("classes", classes);

        return config;
    }
}
