package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.util.Log;
import android.view.View;

/**
 * Created by josh on 11/21/13.
 */

class ShowSurvey implements SurveyCallbacks {

    ShowSurvey(Context context, String token, View view) {
        this.mContext = context;
        this.mToken = token;
        this.mView = view;
    }

    @Override
    public void foundSurvey(Survey s) {
        if (null != s) {
            Log.d(LOGTAG, "found survey " + s.getId() + "!");
            MixpanelAPI.getInstance(mContext, mToken).getPeople().showSurvey(s, mView);
        }
    }

    private Context mContext;
    private String mToken;
    private View mView;
    private static final String LOGTAG = "MixpanelAPI:ShowSurvey";

}
