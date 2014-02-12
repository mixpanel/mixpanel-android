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
 * Activity used internally by Mixpanel to display surveys. You shouldn't send intents directly to this activity-
 * The best way to display a SurveyActivity is to call
 * {@link com.mixpanel.android.mpmetrics.MixpanelAPI.People#showSurvey(com.mixpanel.android.mpmetrics.Survey, android.app.Activity)}
 */
public class SurveyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurveyState saved = null;
        if (null != savedInstanceState) {
            saved = savedInstanceState.getParcelable(SURVEY_STATE_BUNDLE_KEY);
        }
        mIntentId = getIntent().getIntExtra("intentID", Integer.MAX_VALUE);
        mSurveyState = SurveyState.claimSurveyState(saved, mIntentId);
        if (null == mSurveyState) {
            Log.e(LOGTAG, "Survey intent received, but no survey was found.");
            finish();
            return;
        }
        if (null != savedInstanceState) {
            mCurrentQuestion = savedInstanceState.getInt(CURRENT_QUESTION_BUNDLE_KEY, 0);
            // It's possible that this mCurrentQuestion isn't relevant to the current survey
            // (if, for example,
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

        final Survey survey = mSurveyState.getSurvey();
        final String answerDistinctId = mSurveyState.getDistinctId();
        if (null == answerDistinctId) {
            Log.i(LOGTAG, "Can't show a survey to a user with no distinct id set");
            finish();
            return;
        }

        mMixpanel = MixpanelAPI.getInstance(this, mSurveyState.getToken());
        final MixpanelAPI.People people = mMixpanel.getPeople().withIdentity(answerDistinctId);
        people.append("$surveys", survey.getId());
        people.append("$collections", survey.getCollectionId());
        mMixpanel.flush();
        showQuestion(mCurrentQuestion);
    }


    @SuppressLint("SimpleDateFormat")
    @Override
    protected void onDestroy() {
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
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_QUESTION_BUNDLE_KEY, mCurrentQuestion);
        outState.putParcelable(SURVEY_STATE_BUNDLE_KEY, mSurveyState);
    }

    @Override
    public void onBackPressed() {
        if (mCurrentQuestion > 0) {
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


    private MixpanelAPI mMixpanel;
    private View mPreviousButton;
    private View mNextButton;
    private TextView mProgressTextView;
    private CardCarouselLayout mCardHolder;

    private SurveyState mSurveyState;
    private int mCurrentQuestion = 0;
    private int mIntentId = -1;

    private static final String CURRENT_QUESTION_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.CURRENT_QUESTION_BUNDLE_KEY";
    private static final String SURVEY_STATE_BUNDLE_KEY = "com.mixpanel.android.surveys.SurveyActivity.SURVEY_STATE_BUNDLE_KEY";
    private static final String LOGTAG = "MixpanelAPI";
    private static final int GRAY_30PERCENT = Color.argb(255, 90, 90, 90);
}

