package com.mixpanel.android.surveys;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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
import com.mixpanel.android.mpmetrics.InAppNotification;
import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.Survey;
import com.mixpanel.android.mpmetrics.Survey.Question;
import com.mixpanel.android.mpmetrics.UpdateDisplayState;
import com.mixpanel.android.util.MPLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Activity used internally by Mixpanel to display surveys and inapp takeover notifications.
 *
 * You should not send Intent's directly to display this activity. Instead use
 * {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showSurveyIfAvailable(Activity)} and
 * {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showNotificationIfAvailable(Activity)}
 *
 * @deprecated Surveys are deprecated in 4.9.3 and will be removed from the SDK soon. Use this
 * activity carefully since some of its implementation will disappear.
 */
@TargetApi(MPConfig.UI_FEATURES_MIN_API)
@SuppressLint("ClickableViewAccessibility")
@Deprecated
public class SurveyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIntentId = getIntent().getIntExtra(INTENT_ID_KEY, Integer.MAX_VALUE);
        mUpdateDisplayState = UpdateDisplayState.claimDisplayState(mIntentId);
        if (null == mUpdateDisplayState) {
            MPLog.e(LOGTAG, "SurveyActivity intent received, but nothing was found to show.");
            finish();
            return;
        }
        mMixpanel = MixpanelAPI.getInstance(SurveyActivity.this, mUpdateDisplayState.getToken());

        if (isShowingInApp()) {
            onCreateInAppNotification(savedInstanceState);
        } else if (isShowingSurvey()) {
            onCreateSurvey(savedInstanceState);
        } else {
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    private void onCreateInAppNotification(Bundle savedInstanceState) {
        setContentView(R.layout.com_mixpanel_android_activity_notification_full);

        final ImageView backgroundImage = (ImageView) findViewById(R.id.com_mixpanel_android_notification_gradient);
        final FadingImageView inAppImageView = (FadingImageView) findViewById(R.id.com_mixpanel_android_notification_image);
        final TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
        final TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
        final Button ctaButton = (Button) findViewById(R.id.com_mixpanel_android_notification_button);
        final LinearLayout closeButtonWrapper = (LinearLayout) findViewById(R.id.com_mixpanel_android_button_exit_wrapper);

        final UpdateDisplayState.DisplayState.InAppNotificationState notificationState =
                (UpdateDisplayState.DisplayState.InAppNotificationState) mUpdateDisplayState.getDisplayState();
        final InAppNotification inApp = notificationState.getInAppNotification();

        // Layout
        final Display display = getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) closeButtonWrapper.getLayoutParams();
            params.setMargins(0, 0, 0, (int) (size.y * 0.06f)); // make bottom margin 6% of screen height
            closeButtonWrapper.setLayoutParams(params);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            backgroundImage.setBackgroundColor(getResources().getColor(R.color.com_mixpanel_android_inapp_dark_translucent));
        } else {
            backgroundImage.setBackgroundColor(getResources().getColor(R.color.com_mixpanel_android_inapp_dark_translucent, null));
        }
        titleView.setText(inApp.getTitle());
        subtextView.setText(inApp.getBody());
        inAppImageView.setImageBitmap(inApp.getImage());

        final String ctaUrl = inApp.getCallToActionUrl();
        if (ctaUrl != null && ctaUrl.length() > 0) {
            ctaButton.setText(inApp.getCallToAction());
        }

        // Listeners
        ctaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String uriString = inApp.getCallToActionUrl();
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
                        SurveyActivity.this.startActivity(viewIntent);
                        mMixpanel.getPeople().trackNotification("$campaign_open", inApp);
                    } catch (final ActivityNotFoundException e) {
                        MPLog.i(LOGTAG, "User doesn't have an activity for notification URI");
                    }
                }
                finish();
                UpdateDisplayState.releaseDisplayState(mIntentId);
            }
        });
        ctaButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.setBackgroundResource(R.drawable.com_mixpanel_android_cta_button_highlight);
                } else {
                    v.setBackgroundResource(R.drawable.com_mixpanel_android_cta_button);
                }
                return false;
            }
        });
        closeButtonWrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                UpdateDisplayState.releaseDisplayState(mIntentId);
            }
        });

        // Animations
        final ScaleAnimation scale = new ScaleAnimation(
                .95f, 1.0f, .95f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 1.0f);
        scale.setDuration(200);
        inAppImageView.startAnimation(scale);

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

        final Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.com_mixpanel_android_fade_in);
        closeButtonWrapper.startAnimation(fadeIn);

        if (InAppNotification.Style.LIGHT.equalsName(inApp.getStyle())) {
            showLightStyle();
        }
    }

    private void onCreateSurvey(Bundle savedInstanceState) {
        requestOrientationLock();

        if (null != savedInstanceState) {
            mCurrentQuestion = savedInstanceState.getInt(CURRENT_QUESTION_BUNDLE_KEY, 0);
            mSurveyBegun = savedInstanceState.getBoolean(SURVEY_BEGUN_BUNDLE_KEY);
        }

        final String answerDistinctId = mUpdateDisplayState.getDistinctId();
        if (null == answerDistinctId) {
            MPLog.i(LOGTAG, "Can't show a survey to a user with no distinct id set");
            finish();
            return;
        }

        setContentView(R.layout.com_mixpanel_android_activity_survey);

        final UpdateDisplayState.DisplayState.SurveyState surveyState = getSurveyState();
        final Bitmap background = surveyState.getBackground();
        if (null == background) {
            final View contentView = this.findViewById(R.id.com_mixpanel_android_activity_survey_id);
            contentView.setBackgroundColor(GRAY_30PERCENT);
        } else {
            getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), background));
        }
        mPreviousButton = findViewById(R.id.com_mixpanel_android_button_previous);
        mNextButton = findViewById(R.id.com_mixpanel_android_button_next);
        mProgressTextView = (TextView) findViewById(R.id.com_mixpanel_android_progress_text);
        mCardHolder = (CardCarouselLayout) findViewById(R.id.com_mixpanel_android_question_card_holder);
        mCardHolder.setOnQuestionAnsweredListener(new CardCarouselLayout.OnQuestionAnsweredListener() {
            @Override
            public void onQuestionAnswered(Question question, String answer) {
                saveAnswer(question, answer);
                goToNextQuestion();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        final UpdateDisplayState.DisplayState displayState = mUpdateDisplayState.getDisplayState();
        if (null != displayState && displayState.getType() == UpdateDisplayState.DisplayState.SurveyState.TYPE) {
            onStartSurvey();
        }
    }

    private void onStartSurvey() {
        if (mSurveyBegun) {
            return;
        }
        if (!MPConfig.getInstance(this).getTestMode()) {
            trackSurveyAttempted();
        }

        final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setTitle(R.string.com_mixpanel_android_survey_prompt_dialog_title);
        alertBuilder.setMessage(R.string.com_mixpanel_android_survey_prompt_dialog_message);
        alertBuilder.setPositiveButton(R.string.com_mixpanel_android_sure, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SurveyActivity.this.findViewById(R.id.com_mixpanel_android_activity_survey_id).setVisibility(View.VISIBLE);
                mSurveyBegun = true;
                showQuestion(mCurrentQuestion);
            }
        });
        alertBuilder.setNegativeButton(R.string.com_mixpanel_android_no_thanks, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SurveyActivity.this.finish();
            }
        });
        alertBuilder.setCancelable(false);
        mDialog = alertBuilder.create();
        mDialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (isShowingSurvey()) {
            onDestroySurvey();
        }

        super.onDestroy();
    }

    @SuppressLint("SimpleDateFormat")
    private void onDestroySurvey() {
        if (null != mMixpanel) {
            if (mUpdateDisplayState != null && mUpdateDisplayState.getDistinctId() != null) {
                final UpdateDisplayState.DisplayState.SurveyState surveyState = getSurveyState();
                final Survey survey = surveyState.getSurvey();
                final List<Survey.Question> questionList = survey.getQuestions();
                int answerCount = 0;

                final String answerDistinctId = mUpdateDisplayState.getDistinctId();
                final MixpanelAPI.People people = mMixpanel.getPeople().withIdentity(answerDistinctId);
                people.append("$responses", survey.getCollectionId());

                final UpdateDisplayState.AnswerMap answers = surveyState.getAnswers();
                for (final Survey.Question question : questionList) {
                    final String answerString = answers.get(question.getId());
                    if (null != answerString) {
                        try {
                            final JSONObject answerJson = new JSONObject();
                            answerJson.put("$survey_id", survey.getId());
                            answerJson.put("$collection_id", survey.getCollectionId());
                            answerJson.put("$question_id", question.getId());
                            answerJson.put("$question_type", question.getType().toString());

                            final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                            answerJson.put("$time", dateFormat.format(new Date()));
                            answerJson.put("$value", answerString);

                            people.append("$answers", answerJson);

                            answerCount = answerCount + 1;
                        } catch (final JSONException e) {
                            MPLog.e(LOGTAG, "Couldn't record user's answer.", e);
                        }
                    } // if answer is present
                } // For each question
                try {
                    final JSONObject surveyJson = new JSONObject();
                    surveyJson.put("survey_id", survey.getId());
                    surveyJson.put("collection_id", survey.getCollectionId());
                    surveyJson.put("$answer_count", answerCount);
                    surveyJson.put("$survey_shown", mSurveyBegun);
                    mMixpanel.track("$show_survey", surveyJson);
                } catch (final JSONException e) {
                    MPLog.e(LOGTAG, "Couldn't record survey shown.", e);
                } // track the survey as received
            } // if we have a survey state
            mMixpanel.flush();
        } // if we initialized property and we have a mixpanel

        UpdateDisplayState.releaseDisplayState(mIntentId);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (isShowingSurvey()) {
            onSaveInstanceStateSurvey(outState);
        }
    }

    private void onSaveInstanceStateSurvey(Bundle outState) {
        outState.putBoolean(SURVEY_BEGUN_BUNDLE_KEY, mSurveyBegun);
        outState.putInt(CURRENT_QUESTION_BUNDLE_KEY, mCurrentQuestion);
        outState.putParcelable(SURVEY_STATE_BUNDLE_KEY, mUpdateDisplayState);
    }

    @Override
    public void onBackPressed() {
        if (isShowingSurvey() && mCurrentQuestion > 0) {
            goToPreviousQuestion();
        } else {
            if (isShowingInApp()) {
                UpdateDisplayState.releaseDisplayState(mIntentId);
            }
            super.onBackPressed();
        }
    }

    public void goToPreviousQuestion(View v) {
        goToPreviousQuestion();
    }

    public void goToNextQuestion(View v) {
        goToNextQuestion();
    }

    public void completeSurvey(View v) {
        completeSurvey();
    }

    private UpdateDisplayState.DisplayState.SurveyState getSurveyState() {
        // Throws if this is showing an InApp
        return (UpdateDisplayState.DisplayState.SurveyState) mUpdateDisplayState.getDisplayState();
    }

    private boolean isShowingSurvey() {
        if (null == mUpdateDisplayState) {
            return false;
        }
        return UpdateDisplayState.DisplayState.SurveyState.TYPE.equals(
                mUpdateDisplayState.getDisplayState().getType()
        );
    }

    private boolean isShowingInApp() {
        if (null == mUpdateDisplayState) {
            return false;
        }
        return UpdateDisplayState.DisplayState.InAppNotificationState.TYPE.equals(
                mUpdateDisplayState.getDisplayState().getType()
        );
    }

    private void trackSurveyAttempted() {
        final UpdateDisplayState.DisplayState.SurveyState surveyState = getSurveyState();
        final Survey survey = surveyState.getSurvey();
        final MixpanelAPI.People people = mMixpanel.getPeople().withIdentity(mUpdateDisplayState.getDistinctId());
        people.append("$surveys", survey.getId());
        people.append("$collections", survey.getCollectionId());
    }

    private void goToPreviousQuestion() {
        if (mCurrentQuestion > 0) {
            showQuestion(mCurrentQuestion - 1);
        } else {
            completeSurvey();
        }
    }

    private void goToNextQuestion() {
        final UpdateDisplayState.DisplayState.SurveyState surveyState = getSurveyState();
        final int surveySize = surveyState.getSurvey().getQuestions().size();
        if (mCurrentQuestion < surveySize - 1) {
            showQuestion(mCurrentQuestion + 1);
        } else {
            completeSurvey();
        }
    }

    private void showQuestion(final int idx) {
        final UpdateDisplayState.DisplayState.SurveyState surveyState = getSurveyState();
        final List<Question> questions = surveyState.getSurvey().getQuestions();
        if (0 == idx || questions.size() == 0) {
            mPreviousButton.setVisibility(View.GONE);
        } else {
            mPreviousButton.setVisibility(View.VISIBLE);
        }
        if (idx >= questions.size() - 1) {
            mNextButton.setVisibility(View.GONE);
        } else {
            mNextButton.setVisibility(View.VISIBLE);
        }
        final int oldQuestion = mCurrentQuestion;
        mCurrentQuestion = idx;
        final Survey.Question question = questions.get(idx);
        final UpdateDisplayState.AnswerMap answers = surveyState.getAnswers();
        final String answerValue = answers.get(question.getId());
        try {
            if (oldQuestion < idx) {
                mCardHolder.moveTo(question, answerValue, CardCarouselLayout.Direction.FORWARD);
            } else if (oldQuestion > idx) {
                mCardHolder.moveTo(question, answerValue, CardCarouselLayout.Direction.BACKWARD);
            } else {
                mCardHolder.replaceTo(question, answerValue);
            }
        } catch(final CardCarouselLayout.UnrecognizedAnswerTypeException e) {
            goToNextQuestion();
            return;
        }

        if (questions.size() > 1) {
            mProgressTextView.setText("" + (idx + 1) + " of " + questions.size());
        } else {
            mProgressTextView.setText("");
        }
    }

    private void saveAnswer(Survey.Question question, String answer) {
        final UpdateDisplayState.DisplayState.SurveyState surveyState = getSurveyState();
        final UpdateDisplayState.AnswerMap answers = surveyState.getAnswers();
        answers.put(question.getId(), answer.toString());
    }

    @SuppressWarnings("deprecation")
    private void showLightStyle() {
        final ImageView backgroundImage = (ImageView) findViewById(R.id.com_mixpanel_android_notification_gradient);
        final TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
        final TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
        final Button ctaButton = (Button) findViewById(R.id.com_mixpanel_android_notification_button);
        final ImageView closeButton = (ImageView) findViewById(R.id.com_mixpanel_android_image_close);

        backgroundImage.setBackgroundColor(Color.WHITE);

        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.WHITE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            titleView.setTextColor(getResources().getColor(R.color.com_mixpanel_android_inapp_light_hardgray));
            subtextView.setTextColor(getResources().getColor(R.color.com_mixpanel_android_inapp_light_gray));
            ctaButton.setTextColor(getResources().getColor(R.color.com_mixpanel_android_inapp_light_gray));
            border.setStroke(2, getResources().getColor(R.color.com_mixpanel_android_inapp_light_softgray));
        } else {
            titleView.setTextColor(getResources().getColor(R.color.com_mixpanel_android_inapp_light_hardgray, null));
            subtextView.setTextColor(getResources().getColor(R.color.com_mixpanel_android_inapp_light_gray, null));
            ctaButton.setTextColor(getResources().getColor(R.color.com_mixpanel_android_inapp_light_gray, null));
            border.setStroke(2, getResources().getColor(R.color.com_mixpanel_android_inapp_light_softgray, null));
        }
        border.setCornerRadius(6);
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            ctaButton.setBackgroundDrawable(border);
        } else {
            ctaButton.setBackground(border);
        }

        Drawable myIcon = getResources().getDrawable(R.drawable.com_mixpanel_android_close_new);
        if (myIcon != null) {
            final int newColor = getResources().getColor(R.color.com_mixpanel_android_inapp_light_softgray);
            myIcon.setColorFilter(newColor, PorterDuff.Mode.SRC_ATOP);
            closeButton.setImageDrawable(myIcon);
        }

        ctaButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    private void setViewBackground(View v, Drawable d) {
        if (Build.VERSION.SDK_INT < 16) {
            v.setBackgroundDrawable(d);
        } else {
            v.setBackground(d);
        }
    }

    @SuppressLint("NewApi")
    private void requestOrientationLock() {
        if (Build.VERSION.SDK_INT >= 18) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else {
            final int currentOrientation = getResources().getConfiguration().orientation;
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    private void completeSurvey() {
        finish();
    }

    private AlertDialog mDialog;
    private CardCarouselLayout mCardHolder;
    private MixpanelAPI mMixpanel;
    private View mPreviousButton;
    private View mNextButton;
    private TextView mProgressTextView;

    private UpdateDisplayState mUpdateDisplayState;
    private boolean mSurveyBegun = false;
    private int mCurrentQuestion = 0;
    private int mIntentId = -1;

    private static final String SURVEY_BEGUN_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.SURVEY_BEGIN_BUNDLE_KEY";
    private static final String CURRENT_QUESTION_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.CURRENT_QUESTION_BUNDLE_KEY";
    private static final String SURVEY_STATE_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.SURVEY_STATE_BUNDLE_KEY";
    private static final int GRAY_30PERCENT = Color.argb(255, 90, 90, 90);
    private static final int SHADOW_SIZE_THRESHOLD_PX = 100;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.SrvyActvty";

    public static final String INTENT_ID_KEY = "com.mixpanel.android.surveys.SurveyActivity.INTENT_ID_KEY";
}

