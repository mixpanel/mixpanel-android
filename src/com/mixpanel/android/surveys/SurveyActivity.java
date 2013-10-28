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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

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
        mEditAnswerView = (EditText) findViewById(R.id.text_answer);
        mEditAnswerView.setText("");
        mEditAnswerView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) ||
                        actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearComposingText();
                    String answer = v.getText().toString();
                    saveAnswer(mCurrentQuestion, answer);
                    goToNextQuestion();
                    return true;
                }
                return false;
            }
        });
        mChoiceView = (ListView) findViewById(R.id.choice_list);
        mChoiceView.setOnItemClickListener(new OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                String answer = parent.getItemAtPosition(position).toString();
                saveAnswer(mCurrentQuestion, answer); // TODO won't work with text fields
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

        // TODO For testing only, uncomment before merge
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

    public void completeSurvey(View v) {
        completeSurvey();
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
        mCurrentQuestion = idx;
        Survey.Question question = mSurvey.getQuestions().get(mCurrentQuestion);
        Survey.QuestionType questionType = question.getType();
        if (Survey.QuestionType.TEXT == questionType) {
            mChoiceView.setVisibility(View.GONE);
            mEditAnswerView.setVisibility(View.VISIBLE);
        } else if (Survey.QuestionType.MULTIPLE_CHOICE == questionType) {
            mChoiceView.setVisibility(View.VISIBLE);
            mEditAnswerView.setVisibility(View.GONE);
            final ChoiceAdapter answerAdapter = new ChoiceAdapter(question.getChoices(), getLayoutInflater());
            mChoiceView.setAdapter(answerAdapter);
        } else {
            goToNextQuestion();
        }
        mProgressTextView.setText("Question " + (mCurrentQuestion + 1) + " of " + mSurvey.getQuestions().size());
        mPromptView.setText(question.getPrompt());
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
            if (0 == position) {
                return VIEW_TYPE_FIRST;
            }
            if (position == mChoices.size() - 1) {
                return VIEW_TYPE_LAST;
            }
            return VIEW_TYPE_MIDDLE;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            int viewId = -1;
            if (null == convertView) {
                switch(getItemViewType(position)) {
                case VIEW_TYPE_FIRST:
                    viewId = R.layout.com_mixpanel_android_first_choice_answer;
                    break;
                case VIEW_TYPE_LAST:
                    viewId = R.layout.com_mixpanel_android_last_choice_answer;
                    break;
                case VIEW_TYPE_MIDDLE:
                    viewId = R.layout.com_mixpanel_android_middle_choice_answer;
                    break;
                }
                convertView = mInflater.inflate(viewId, parent, false);
            }

            TextView choiceText = (TextView) convertView.findViewById(R.id.multiple_choice_answer_text);
            String choice = mChoices.get(position);
            choiceText.setText(choice);
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_MAX;
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

        private static final int VIEW_TYPE_FIRST = 0;
        private static final int VIEW_TYPE_LAST = 1;
        private static final int VIEW_TYPE_MIDDLE = 2;
        private static final int VIEW_TYPE_MAX = 3; // Should always be precisely one more than the largest VIEW_TYPE
    }

    private static final String LOGTAG = "MixpanelAPI";
    private MixpanelAPI mMixpanel;
    private Survey mSurvey;
    private String mDistinctId;
    private String mToken;
    private TextView mPromptView;
    private TextView mProgressTextView;
    private ListView mChoiceView;
    private EditText mEditAnswerView;

    private Map<Survey.Question, String> mAnswers;
    private int mCurrentQuestion = 0;
}

