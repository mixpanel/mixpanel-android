package com.mixpanel.android.mpmetrics;

import android.os.Parcel;
import android.os.Parcelable;

import com.mixpanel.android.util.MPLog;

import org.json.JSONException;
import org.json.JSONObject;

/* package */ class DisplayTrigger implements Parcelable {
    private static final String LOGTAG = "MixpanelAPI.DisplayTrigger";
    private static final String ANY_EVENT = "$any_event";
    private static final String EVENT_KEY = "event";
    private static final String SELECTOR_KEY = "selector";

    private final String mEventName;
    private final JSONObject mJSONSelector;
    private final SelectorEvaluator mEvaluator;

    public DisplayTrigger(JSONObject displayTrigger) throws BadDecideObjectException {
        SelectorEvaluator evaluator = null;
        try{
            mEventName = displayTrigger.getString(EVENT_KEY);
            mJSONSelector = displayTrigger.optJSONObject(SELECTOR_KEY);
            if (mJSONSelector != null) {
                evaluator = new SelectorEvaluator(mJSONSelector);
            }
        } catch (JSONException e) {
            throw new BadDecideObjectException("Event triggered notification JSON was unexpected or bad", e);
        }
        mEvaluator = evaluator;
    }

    public DisplayTrigger(Parcel in) {
        mEventName = in.readString();
        JSONObject tempSelector = null;
        SelectorEvaluator evaluator = null;
        try {
            tempSelector = new JSONObject(in.readString());
        } catch (JSONException e) {
            MPLog.e(LOGTAG, "Error parsing selector from display_trigger", e);
        }
        mJSONSelector = tempSelector;
        if (mJSONSelector != null) {
            evaluator = new SelectorEvaluator(mJSONSelector);
        }
        mEvaluator = evaluator;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mEventName);
        dest.writeString(mJSONSelector.toString());
    }

    public boolean matchesEventDescription(AnalyticsMessages.EventDescription eventDescription) {
        if (null != eventDescription) {
            if (mEventName.equals(ANY_EVENT) ||
                    eventDescription.getEventName().equals(mEventName)) {
                if (mEvaluator != null) {
                    try {
                        return mEvaluator.evaluate(eventDescription.getProperties());
                    } catch (Exception e) {
                        MPLog.e(LOGTAG, "Error evaluating selector", e);
                        return false;
                    }
                } else {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<DisplayTrigger> CREATOR = new Parcelable.Creator<DisplayTrigger>() {

        @Override
        public DisplayTrigger createFromParcel(Parcel source) {
            return new DisplayTrigger(source);
        }

        @Override
        public DisplayTrigger[] newArray(int size) {
            return new DisplayTrigger[size];
        }
    };
}
