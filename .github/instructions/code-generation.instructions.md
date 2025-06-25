# Code Generation Instructions - Mixpanel Android SDK

When generating new code for this SDK, follow these patterns:

## Class Structure
```java
package com.mixpanel.android.mpmetrics;

import java.util.Map;
import org.json.JSONObject;

// Package-private class (not public)
class NewFeatureManager {
    private static final String LOGTAG = "MixpanelAPI.NewFeature";
    
    // Immutable fields
    private final Context mContext;
    private final MPConfig mConfig;
    
    // Mutable state with synchronization
    private final Object mStateLock = new Object();
    private Map<String, Object> mState;
    
    NewFeatureManager(Context context, MPConfig config) {
        mContext = context.getApplicationContext();
        mConfig = config;
    }
}
```

## Public API Methods
```java
public void newFeature(String param, JSONObject properties) {
    // 1. Check opt-out first
    if (hasOptedOut()) {
        return;
    }
    
    // 2. Validate inputs (no exceptions)
    if (param == null || param.length() == 0) {
        MPLog.e(LOGTAG, "Invalid parameter");
        return;
    }
    
    // 3. Try-catch everything
    try {
        // 4. Queue to background thread
        Message msg = Message.obtain();
        msg.what = NEW_FEATURE_MESSAGE;
        msg.obj = new FeatureDescription(param, properties, mToken);
        mMessages.enqueueMessage(msg);
        
    } catch (Exception e) {
        MPLog.e(LOGTAG, "Failed to process feature", e);
    }
}
```

## Database Operations
```java
// Always use try-finally for cleanup
Cursor cursor = null;
try {
    cursor = db.query(table, columns, selection, args, null, null, orderBy);
    while (cursor.moveToNext()) {
        // Process row
    }
} catch (SQLException e) {
    MPLog.e(LOGTAG, "Query failed", e);
    return null;
} finally {
    if (cursor != null) {
        cursor.close();
    }
}
```

## Background Processing
```java
private void processInBackground(final FeatureDescription desc) {
    // Use existing worker thread
    mWorker.runMessage(Message.obtain(null, PROCESS_FEATURE, desc));
}

// In handler
case PROCESS_FEATURE:
    FeatureDescription desc = (FeatureDescription) msg.obj;
    synchronized (mDbAdapter) {
        mDbAdapter.addFeature(desc);
    }
    break;
```

## Configuration
```java
// Check runtime -> manifest -> default
if (mOptions != null && mOptions.featureEnabled != null) {
    return mOptions.featureEnabled;
}
int manifestValue = mMetaData.getInt("com.mixpanel.android.MPConfig.FeatureEnabled", -1);
if (manifestValue != -1) {
    return manifestValue == 1;
}
return true; // default
```

## Common Patterns to Follow
- Static nested classes for data models
- Final fields wherever possible
- Defensive copies of mutable inputs
- Lazy initialization of expensive objects
- Separate lock objects for different state
- Method overloading for convenience
- Descriptive MPLog messages