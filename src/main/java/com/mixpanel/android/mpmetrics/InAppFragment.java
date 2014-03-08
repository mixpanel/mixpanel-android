package com.mixpanel.android.mpmetrics;

import java.nio.ByteBuffer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.util.ActivityImageUtils;

@SuppressLint("NewApi")
public class InAppFragment extends Fragment implements View.OnClickListener {
    static InAppFragment create(InAppNotification notif) {
        InAppFragment fragment = new InAppFragment();

        Bundle b = new Bundle();
        b.putInt("id", notif.getId());
        b.putInt("message_id", notif.getMessageId());
        b.putString("type", notif.getType().toString());
        b.putString("title", notif.getTitle());
        b.putString("body", notif.getBody());
        b.putString("image_url", notif.getImageUrl());
        b.putString("cta", notif.getCallToAction());
        b.putString("cta_url", notif.getCallToActionUrl());

        Bitmap image = notif.getImage();
        ByteBuffer buf = ByteBuffer.allocate(image.getByteCount());
        image.copyPixelsToBuffer(buf);
        b.putByteArray("image", buf.array());

        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = activity;
        mHandler = new Handler();
        mRemover = new Runnable() {
            public void run() {
                InAppFragment.this.remove();
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();
        View mini = inflater.inflate(R.layout.com_mixpanel_android_activity_notification_mini, container, false);
        TextView titleView = (TextView) mini.findViewById(R.id.com_mixpanel_android_notification_title);
        ImageView notifImageView = (ImageView) mini.findViewById(R.id.com_mixpanel_android_notification_image);

        int highlightColor = ActivityImageUtils.getHighlightColorFromBackground(mParent);
        mini.setBackgroundColor(highlightColor);

        titleView.setText(args.getString("title"));

        byte[] imageBytes = args.getByteArray("image");
        Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        notifImageView.setImageBitmap(image);

        float heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 75, mParent.getResources().getDisplayMetrics());
        ScaleAnimation sa = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, heightPx / 2, heightPx / 2);
        sa.setInterpolator(new SineBounceInterpolator());
        sa.setDuration(500);
        sa.setStartOffset(300);
        notifImageView.startAnimation(sa);

        mini.setOnClickListener(this);

        mHandler.postDelayed(mRemover, MINI_REMOVE_TIME);

        return mini;
    }

    @Override
    public void onStop() {
        super.onStop();

        mHandler.removeCallbacks(mRemover);
    }

    @Override
    public void onClick(View clicked) {
        Bundle b = getArguments();

        String uriString = b.getString("cta_url");
        if (uriString != null && uriString.length() > 0) {
            Uri uri = null;
            try {
                uri = Uri.parse(uriString);
            } catch (IllegalArgumentException e) {
                Log.i(LOGTAG, "Can't parse notification URI, will not take any action", e);
                return;
            }

            assert(uri != null);
            try {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                mParent.startActivity(viewIntent);
            } catch (ActivityNotFoundException e) {
                Log.i(LOGTAG, "User doesn't have an activity for notification URI");
            }
        }

        this.remove();
    }

    private void remove() {
        if (mParent != null) {
            FragmentTransaction ft = mParent.getFragmentManager().beginTransaction();
            ft.setCustomAnimations(R.anim.slide_up, R.anim.slide_down).remove(this).commit();
        }
    }

    private class SineBounceInterpolator implements Interpolator {
        public SineBounceInterpolator() { }
        public float getInterpolation(float t) {
            return (float) -(Math.pow(Math.E, -8*t) * Math.cos(12*t)) + 1;
        }
    }

    private Activity mParent;
    private Handler mHandler;
    private Runnable mRemover;

    private static final String LOGTAG = "InAppFragment";
    private static final int MINI_REMOVE_TIME = 6000;
}
