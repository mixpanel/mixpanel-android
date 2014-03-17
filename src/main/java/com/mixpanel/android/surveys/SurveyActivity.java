package com.mixpanel.android.surveys;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.Survey;
import com.mixpanel.android.mpmetrics.Survey.Question;
import com.mixpanel.android.mpmetrics.SurveyState;

/**
 * Activity used internally by Mixpanel to display surveys and inapp takeover notifications.
 * The best way to display a SurveyActivity for surveys is to call
 * {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showSurvey(com.mixpanel.android.mpmetrics.Survey, android.app.Activity)}
 */
public class SurveyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActivityType = Type.SURVEY;
        if (getIntent().hasExtra(ACTIVITY_TYPE_KEY) && getIntent().getSerializableExtra(ACTIVITY_TYPE_KEY) == Type.INAPP_TAKEOVER) {
            mActivityType = Type.INAPP_TAKEOVER;
        } else {
            onCreateSurvey(savedInstanceState);
        }

    }

    private void onCreateSurvey(Bundle savedInstanceState) {
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
    public static final String INTENT_ID_KEY = "intentId";
    public static final String SHOW_ASK_DIALOG_KEY = "showAskDialog";
}

