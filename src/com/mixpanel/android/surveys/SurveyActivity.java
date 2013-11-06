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
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
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
        mVisibleCard = null;
        mBackupCard = null;

        mDistinctId = getIntent().getStringExtra("distinctId");
        mToken = getIntent().getStringExtra("token");
        String surveyJsonStr = getIntent().getStringExtra("surveyJson");
        final byte[] backgroundJpgBytes = getIntent().getByteArrayExtra("backgroundJpgBytes");
        final Bitmap background = BitmapFactory.decodeByteArray(backgroundJpgBytes, 0, backgroundJpgBytes.length);
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), background));

        setContentView(R.layout.com_mixpanel_android_activity_survey);
        mProgressTextView = (TextView) findViewById(R.id.progress_text);
        mCardHolder = (ViewGroup) findViewById(R.id.question_card_holder);

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

    private void showQuestion(final int idx) {
        final int oldQuestion = mCurrentQuestion;

        QuestionCard cardToShow = mBackupCard;
        if (null == cardToShow) {
            final View v = getLayoutInflater().inflate(R.layout.com_mixpanel_android_question_card, null);
            cardToShow = new QuestionCard(v);
        }
        final Survey.Question question = mSurvey.getQuestions().get(idx);
        final String answerValue = mAnswers.get(question);
        try {
            cardToShow.showQuestionOnCard(this, question, answerValue);
        } catch(UnrecognizedAnswerTypeException e) {
            goToNextQuestion();
            return;
        }
        // ELSE

        final QuestionCard cardShowing = mVisibleCard;
        if (null == cardShowing) {
            mCardHolder.addView(cardToShow.getView());
        } else {
            final View viewShowing = cardShowing.getView();
            final View viewToShow = cardToShow.getView();
            mCardHolder.removeAllViews(); // TODO right now required because we could be in transit
            viewShowing.setVisibility(View.VISIBLE);
            viewToShow.setVisibility(View.VISIBLE);
            mCardHolder.addView(viewShowing);
            mCardHolder.addView(viewToShow);

            Animation exit;
            Animation entrance;
            if (oldQuestion < idx) {
                exit = exitLeft(viewShowing.getWidth(), viewShowing.getHeight());
                entrance = enterRight(viewToShow.getWidth(), viewToShow.getHeight());
            } else {
                exit = exitRight(viewShowing.getWidth(), viewShowing.getHeight());
                entrance = enterLeft(viewToShow.getWidth(), viewToShow.getHeight());
            }

            exit.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    viewShowing.setVisibility(View.GONE);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
                @Override
                public void onAnimationStart(Animation animation) {}
            });

            entrance.setFillAfter(true);
            viewShowing.startAnimation(exit);
            viewToShow.startAnimation(entrance);
            // TODO Need to disable the card before we give up control, or we may get votes in transit...
        }

        mProgressTextView.setText("" + (idx + 1) + " of " + mSurvey.getQuestions().size());
        mBackupCard = cardShowing;
        mVisibleCard = cardToShow;
        mCurrentQuestion = idx;
    }

    @SuppressLint("SimpleDateFormat")
    private void saveAnswer(Survey.Question question, String answer) {
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

    private Animation enterRight(final float cardWidth, final float cardHeight) {
        final float slideDistance = cardWidth * 1.3f;
        final float dropDistance = cardHeight * 4;

        AnimationSet set = new AnimationSet(true);
        TranslateAnimation slideIn = new TranslateAnimation(slideDistance, 0, dropDistance, 0);
        slideIn.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideIn);

        RotateAnimation rotateIn = new RotateAnimation(90, 0, 0, cardHeight);
        rotateIn.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(rotateIn);

        return set;
    }

    private Animation exitRight(final float cardWidth, final float cardHeight) {
        final float slideDistance = cardWidth * 1.3f;
        final float dropDistance = cardHeight * 4;

        AnimationSet set = new AnimationSet(true);
        TranslateAnimation slideOut = new TranslateAnimation(0, slideDistance, 0, dropDistance);
        slideOut.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideOut);

        RotateAnimation rotateOut = new RotateAnimation(0, 90, 0, cardHeight);
        rotateOut.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(rotateOut);
        return set;
    }

    private Animation exitLeft(final float cardWidth, final float cardHeight) {
        final float slideDistance = cardWidth * 1.3f;

        AnimationSet set = new AnimationSet(false); // TODO consider using true to share a single interpolator
        TranslateAnimation slideX = new TranslateAnimation(0, -slideDistance, 0, 0);
        slideX.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideX);

        RotateAnimation rotateOut = new RotateAnimation(0, -90, cardWidth, cardHeight);
        rotateOut.setStartOffset((long) (ANIMATION_DURATION_MILLIS * 0.4));
        rotateOut.setDuration(ANIMATION_DURATION_MILLIS); // TODO how does this interact with the offset?
        set.addAnimation(rotateOut);

        ScaleAnimation scaleDown = new ScaleAnimation(1, 0.8f, 1, 0.8f);
        scaleDown.setStartOffset((long) (ANIMATION_DURATION_MILLIS * 0.4));
        scaleDown.setDuration(ANIMATION_DURATION_MILLIS); // TODO how does this interact with the offset?
        set.addAnimation(scaleDown);
        return set;
    }

    private Animation enterLeft(final float cardWidth, final float cardHeight) {
        final float slideDistance = cardWidth * 1.3f;

        AnimationSet set = new AnimationSet(false); // TODO consider using true to share a single interpolator
        TranslateAnimation slideX = new TranslateAnimation(-slideDistance, 0, 0, 0);
        slideX.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideX);

        RotateAnimation rotateIn = new RotateAnimation(-90, 0, cardWidth, cardHeight);
        rotateIn.setDuration((long) (ANIMATION_DURATION_MILLIS * 0.4));
        set.addAnimation(rotateIn);

        ScaleAnimation scaleUp = new ScaleAnimation(0.8f, 1, 0.8f, 1);
        scaleUp.setDuration((long) (ANIMATION_DURATION_MILLIS * 0.4));
        set.addAnimation(scaleUp);

        return set;
    }

    private static class UnrecognizedAnswerTypeException extends Exception {
        public UnrecognizedAnswerTypeException(String string) {
            super(string);
        }
        private static final long serialVersionUID = -6040399928243560328L;
    }

    private class QuestionCard {

        public QuestionCard(final View cardView) {
            mCardView = cardView;
            mPromptView = (TextView) cardView.findViewById(R.id.prompt_text);
            mEditAnswerView = (EditText) cardView.findViewById(R.id.text_answer);
            mChoiceView = (ListView) cardView.findViewById(R.id.choice_list);
            mEditAnswerView.setText("");
            mEditAnswerView.setOnEditorActionListener(new OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) ||
                            actionId == EditorInfo.IME_ACTION_DONE) {
                        v.clearComposingText();
                        String answer = v.getText().toString();
                        saveAnswer(mQuestion, answer);
                        goToNextQuestion();
                        return true;
                    }
                    return false;
                }
            });
            mChoiceView.setOnItemClickListener(new OnItemClickListener(){
                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    String answer = parent.getItemAtPosition(position).toString();
                    saveAnswer(mQuestion, answer);
                    goToNextQuestion();
                }
            });
        }

        public View getView() {
            return mCardView;
        }

        public void showQuestionOnCard(Activity parent, Survey.Question question, String answerValueOrNull)
            throws UnrecognizedAnswerTypeException {
            mQuestion = question;
            mPromptView.setText(mQuestion.getPrompt());

            Survey.QuestionType questionType = question.getType();
            if (Survey.QuestionType.TEXT == questionType) {
                mChoiceView.setVisibility(View.GONE);
                mEditAnswerView.setVisibility(View.VISIBLE);
                if (null != answerValueOrNull) {
                    mEditAnswerView.setText(answerValueOrNull);
                }
            } else if (Survey.QuestionType.MULTIPLE_CHOICE == questionType) {
                mChoiceView.setVisibility(View.VISIBLE);
                mEditAnswerView.setVisibility(View.GONE);
                final ChoiceAdapter answerAdapter = new ChoiceAdapter(question.getChoices(), parent.getLayoutInflater());
                mChoiceView.setAdapter(answerAdapter);
                mChoiceView.clearChoices();
                if (null != answerValueOrNull) {
                    for (int i = 0; i < answerAdapter.getCount(); i++) {
                        String item = answerAdapter.getItem(i);
                        if (item.equals(answerValueOrNull)) {
                            mChoiceView.setItemChecked(i, true);
                        }
                    }
                }
            } else {
                throw new UnrecognizedAnswerTypeException("No way to display question type " + questionType);
            }
        }

        private Survey.Question mQuestion;
        private final View mCardView;
        private final TextView mPromptView;
        private final TextView mEditAnswerView;
        private final ListView mChoiceView;
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

            TextView choiceText = (TextView) convertView.findViewById(R.id.com_mixpanel_android_multiple_choice_answer_text);
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

    private MixpanelAPI mMixpanel;
    private Survey mSurvey;
    private String mDistinctId;
    private String mToken;
    private TextView mProgressTextView;
    private ViewGroup mCardHolder;
    private QuestionCard mVisibleCard;
    private QuestionCard mBackupCard;

    private Map<Survey.Question, String> mAnswers;
    private int mCurrentQuestion = 0;

    private static final String LOGTAG = "MixpanelAPI";
    private static final long ANIMATION_DURATION_MILLIS = 400;
}

