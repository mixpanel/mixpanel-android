package com.mixpanel.android.util;

import com.mixpanel.android.mpmetrics.MPConfig;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Build mixpanel endpoints with configuration or endpoint overide
 */
public class MPUrlBuilder {

    private final String mEventEndpoint;
    private final String mPeopleEndpoint;
    private final String mDecideEndpoint;
    private final String mEditorUrl;

    public MPUrlBuilder(MPConfig config) {
        this(config, null);
    }

    public MPUrlBuilder(MPConfig config, String enpointOveride) {
        if(enpointOveride == null){
            mEventEndpoint = config.getEventsEndpoint();
            mPeopleEndpoint = config.getPeopleEndpoint();
            mDecideEndpoint =  config.getDecideEndpoint();
        }
        else{
            if(!enpointOveride.endsWith("/")){
                enpointOveride = enpointOveride + "/";
            }
            mEventEndpoint = enpointOveride + "track?ip=" + (config.getUseIpAddressForGeolocation() ? "1" : "0");
            mPeopleEndpoint = enpointOveride + "engage";
            mDecideEndpoint = enpointOveride + "decide";
        }

        mEditorUrl = config.getEditorUrl();
    }

    
    private String BuildUrl(String base, String relativ) throws MalformedURLException {
        return new URL(new URL(base), relativ).toString();
    }

    public String getDecideEndpoint() {
        return mDecideEndpoint;
    }

    public String getEditorUrl() {
        return mEditorUrl;
    }

    public String getPeopleEndpoint() {
        return mPeopleEndpoint;
    }

    public String getEventEndpoint() {
        return mEventEndpoint;
    }
}
