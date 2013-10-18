package com.mixpanel.android.surveys;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.Survey;

public class SurveyActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String surveyJsonStr = getIntent().getStringExtra("surveyJson");
        mDistinctId = getIntent().getStringExtra("distinctId");
        mToken = getIntent().getStringExtra("token");
        final byte[] backgroundJpgBytes = getIntent().getByteArrayExtra("backgroundJpgBytes");
        mBackground = BitmapFactory.decodeByteArray(backgroundJpgBytes, 0, backgroundJpgBytes.length);
        mMixpanel = MixpanelAPI.getInstance(this, mToken); // TODO CANT DO THIS. You've gotta make sure you use the same instance? But threads?

        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), mBackground));


        // identify the person we're saving answers for TODO RACE CONDITION NEED DIRECT INSTANCE LOOKUP
        mMixpanel.getPeople().identify(mDistinctId);

        try {
            mSurvey = new Survey(new JSONObject(surveyJsonStr));
            mAnswers = new HashMap<Survey.Question, String>();
        } catch (JSONException e) {
            // TODO can't merge without doing something useful here.
            Log.e(LOGTAG, "Unable to parse survey json: "+surveyJsonStr, e);
        }

        LinearLayout layout = constructView();
        setContentView(layout);

        // listen on skip button press
        mSkipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToNextQuestion();
            }
        });

        // TODO For testing only
        // mMixpanel.getPeople().append("$surveys", mSurvey.getId());
        // mMixpanel.getPeople().append("$collections", mSurvey.getCollectionId());
        // mMixpanel.flush();
        showQuestion(0);
    }

    private LinearLayout constructView() {
        // top level layout
        LinearLayout decideLayout = new LinearLayout(this);
        decideLayout.setOrientation(LinearLayout.VERTICAL);
        decideLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // relative layout for progress / skip btn container
        RelativeLayout progressLayout = new RelativeLayout(this);
        progressLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));

        // progress text (e.g. Question 1 of 10)
        mProgressTextView = new TextView(this);

        // the question text
        mPromptView = new TextView(this);
        mPromptView.setTextSize(20);

        // skip btn
        mSkipButton = new Button(this);
        mSkipButton.setText("Skip");
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        mSkipButton.setLayoutParams(params);
        mSkipButton.setPadding(30, 10, 30, 10);

        // radio group for answer choices
        mAnswerRadioGroup = new RadioGroup(this);
        mAnswerRadioGroup.setLayoutParams(new RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // add em all
        progressLayout.addView(mSkipButton);
        progressLayout.addView(mProgressTextView);
        decideLayout.addView(progressLayout);
        decideLayout.addView(mPromptView);
        decideLayout.addView(mAnswerRadioGroup);
        return decideLayout;
    }

    @Override
    public void onBackPressed() {
        if (mCurrentQuestion > 0) {
            showQuestion(mCurrentQuestion-1);
        } else {
            super.onBackPressed();    //To change body of overridden methods use File | Settings | File Templates.
        }
    }

    private void goToNextQuestion() {
        if (mCurrentQuestion < mSurvey.getQuestions().size()-1) {
            showQuestion(mCurrentQuestion+1);
        } else {
            completeSurvey();
        }
    }

    private void showQuestion(int idx) {

        Survey.Question question = mSurvey.getQuestions().get(idx);
        mCurrentQuestion = idx;
        mProgressTextView.setText("Question " + (idx + 1) + " of " + mSurvey.getQuestions().size());
        mPromptView.setText(question.getPrompt());
        mAnswerRadioGroup.removeAllViews();
        mAnswerRadioGroup.setOnCheckedChangeListener(null);

        for (String choice:question.getChoices()) {
            addChoice(question, choice);
        }

        mAnswerRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                RadioButton checkedButton = (RadioButton) findViewById(radioGroup.getCheckedRadioButtonId());
                Object answer = checkedButton.getText();
                saveAnswer(mCurrentQuestion, answer);
                goToNextQuestion();
            }
        });
    }

    private void saveAnswer(int questionIdx, Object answer) {
        Log.i(LOGTAG, "Answering question " + (questionIdx + 1) + ": " + answer);

        // assign the answer to the question locally
        Survey.Question question = mSurvey.getQuestions().get(questionIdx);
        mAnswers.put(question, answer.toString());
        mMixpanel.getPeople().append("$responses", mSurvey.getCollectionId()); // <<--- TODO should be $union

        try {
            JSONObject answerJson = new JSONObject();
            answerJson.put("$survey_id", mSurvey.getId());
            answerJson.put("$collection_id", mSurvey.getCollectionId());
            answerJson.put("$question_id", question.getId());
            answerJson.put("$question_type", question.getType().toString());

            // TODO find a better way to share this format convention
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            answerJson.put("$time", dateFormat.format(new Date()));
            answerJson.put("$value", answer.toString());

            mMixpanel.getPeople().append("$answers", answerJson);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Couldn't record user's answer.", e);
        }
        mMixpanel.flush();
    }

    private void addChoice(Survey.Question question, Object choice) {
        RadioButton radioButton = new RadioButton(this);
        radioButton.setText((String)choice);
        radioButton.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        radioButton.setPadding(50, 10, 0, 0);
        mAnswerRadioGroup.addView(radioButton);

        if (mAnswers.containsKey(question) && mAnswers.get(question).equals(choice)) {
            mAnswerRadioGroup.check(radioButton.getId());
        }
    }

    private void completeSurvey() {
        finish();
    }

    @Override
    protected void onDestroy() {
        mMixpanel.flush();
        super.onDestroy();
    }


    private static final String LOGTAG = "MixpanelAPI";
    private MixpanelAPI mMixpanel;
    private Survey mSurvey;
    private String mDistinctId;
    private String mToken;
    private TextView mPromptView;
    private TextView mProgressTextView;
    private Button mSkipButton;
    private RadioGroup mAnswerRadioGroup;
    private Bitmap mBackground;

    private Map<Survey.Question, String> mAnswers;
    private int mCurrentQuestion = 0;
}

