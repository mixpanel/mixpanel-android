package com.mixpanel.android.util;

import java.util.Map;

public interface MixpanelServerCallback {
    Map<String, String> getHeaders();

    void onResponse(String apiPath, int responseCode);
}
