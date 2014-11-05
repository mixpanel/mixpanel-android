package com.mixpanel.android.viewcrawler;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/* package */ class EditState {

    public EditState() {
        mUiThreadHandler = new Handler(Looper.getMainLooper());
        mIntendedEdits = new HashMap<String, List<ViewVisitor>>();
        mCurrentEdits = new HashSet<EditBinding>();
    }

    // Must be thread-safe
    public void applyAllChangesOnUiThread(final Set<Activity> liveActivities) {
        if (Thread.currentThread() == mUiThreadHandler.getLooper().getThread()) {
            applyIntendedEdits(liveActivities);
        } else {
            mUiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    applyIntendedEdits(liveActivities);
                }
            });
        }
    }

    // Must be thread-safe
    public void setEdits(Map<String, List<ViewVisitor>> newEdits) {
        synchronized (mCurrentEdits) {
            for (EditBinding stale: mCurrentEdits) {
                stale.kill();
            }
            mCurrentEdits.clear();
        }

        synchronized(mIntendedEdits) {
            mIntendedEdits.clear();
            mIntendedEdits.putAll(newEdits);
        }
    }

    // Must be called on UI Thread
    private void applyIntendedEdits(final Set<Activity> liveActivities) {
        synchronized (liveActivities) {
            for (Activity activity : liveActivities) {
                final String activityName = activity.getClass().getCanonicalName();
                final View rootView = activity.getWindow().getDecorView().getRootView();

                final List<ViewVisitor> specificChanges;
                final List<ViewVisitor> wildcardChanges;
                synchronized (mIntendedEdits) {
                    specificChanges = mIntendedEdits.get(activityName);
                    wildcardChanges = mIntendedEdits.get(null);
                }

                if (null != specificChanges) {
                    applyChangesFromList(rootView, specificChanges);
                }

                if (null != wildcardChanges) {
                    applyChangesFromList(rootView, wildcardChanges);
                }
            }
        }
    }

    // Must be called on UI Thread
    private void applyChangesFromList(View rootView, List<ViewVisitor> changes) {
        synchronized (mCurrentEdits) {
            int size = changes.size();
            for (int i = 0; i < size; i++) {
                final ViewVisitor visitor = changes.get(i);
                final EditBinding binding = new EditBinding(rootView, visitor, mUiThreadHandler);
                mCurrentEdits.add(binding);
            }
        }
    }

    /* The binding between a bunch of edits and a view. Should be instantiated and live on the UI thread */
    private static class EditBinding implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        public EditBinding(View viewRoot, ViewVisitor edit, Handler uiThreadHandler) {
            mEdit = edit;
            mViewRoot = new WeakReference<View>(viewRoot);
            mHandler = uiThreadHandler;
            mAlive = true;

            final ViewTreeObserver observer = viewRoot.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnGlobalLayoutListener(this);
            }
            run();
        }

        @Override
        public void onGlobalLayout() {
            run();
        }

        @Override
        public void run() {
            if (!mAlive) {
                return;
            }

            final View viewRoot = mViewRoot.get();
            if (null == viewRoot) {
                kill();
                return;
            }
            // ELSE View is alive and we are alive

            mEdit.visit(viewRoot);
            mHandler.removeCallbacks(this);
            mHandler.postDelayed(this, 1000);
        }

        public void kill() {
            mAlive = false;
            final View viewRoot = mViewRoot.get();
            if (null != viewRoot) {
                final ViewTreeObserver observer = viewRoot.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeGlobalOnLayoutListener(this);
                }
            }
        }

        private volatile boolean mAlive;
        private final WeakReference<View> mViewRoot;
        private final ViewVisitor mEdit;
        private final Handler mHandler;
    }

    private final Handler mUiThreadHandler;
    private final Map<String, List<ViewVisitor>> mIntendedEdits;
    private final Set<EditBinding> mCurrentEdits;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.EditState";
}
