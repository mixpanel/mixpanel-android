package com.mixpanel.android.mpmetrics;

import static com.mixpanel.android.mpmetrics.ConfigurationChecker.LOGTAG;

import com.mixpanel.android.util.MPLog;

import org.json.JSONObject;

public class FlagOptions {

    private final boolean mEnabled;
    private final JSONObject mContext;
    private final boolean mLoadOnFirstForeground;

    private FlagOptions(Builder builder) {
        this.mEnabled = builder.mEnabled;
        this.mLoadOnFirstForeground = builder.mLoadOnFirstForeground;
        if (builder.mContext == null) {
            this.mContext = new JSONObject();
        } else {
            JSONObject contextCopy;
            try {
                contextCopy = new JSONObject(builder.mContext.toString());
            } catch (Exception e) {
                MPLog.e(LOGTAG, "Failed to copy context JSONObject", e);
                contextCopy = new JSONObject();
            }
            this.mContext = contextCopy;
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public JSONObject getContext() {
        if (mContext == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(mContext.toString());
        } catch (Exception e) {
            MPLog.e(LOGTAG, "Invalid feature flags context", e);
            return new JSONObject();
        }
    }

    public boolean shouldLoadOnFirstForeground() {
        return mLoadOnFirstForeground;
    }

    public static class Builder {
        private boolean mEnabled = false;
        private JSONObject mContext = new JSONObject();
        private boolean mLoadOnFirstForeground = true;

        public Builder() {
        }

        public Builder setEnabled(boolean enabled) {
            this.mEnabled = enabled;
            return this;
        }

        public Builder setContext(JSONObject context) {
            if (context == null) {
                this.mContext = new JSONObject();
            } else {
                try {
                    this.mContext = new JSONObject(context.toString());
                } catch (Exception e) {
                    this.mContext = new JSONObject();
                }
            }
            return this;
        }

        public Builder setLoadOnFirstForeground(boolean loadOnFirstForeground) {
            this.mLoadOnFirstForeground = loadOnFirstForeground;
            return this;
        }

        public FlagOptions build() {
            return new FlagOptions(this);
        }
    }
}
