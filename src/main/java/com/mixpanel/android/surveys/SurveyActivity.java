package com.mixpanel.android.surveys;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.InAppNotification;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.Survey;
import com.mixpanel.android.mpmetrics.Survey.Question;
import com.mixpanel.android.mpmetrics.SurveyState;

/**
 * Activity used internally by Mixpanel to display surveys and inapp takeover notifications.
 * The best way to display a SurveyActivity for surveys is to call
 * {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showSurvey(com.mixpanel.android.mpmetrics.Survey, android.app.Activity)}
 */
@TargetApi(11)
public class SurveyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivityType = Type.SURVEY;
        if (getIntent().hasExtra(ACTIVITY_TYPE_KEY) && getIntent().getSerializableExtra(ACTIVITY_TYPE_KEY) == Type.INAPP_TAKEOVER) {
            mActivityType = Type.INAPP_TAKEOVER;
            mInAppNotification = getIntent().getParcelableExtra(INAPP_NOTIFICATION_KEY);
            onCreateInAppNotification(savedInstanceState);
        } else {
            onCreateSurvey(savedInstanceState);
        }
    }

    private void onCreateInAppNotification(Bundle savedInstanceState) {
        setContentView(R.layout.com_mixpanel_android_activity_notification_full);

        final ImageView notifImage = (ImageView) findViewById(R.id.com_mixpanel_android_notification_image);
        final TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
        final TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
        final Button ctaButton = (Button) findViewById(R.id.com_mixpanel_android_notification_button);
        final ImageButton closeButton = (ImageButton) findViewById(R.id.com_mixpanel_android_button_exit);

        titleView.setText(mInAppNotification.getTitle());
        subtextView.setText(mInAppNotification.getBody());
        notifImage.setImageBitmap(mInAppNotification.getImage());

        final String ctaUrl = mInAppNotification.getCallToActionUrl();
        if (ctaUrl != null && ctaUrl.length() > 0) {
            ctaButton.setText(mInAppNotification.getCallToAction());
        }
        ctaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String uriString = mInAppNotification.getCallToActionUrl();
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
                        SurveyActivity.this.startActivity(viewIntent);
                    } catch (ActivityNotFoundException e) {
                        Log.i(LOGTAG, "User doesn't have an activity for notification URI");
                    }
                }
                finish();
            }
        });
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
                finish();
            }
        });
    }

    private void onCreateSurvey(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        SurveyState saved = null;
        if (null != savedInstanceState) {
            saved = savedInstanceState.getParcelable(SURVEY_STATE_BUNDLE_KEY);
        }
        mIntentId = getIntent().getIntExtra(INTENT_ID_KEY, Integer.MAX_VALUE);
        mSurveyState = SurveyState.claimSurveyState(saved, mIntentId);
        if (null == mSurveyState) {
            Log.e(LOGTAG, "Survey intent received, but no survey was found.");
            finish();
            return;
        }
        if (null != savedInstanceState) {
            mCurrentQuestion = savedInstanceState.getInt(CURRENT_QUESTION_BUNDLE_KEY, 0);
        }
        final String answerDistinctId = mSurveyState.getDistinctId();
        if (null == answerDistinctId) {
            Log.i(LOGTAG, "Can't show a survey to a user with no distinct id set");
            finish();
            return;
        }

        setContentView(R.layout.com_mixpanel_android_activity_survey);
        final Bitmap background = mSurveyState.getBackground();
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

        if (mActivityType == Type.SURVEY) {
            onStartSurvey();
        }
    }

    private void onStartSurvey() {
        trackSurveyAttempted();
        if (getIntent().getBooleanExtra(SHOW_ASK_DIALOG_KEY, true)) {
            final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setTitle("We'd love your feedback!");
            alertBuilder.setMessage("Mind taking a quick survey?");
            alertBuilder.setPositiveButton("Sure", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SurveyActivity.this.findViewById(R.id.com_mixpanel_android_activity_survey_id).setVisibility(View.VISIBLE);
                    showQuestion(mCurrentQuestion);
                }
            });
            alertBuilder.setNegativeButton("No, Thanks", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SurveyActivity.this.finish();
                }
            });
            alertBuilder.setCancelable(false);
            alertBuilder.show();
        } else {
            SurveyActivity.this.findViewById(R.id.com_mixpanel_android_activity_survey_id).setVisibility(View.VISIBLE);
            showQuestion(mCurrentQuestion);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (this.mActivityType == Type.INAPP_TAKEOVER) {
            onResumeInAppNotification();
        }
    }

    private void onResumeInAppNotification() {
        final ImageView notifImage = (ImageView) findViewById(R.id.com_mixpanel_android_notification_image);
        final TextView titleView = (TextView) findViewById(R.id.com_mixpanel_android_notification_title);
        final TextView subtextView = (TextView) findViewById(R.id.com_mixpanel_android_notification_subtext);
        final Button ctaButton = (Button) findViewById(R.id.com_mixpanel_android_notification_button);
        final ImageButton closeButton = (ImageButton) findViewById(R.id.com_mixpanel_android_button_exit);

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

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        closeButton.startAnimation(fadeIn);
    }

    @Override
    protected void onDestroy() {
        if (mActivityType == Type.SURVEY) {
            onDestroySurvey();
        }

        super.onDestroy();
    }

    @SuppressLint("SimpleDateFormat")
    private void onDestroySurvey() {
        if (null != mMixpanel) {
            if (null != mSurveyState) {
                final Survey survey = mSurveyState.getSurvey();
                final List<Survey.Question> questionList = survey.getQuestions();

                final String answerDistinctId = mSurveyState.getDistinctId();
                final MixpanelAPI.People people = mMixpanel.getPeople().withIdentity(answerDistinctId);
                people.append("$responses", survey.getCollectionId());

                final SurveyState.AnswerMap answers = mSurveyState.getAnswers();
                for (final Survey.Question question:questionList) {
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
                        } catch (final JSONException e) {
                            Log.e(LOGTAG, "Couldn't record user's answer.", e);
                        }
                    } // if answer is present
                } // For each question
            } // if we have a survey state
            mMixpanel.flush();
        } // if we initialized property and we have a mixpanel

        SurveyState.releaseSurvey(mIntentId);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mActivityType == Type.SURVEY) {
            onSaveInstanceStateSurvey(outState);
        }
    }

    private void onSaveInstanceStateSurvey(Bundle outState) {
        outState.putInt(CURRENT_QUESTION_BUNDLE_KEY, mCurrentQuestion);
        outState.putParcelable(SURVEY_STATE_BUNDLE_KEY, mSurveyState);
    }

    @Override
    public void onBackPressed() {
        if (mActivityType == Type.SURVEY && mCurrentQuestion > 0) {
            goToPreviousQuestion();
        } else {
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

    private void trackSurveyAttempted() {
        mMixpanel = MixpanelAPI.getInstance(SurveyActivity.this, mSurveyState.getToken());
        final Survey survey = mSurveyState.getSurvey();
        final MixpanelAPI.People people = mMixpanel.getPeople().withIdentity(mSurveyState.getDistinctId());
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
        final int surveySize = mSurveyState.getSurvey().getQuestions().size();
        if (mCurrentQuestion < surveySize - 1) {
            showQuestion(mCurrentQuestion + 1);
        } else {
            completeSurvey();
        }
    }

    private void showQuestion(final int idx) {
        final List<Question> questions = mSurveyState.getSurvey().getQuestions();
        if (0 == idx || questions.size() == 0) {
            mPreviousButton.setEnabled(false);
        } else {
            mPreviousButton.setEnabled(true);
        }
        if (idx >= questions.size() - 1) {
            mNextButton.setEnabled(false);
        } else {
            mNextButton.setEnabled(true);
        }
        final int oldQuestion = mCurrentQuestion;
        mCurrentQuestion = idx;
        final Survey.Question question = questions.get(idx);
        final SurveyState.AnswerMap answers = mSurveyState.getAnswers();
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
        final SurveyState.AnswerMap answers = mSurveyState.getAnswers();
        answers.put(question.getId(), answer.toString());
    }

    private void completeSurvey() {
        finish();
    }

    private CardCarouselLayout mCardHolder;
    private InAppNotification mInAppNotification;
    private MixpanelAPI mMixpanel;
    private View mPreviousButton;
    private View mNextButton;
    private TextView mProgressTextView;
    private Type mActivityType;

    private SurveyState mSurveyState;
    private int mCurrentQuestion = 0;
    private int mIntentId = -1;

    private static final String CURRENT_QUESTION_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.CURRENT_QUESTION_BUNDLE_KEY";
    private static final String SURVEY_STATE_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.SURVEY_STATE_BUNDLE_KEY";
    private static final String LOGTAG = "MixpanelAPI";
    private static final int GRAY_30PERCENT = Color.argb(255, 90, 90, 90);

    public static enum Type {
        INAPP_TAKEOVER {
            @Override
            public String toString() {
                return "inapp_takeover";
            }
        },
        SURVEY {
            @Override
            public String toString() {
                return "survey";
            }
        }
    };
    public static final String ACTIVITY_TYPE_KEY = "activityType";
    public static final String INAPP_NOTIFICATION_KEY = "inapp_notification";
    public static final String INTENT_ID_KEY = "intentId";
    public static final String SHOW_ASK_DIALOG_KEY = "showAskDialog";
}

