package com.mixpanel.android.viewcrawler;

import android.app.Activity;
import android.content.Context;
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

public class ConnectivityIndicator
        extends FrameLayout
{
    private static final int MINIMIZED_DIAMETER = 100;
    private static ConnectivityIndicator sInstance;
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private int currentX = -1;
    private int currentY = -1;
    private boolean isShown;
    private static final String LOGTAG = "MixpanelAPI.CIndicator";

    public static ConnectivityIndicator start(Context context)
    {
        if (sInstance == null) {
            sInstance = new ConnectivityIndicator(context);
        }
        return sInstance;
    }

    public static void stop()
    {
        sInstance = null;
    }

    private View.OnTouchListener getConnectivityIndicatorTouchListener() {
        return new View.OnTouchListener()
        {
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragEvent=false;

            public boolean onTouch(View v,MotionEvent event)
            {
                if((event == null)||(ConnectivityIndicator.this.params == null)||(ConnectivityIndicator.this.windowManager == null)){
                    return false;
                }
                System.out.println(event.getAction() + " , " + event.getRawX() + " , " + event.getRawY());
                switch(event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        this.initialTouchX = event.getRawX();
                        this.initialTouchY = event.getRawY();
                        this.isDragEvent = false;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(this.isDragEvent)
                        {
                            ConnectivityIndicator.this.currentX = ConnectivityIndicator.this.params.x;
                            ConnectivityIndicator.this.currentY = ConnectivityIndicator.this.params.y;
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - this.initialTouchX;
                        float diffY = event.getRawY() - this.initialTouchY;
                        ConnectivityIndicator.this.params.x = (ConnectivityIndicator.this.currentX + (int)diffX);
                        ConnectivityIndicator.this.params.y = (ConnectivityIndicator.this.currentY + (int)diffY);
                        ConnectivityIndicator.this.windowManager.updateViewLayout(ConnectivityIndicator.this, ConnectivityIndicator.this.params);
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

    private ConnectivityIndicator(Context context)
    {
        super(context);

        setOnTouchListener(getConnectivityIndicatorTouchListener());
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.com_mixpanel_android_connectivityabtest);
        addView(iconView);
    }

    public static void show(final Activity activity)
    {
        if ((sInstance == null) || (!ViewCrawler.mMixpanelInEditMode) || activity == null) {
            return;
        }
        if (sInstance.isShown) {
            return;
        }
        sInstance.isShown = true;
        sInstance.windowManager = activity.getWindowManager();
        Map<String, Integer> screenSize = getScreenSizeMap(activity);
        if ((sInstance.currentX < 0) || (sInstance.currentX > screenSize.get("width"))) {
            sInstance.currentX = ((screenSize.get("width")) - MINIMIZED_DIAMETER);
        }
        if ((sInstance.currentY < 0) || (sInstance.currentY > (screenSize.get("height")))) {
            sInstance.currentY = MINIMIZED_DIAMETER;
        }
        sInstance.params = new WindowManager.LayoutParams(MINIMIZED_DIAMETER, MINIMIZED_DIAMETER, WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);

        IBinder windowToken = activity.getWindow().getDecorView().getRootView().getApplicationWindowToken();
        sInstance.params.token = windowToken;
        sInstance.params.gravity = Gravity.LEFT | Gravity.TOP;
        sInstance.params.x = sInstance.currentX;
        sInstance.params.y = sInstance.currentY;
        if (windowToken != null)
        {
            try
            {
                sInstance.windowManager.addView(sInstance, sInstance.params);
            }
            catch (Exception e)
            {
                Log.e(LOGTAG, "Error displaying Connectivity Indicator " + e.getMessage());
            }
        }
        else
        {
            final View viewById = activity.findViewById(android.R.id.content);
            if (viewById != null) {
                viewById.post(new Runnable()
                {
                    public void run()
                    {
                        ConnectivityIndicator.sInstance.params.token = activity.getWindow().getDecorView().getWindowToken();
                        try
                        {
                            WindowManager windowManager = activity.getWindowManager();
                            if (windowManager != null) {
                                windowManager.addView(ConnectivityIndicator.sInstance, ConnectivityIndicator.sInstance.params);
                            }
                        }
                        catch (Exception e)
                        {
                            Log.e(LOGTAG, "Error displaying Connectivity Indicator " + e.getMessage());
                        }
                    }
                });
            }
        }
    }

    public static void hide(Activity activity)
    {
        if (sInstance == null || activity == null) {
            return;
        }
        if (!sInstance.isShown) {
            return;
        }
        sInstance.isShown = false;
        if (!ViewCrawler.mMixpanelInEditMode) {
            return;
        }
        sInstance.windowManager = activity.getWindowManager();
        try
        {
            sInstance.windowManager.removeViewImmediate(sInstance);
        }
        catch (Exception e)
        {
            Log.e(LOGTAG, "Error displaying Connectivity Indicator " + e.getMessage());
        }
    }

    public static Map<String, Integer> getScreenSizeMap(Context context)
    {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int orientation = context.getResources().getConfiguration().orientation;
        HashMap<String, Integer> screenMap = new HashMap<>();
        switch (orientation)
        {
            case 1:
                screenMap.put("height", metrics.heightPixels);
                screenMap.put("width", metrics.widthPixels);
                break;
            case 2:
                screenMap.put("height", metrics.widthPixels);
                screenMap.put("width", metrics.heightPixels);
        }
        return screenMap;
    }
}
