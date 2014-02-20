package com.mixpanel.android.mpmetrics;

import java.util.List;

/* package */ interface DecideCallbacks {
    public void foundResults(List<Survey> surveys, List<InAppNotification> notifications);
}
