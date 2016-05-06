package com.mixpanel.android.viewcrawler;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.mixpanel.android.R;

import java.util.HashMap;
import java.util.Map;

public class ConnectivityIndicator extends FrameLayout {
    private static final int MINIMIZED_DIAMETER = 100;
    private static ConnectivityIndicator sInstance;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mParams;
    private int mCurrentX = -1;
    private int mCurrentY = -1;
    private static final String LOGTAG = "MixpanelAPI.CIndicator";

    public static ConnectivityIndicator start(Context context) {
        if (sInstance == null) {
            sInstance = new ConnectivityIndicator(context);
        }
        return sInstance;
    }

    public static void stop() {
        sInstance = null;
    }

    private View.OnTouchListener getConnectivityIndicatorTouchListener() {
        return new View.OnTouchListener() {
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragEvent=false;

            public boolean onTouch(View v,MotionEvent event) {
                if ((event == null) || (ConnectivityIndicator.this.mParams == null) || (ConnectivityIndicator.this.mWindowManager == null)) {
                    return false;
                }
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        this.initialTouchX = event.getRawX();
                        this.initialTouchY = event.getRawY();
                        this.isDragEvent = false;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(this.isDragEvent) {
                            ConnectivityIndicator.this.mCurrentX = ConnectivityIndicator.this.mParams.x;
                            ConnectivityIndicator.this.mCurrentY = ConnectivityIndicator.this.mParams.y;
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - this.initialTouchX;
                        float diffY = event.getRawY() - this.initialTouchY;
                        ConnectivityIndicator.this.mParams.x = (ConnectivityIndicator.this.mCurrentX + (int)diffX);
                        ConnectivityIndicator.this.mParams.y = (ConnectivityIndicator.this.mCurrentY + (int)diffY);
                        ConnectivityIndicator.this.mWindowManager.updateViewLayout(ConnectivityIndicator.this, ConnectivityIndicator.this.mParams);
                        int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                        if((diffX > slop) || (diffY > slop)){
                            this.isDragEvent = true;
                        }
                        return true;
                }
                return false;
            }
        };
    }

    private ConnectivityIndicator(Context context) {
        super(context);
        setOnTouchListener(getConnectivityIndicatorTouchListener());
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.com_mixpanel_android_connectivityabtest);
        addView(iconView);
    }

    public static void show(final Activity activity, Context context) {
        if (sInstance == null) {
            ConnectivityIndicator.start(context);
        }
        if (activity == null) {
            return;
        }
        if (sInstance.getParent() != null) {
            return;
        }
        sInstance.mWindowManager = activity.getWindowManager();
        Map<String, Integer> screenSize = getScreenSizeMap(activity);
        if ((sInstance.mCurrentX < 0) || (sInstance.mCurrentX > screenSize.get("width"))) {
            sInstance.mCurrentX = ((screenSize.get("width")) - MINIMIZED_DIAMETER);
        }
        if ((sInstance.mCurrentY < 0) || (sInstance.mCurrentY > (screenSize.get("height")))) {
            sInstance.mCurrentY = MINIMIZED_DIAMETER;
        }
        sInstance.mParams = new WindowManager.LayoutParams(MINIMIZED_DIAMETER, MINIMIZED_DIAMETER, WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);

        IBinder windowToken = activity.getWindow().getDecorView().getRootView().getApplicationWindowToken();
        sInstance.mParams.token = windowToken;
        sInstance.mParams.gravity = Gravity.LEFT | Gravity.TOP;
        sInstance.mParams.x = sInstance.mCurrentX;
        sInstance.mParams.y = sInstance.mCurrentY;
        if (windowToken != null) {
            try {
                sInstance.mWindowManager.addView(ConnectivityIndicator.sInstance, ConnectivityIndicator.sInstance.mParams);
            }
            catch (Exception e) {
                Log.e(LOGTAG, "Error displaying Connectivity Indicator " + e.getMessage());
            }
        }
        else {
            final View viewById = activity.findViewById(android.R.id.content);
            if (viewById != null) {
                viewById.post(new Runnable()
                {
                    public void run()
                    {
                        ConnectivityIndicator.sInstance.mParams.token = activity.getWindow().getDecorView().getWindowToken();
                        try {
                            WindowManager windowManager = activity.getWindowManager();
                            if (windowManager != null) {
                                windowManager.addView(sInstance, sInstance.mParams);
                            }
                        }
                        catch (Exception e) {
                            Log.e(LOGTAG, "Error displaying Connectivity Indicator " + e.getMessage());
                        }
                    }
                });
            }
        }
    }

    public static void hide(Activity activity) {
        if (sInstance == null || activity == null) {
            return;
        }
        if (sInstance.getParent() == null) {
            return;
        }
        sInstance.mWindowManager = activity.getWindowManager();
        try {
            sInstance.mWindowManager.removeViewImmediate(sInstance);
        }
        catch (Exception e) {
            Log.e(LOGTAG, "Error displaying Connectivity Indicator " + e.getMessage());
        }
    }

    public static Map<String, Integer> getScreenSizeMap(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int orientation = context.getResources().getConfiguration().orientation;
        HashMap<String, Integer> screenMap = new HashMap<>();
        switch (orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                screenMap.put("height", metrics.heightPixels);
                screenMap.put("width", metrics.widthPixels);
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                screenMap.put("height", metrics.widthPixels);
                screenMap.put("width", metrics.heightPixels);
                break;
        }
        return screenMap;
    }
}
