package com.mixpanel.android.surveys;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.Survey;

public class SurveyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDistinctId = getIntent().getStringExtra("distinctId");
        mToken = getIntent().getStringExtra("token");
        String surveyJsonStr = getIntent().getStringExtra("surveyJson");
        final byte[] backgroundJpgBytes = getIntent().getByteArrayExtra("backgroundJpgBytes");
        final Bitmap background = BitmapFactory.decodeByteArray(backgroundJpgBytes, 0, backgroundJpgBytes.length);
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), background));
        setContentView(R.layout.com_mixpanel_android_activity_survey);
        mProgressTextView = (TextView) findViewById(R.id.progress_text);
        mPromptView = (TextView) findViewById(R.id.prompt_text);
        mChoiceView = (ListView) findViewById(R.id.choice_list);
        mChoiceView.setOnItemClickListener(new OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                String answer = parent.getItemAtPosition(position).toString();
                saveAnswer(mCurrentQuestion, answer);
                goToNextQuestion();
            }
        });

        // identify the person we're saving answers for TODO RACE CONDITION NEED DIRECT INSTANCE LOOKUP
        mMixpanel = MixpanelAPI.getInstance(this, mToken); // TODO CANT DO THIS. You've gotta make sure you use the same instance? But threads?
        mMixpanel.getPeople().identify(mDistinctId);
        try {
            mSurvey = new Survey(new JSONObject(surveyJsonStr));
            mAnswers = new HashMap<Survey.Question, String>();
        } catch (JSONException e) {
            // TODO can't merge without doing something useful here.
            Log.e(LOGTAG, "Unable to parse survey json: " + surveyJsonStr, e);
        }

        // TODO For testing only
        // mMixpanel.getPeople().append("$surveys", mSurvey.getId());
        // mMixpanel.getPeople().append("$collections", mSurvey.getCollectionId());
        // mMixpanel.flush();
        showQuestion(0);
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

    @Override
    protected void onDestroy() {
        mMixpanel.flush();
        super.onDestroy();
    }

    private void goToPreviousQuestion() {
        if (mCurrentQuestion > 0) {
            showQuestion(mCurrentQuestion-1);
        } else {
            completeSurvey();
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
        final ChoiceAdapter answerAdapter = new ChoiceAdapter(question.getChoices(), getLayoutInflater());
        mChoiceView.setAdapter(answerAdapter);
    }

    @SuppressLint("SimpleDateFormat")
    private void saveAnswer(int questionIdx, String answer) {
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

    private void completeSurvey() {
        finish();
    }

    private static class ChoiceAdapter implements ListAdapter {

        public ChoiceAdapter(List<String> choices, LayoutInflater inflater) {
            mChoices = choices;
            mInflater = inflater;
        }

        @Override
        public int getCount() {
            return mChoices.size();
        }

        @Override
        public String getItem(int position) {
            return mChoices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = mInflater.inflate(R.layout.com_mixpanel_android_multiple_choice_answer, parent, false);
            }
            TextView choiceText = (TextView) convertView.findViewById(R.id.multiple_choice_answer_text);
            String choice = mChoices.get(position);
            choiceText.setText(choice);
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return mChoices.isEmpty();
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            ; // Underlying data *never* changes
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            ; // Underlying data never changes
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int arg0) {
            return true;
        }

        private final List<String> mChoices;
        private final LayoutInflater mInflater;
    }

    private static final String LOGTAG = "MixpanelAPI";
    private MixpanelAPI mMixpanel;
    private Survey mSurvey;
    private String mDistinctId;
    private String mToken;
    private TextView mPromptView;
    private TextView mProgressTextView;
    private ListView mChoiceView;

    private Map<Survey.Question, String> mAnswers;
    private int mCurrentQuestion = 0;
}

