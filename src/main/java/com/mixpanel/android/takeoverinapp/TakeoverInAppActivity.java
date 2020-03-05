package com.mixpanel.android.takeoverinapp;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.InAppButton;
import com.mixpanel.android.mpmetrics.InAppNotification;
import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.TakeoverInAppNotification;
import com.mixpanel.android.mpmetrics.UpdateDisplayState;
import com.mixpanel.android.util.MPLog;
import com.mixpanel.android.util.ViewUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Activity used internally by Mixpanel to display inApp takeover notifications.
 *
 * You should not send Intent's directly to display this activity. Instead use
 * {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showNotificationIfAvailable(Activity)}
 */
@TargetApi(MPConfig.UI_FEATURES_MIN_API)
@SuppressLint("ClickableViewAccessibility")
public class TakeoverInAppActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIntentId = getIntent().getIntExtra(INTENT_ID_KEY, Integer.MAX_VALUE);
        mUpdateDisplayState = UpdateDisplayState.claimDisplayState(mIntentId);
        if (null == mUpdateDisplayState) {
            MPLog.e(LOGTAG, "TakeoverInAppActivity intent received, but nothing was found to show.");
            finish();
            return;
        }
        mMixpanel = MixpanelAPI.getInstance(TakeoverInAppActivity.this, mUpdateDisplayState.getToken());

        onCreateInAppNotification();
    }

    private void onCreateInAppNotification() {
        setContentView(R.layout.com_mixpanel_android_activity_notification_full);

        final ImageView backgroundImage = (ImageView) findViewById(R.id.com_mixpanel_android_notification_gradient);
        final FadingImageView inAppImageView = (FadingImageView) findViewById(R.id.com_mixpanel_android_notification_image);
        final TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
        final TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
        ArrayList<Button> ctaButtons = new ArrayList<>();
        final Button ctaButton = (Button) findViewById(R.id.com_mixpanel_android_notification_button);
        ctaButtons.add(ctaButton);
        final Button secondCtaButton = (Button) findViewById(R.id.com_mixpanel_android_notification_second_button);
        ctaButtons.add(secondCtaButton);
        final LinearLayout closeButtonWrapper = (LinearLayout) findViewById(R.id.com_mixpanel_android_button_exit_wrapper);
        final ImageView closeButtonImageView = (ImageView) findViewById(R.id.com_mixpanel_android_image_close);

        final UpdateDisplayState.DisplayState.InAppNotificationState notificationState =
                (UpdateDisplayState.DisplayState.InAppNotificationState) mUpdateDisplayState.getDisplayState();
        final TakeoverInAppNotification inApp = (TakeoverInAppNotification) notificationState.getInAppNotification();

        final Display display = getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) closeButtonWrapper.getLayoutParams();
            params.setMargins(0, 0, 0, (int) (size.y * 0.06f)); // make bottom margin 6% of screen height
            closeButtonWrapper.setLayoutParams(params);
        }

        inAppImageView.showShadow(inApp.setShouldShowShadow());

        backgroundImage.setBackgroundColor(inApp.getBackgroundColor());

        if (inApp.hasTitle()) {
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(inApp.getTitle());
            titleView.setTextColor(inApp.getTitleColor());
        } else {
            titleView.setVisibility(View.GONE);
        }

        if (inApp.hasBody()) {
            subtextView.setVisibility(View.VISIBLE);
            subtextView.setText(inApp.getBody());
            subtextView.setTextColor(inApp.getBodyColor());
        } else {
            subtextView.setVisibility(View.GONE);
        }

        inAppImageView.setImageBitmap(inApp.getImage());

        for (int i = 0; i < ctaButtons.size(); i++) {
            InAppButton inAppButtonModel = inApp.getButton(i);
            Button inAppButton = ctaButtons.get(i);

            setUpInAppButton(inAppButton, inAppButtonModel, inApp, i);
        }

        if (inApp.getNumButtons() == 1) {
            LinearLayout.LayoutParams oneButtonLayoutParams = (LinearLayout.LayoutParams) ctaButton.getLayoutParams();
            oneButtonLayoutParams.weight = 0;
            oneButtonLayoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            ctaButton.setLayoutParams(oneButtonLayoutParams);
        }

        closeButtonImageView.setColorFilter(inApp.getCloseColor());
        closeButtonWrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                UpdateDisplayState.releaseDisplayState(mIntentId);
            }
        });

        setUpNotificationAnimations(inAppImageView, titleView, subtextView, ctaButtons, closeButtonWrapper);
    }

    @SuppressWarnings("deprecation")
    private void setUpInAppButton(Button inAppButton, final InAppButton inAppButtonModel, final InAppNotification inApp, final int buttonIndex) {
        if (inAppButtonModel != null) {
            inAppButton.setVisibility(View.VISIBLE);
            inAppButton.setText(inAppButtonModel.getText());
            inAppButton.setTextColor(inAppButtonModel.getTextColor());
            inAppButton.setTransformationMethod(null);

            final GradientDrawable buttonBackground = new GradientDrawable();
            final int highlightColor = inAppButtonModel.getBackgroundColor() != 0 ? ViewUtils.mixColors(inAppButtonModel.getBackgroundColor(), 0x33868686) : 0x33868686;
            inAppButton.setOnTouchListener(new View.OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        int highLight = highlightColor;
                        buttonBackground.setColor(highLight);
                    } else {
                        buttonBackground.setColor(inAppButtonModel.getBackgroundColor());
                    }
                    return false;
                }
            });
            buttonBackground.setColor(inAppButtonModel.getBackgroundColor());
            buttonBackground.setStroke((int) ViewUtils.dpToPx(2, this), inAppButtonModel.getBorderColor());
            buttonBackground.setCornerRadius((int) ViewUtils.dpToPx(5, this));

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                inAppButton.setBackgroundDrawable(buttonBackground);
            } else {
                inAppButton.setBackground(buttonBackground);
            }

            inAppButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    JSONObject trackingProperties = null;
                    final String uriString = inAppButtonModel.getCtaUrl();
                    if (uriString != null && uriString.length() > 0) {
                        Uri uri;
                        try {
                            uri = Uri.parse(uriString);
                        } catch (final IllegalArgumentException e) {
                            MPLog.i(LOGTAG, "Can't parse notification URI, will not take any action", e);
                            return;
                        }

                        try {
                            final Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                            TakeoverInAppActivity.this.startActivity(viewIntent);
                        } catch (final ActivityNotFoundException e) {
                            MPLog.i(LOGTAG, "User doesn't have an activity for notification URI");
                        }

                        try {
                            trackingProperties = new JSONObject();
                            trackingProperties.put("url", uriString);
                        } catch (final JSONException e) {
                            MPLog.e(LOGTAG, "Can't put url into json properties");
                        }
                    }
                    String whichButton = "primary";
                    final TakeoverInAppNotification takeoverInApp = (TakeoverInAppNotification)inApp;
                    if (takeoverInApp.getNumButtons() == 2) {
                        whichButton = (buttonIndex == 0) ? "secondary" : "primary";
                    }
                    try {
                        if (trackingProperties == null) {
                            trackingProperties = new JSONObject();
                        }
                        trackingProperties.put("button", whichButton);
                    } catch (final JSONException e) {
                        MPLog.e(LOGTAG, "Can't put button type into json properties");
                    }
                    mMixpanel.getPeople().trackNotification("$campaign_open", inApp, trackingProperties);
                    finish();
                    UpdateDisplayState.releaseDisplayState(mIntentId);
                }
            });
        } else {
            inAppButton.setVisibility(View.GONE);
        }
    }

    private void setUpNotificationAnimations(ImageView notificationImage, TextView notificationTitle, TextView notificationBody, ArrayList<Button> ctaButtons, LinearLayout closeButtonWrapper) {
        final ScaleAnimation scale = new ScaleAnimation(
                .95f, 1.0f, .95f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 1.0f);
        scale.setDuration(200);
        notificationImage.startAnimation(scale);

        final TranslateAnimation translate = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.0f
        );
        translate.setInterpolator(new DecelerateInterpolator());
        translate.setDuration(200);
        notificationTitle.startAnimation(translate);
        notificationBody.startAnimation(translate);
        for (Button ctaButton : ctaButtons) {
            ctaButton.startAnimation(translate);
        }

        final Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.com_mixpanel_android_fade_in);
        closeButtonWrapper.startAnimation(fadeIn);
    }

    @Override
    public void onBackPressed() {
        UpdateDisplayState.releaseDisplayState(mIntentId);
        super.onBackPressed();
    }

    private MixpanelAPI mMixpanel;

    private UpdateDisplayState mUpdateDisplayState;
    private int mIntentId = -1;

    private static final String LOGTAG = "MixpanelAPI.TakeoverInAppActivity";

    public static final String INTENT_ID_KEY = "com.mixpanel.android.takeoverinapp.TakeoverInAppActivity.INTENT_ID_KEY";
}

