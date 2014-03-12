package com.mixpanel.android.mpmetrics;

import java.nio.ByteBuffer;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.util.ActivityImageUtils;

@SuppressLint("NewApi")
public class InAppFragment extends Fragment implements View.OnClickListener {
    static InAppFragment create(InAppNotification notif) {
        final InAppFragment fragment = new InAppFragment();

        final Bundle bundle = new Bundle();
        bundle.putInt("id", notif.getId());
        bundle.putInt("message_id", notif.getMessageId());
        bundle.putString("type", notif.getType().toString());
        bundle.putString("title", notif.getTitle());
        bundle.putString("body", notif.getBody());
        bundle.putString("image_url", notif.getImageUrl());
        bundle.putString("cta", notif.getCallToAction());
        bundle.putString("cta_url", notif.getCallToActionUrl());

        final Bitmap image = notif.getImage();
        final ByteBuffer buf = ByteBuffer.allocate(image.getByteCount());
        image.copyPixelsToBuffer(buf);
        bundle.putByteArray("image", buf.array());

        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // We have to hold these references because the Activity does not clear it's Handler
        // of messages when it disappears, so we have to manually clear the mRemover in onStop
        mParent = activity;
        mHandler = new Handler();
        mRemover = new Runnable() {
            public void run() {
                InAppFragment.this.remove();
            }
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mType = getArguments().getString("type");
        mKill = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        if (mType == InAppNotification.Type.TAKEOVER.toString()) {
            mInAppView = this.createTakeover(inflater, container);
        } else {
            mInAppView = this.createMini(inflater, container);
            mInAppView.setOnClickListener(this);
        }

        return mInAppView;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Begin animations when fragment becomes visible
        if (mType == InAppNotification.Type.TAKEOVER.toString()) {
            final ImageView notifImage = (ImageView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_image);
            final TextView titleView = (TextView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_title);
            final TextView subtextView = (TextView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_subtext);
            final Button ctaButton = (Button) mInAppView.findViewById(R.id.com_mixpanel_android_notification_button);
            final ImageButton closeButton = (ImageButton) mInAppView.findViewById(R.id.com_mixpanel_android_button_exit);

            final ScaleAnimation scale = new ScaleAnimation(
                .95f, 1.0f, .95f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 1.0f);
            scale.setDuration(200);
            notifImage.startAnimation(scale);

            final TranslateAnimation translate = new TranslateAnimation(
                 Animation.RELATIVE_TO_SELF, 0.0f,
                 Animation.RELATIVE_TO_SELF, 0.0f,
                 Animation.RELATIVE_TO_SELF, 0.5f,
                 Animation.RELATIVE_TO_SELF, 0.0f
            );
            translate.setInterpolator(new DecelerateInterpolator());
            translate.setDuration(200);
            titleView.startAnimation(translate);
            subtextView.startAnimation(translate);
            ctaButton.startAnimation(translate);

            final AnimatorSet fadeIn = (AnimatorSet) AnimatorInflater.loadAnimator(mParent, R.anim.fade_in);
            fadeIn.setTarget(closeButton);
            fadeIn.start();
        } else if (mType == InAppNotification.Type.MINI.toString()) {
            // getHighlightColorFromBackground doesn't seem to work on onResume because the view
            // has not been fully rendered, so try and delay a little bit
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mInAppView.setVisibility(View.VISIBLE);
                    final int highlightColor = ActivityImageUtils.getHighlightColorFromBackground(mParent);
                    mInAppView.setBackgroundColor(highlightColor);

                    final ImageView notifImage = (ImageView) mInAppView.findViewById(R.id.com_mixpanel_android_notification_image);

                    final float heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 75, mParent.getResources().getDisplayMetrics());
                    final TranslateAnimation translate = new TranslateAnimation(0, 0, heightPx, 0);
                    translate.setInterpolator(new DecelerateInterpolator());
                    translate.setDuration(200);
                    mInAppView.startAnimation(translate);

                    final ScaleAnimation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, heightPx / 2, heightPx / 2);
                    scale.setInterpolator(new SineBounceInterpolator());
                    scale.setDuration(400);
                    scale.setStartOffset(200);
                    notifImage.startAnimation(scale);
                }
            }, 500);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (mKill) {
            mParent.getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        mHandler.removeCallbacks(mRemover);

        // This Fragment when registered on the Activity is part of its state, and so gets
        // restored / recreated when the Activity goes away and comes back, so we have to
        // remember kill it. If the Activity object fully dies, then it is not remembered,
        // so onSaveInstanceState is not necessary.
        mKill = true;
    }

    @Override
    public void onClick(View clicked) {
        final Bundle bundle = getArguments();

        final String uriString = bundle.getString("cta_url");
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

        remove();
    }

    private View createMini(LayoutInflater inflater, ViewGroup container) {
        final Bundle args = getArguments();
        final View mini = inflater.inflate(R.layout.com_mixpanel_android_activity_notification_mini, container, false);
        final TextView titleView = (TextView) mini.findViewById(R.id.com_mixpanel_android_notification_title);
        final ImageView notifImage = (ImageView) mini.findViewById(R.id.com_mixpanel_android_notification_image);

        titleView.setText(args.getString("title"));

        final byte[] imageBytes = args.getByteArray("image");
        final Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        notifImage.setImageBitmap(image);

        mHandler.postDelayed(mRemover, MINI_REMOVE_TIME);

        return mini;
    }

    private View createTakeover(LayoutInflater inflater, ViewGroup container) {
        final Bundle args = getArguments();
        final View takeover = inflater.inflate(R.layout.com_mixpanel_android_activity_notification_full, container, false);
        final ImageView notifImage = (ImageView) takeover.findViewById(R.id.com_mixpanel_android_notification_image);
        final TextView titleView = (TextView) takeover.findViewById(R.id.com_mixpanel_android_notification_title);
        final TextView subtextView = (TextView) takeover.findViewById(R.id.com_mixpanel_android_notification_subtext);
        final Button ctaButton = (Button) takeover.findViewById(R.id.com_mixpanel_android_notification_button);
        final ImageButton closeButton = (ImageButton) takeover.findViewById(R.id.com_mixpanel_android_button_exit);

        titleView.setText(args.getString("title"));
        subtextView.setText(args.getString("body"));

        final byte[] imageBytes = args.getByteArray("image");
        final Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        notifImage.setImageBitmap(image);

        final String ctaUrl = args.getString("cta_url");
        if (ctaUrl != null && ctaUrl.length() > 0) {
            ctaButton.setText(args.getString("cta"));
        }
        ctaButton.setOnClickListener(this);
        ctaButton.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setBackgroundResource(R.drawable.com_mixpanel_android_cta_button_highlight);
                } else {
                    v.setBackgroundResource(R.drawable.com_mixpanel_android_cta_button);
                }
                return false;
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                remove();
            }
        });

        return takeover;
    }

    private void remove() {
        if (mParent != null) {
            final FragmentManager fragmentManager = mParent.getFragmentManager();

            // setCustomAnimations works on a per transaction level, so the animations set
            // when this fragment was created do not apply
            if (mType == InAppNotification.Type.TAKEOVER.toString()) {
                fragmentManager.popBackStack();
            } else {
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.setCustomAnimations(0, R.anim.slide_down).remove(this).commit();
            }
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
    private String mType;
    private View mInAppView;

    private boolean mKill;

    private static final String LOGTAG = "InAppFragment";
    private static final int MINI_REMOVE_TIME = 6000;
}
