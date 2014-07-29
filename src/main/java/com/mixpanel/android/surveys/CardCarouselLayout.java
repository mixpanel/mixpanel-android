package com.mixpanel.android.surveys;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.mixpanel.android.R;
import com.mixpanel.android.mpmetrics.Survey;

/**
 * Part of the Mixpanel Surveys user interface.
 *
 * Users of the library should not instantiate this class directly.
 */
public class CardCarouselLayout extends ViewGroup {

    public static class UnrecognizedAnswerTypeException extends Exception {
        private UnrecognizedAnswerTypeException(String string) {
            super(string);
        }
        private static final long serialVersionUID = -6040399928243560328L;
    }

    public interface OnQuestionAnsweredListener {
         public void onQuestionAnswered(Survey.Question question, String answer);
    }

    public static enum Direction {
        FORWARD, BACKWARD;
    }

    public CardCarouselLayout(Context context) {
        super(context);
        initCards(context);
    }

    public CardCarouselLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initCards(context);
    }

    public CardCarouselLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initCards(context);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    public void setOnQuestionAnsweredListener(OnQuestionAnsweredListener listener) {
        mListener = listener;
    }

    public void moveTo(Survey.Question question, String answerOrNull, Direction direction)
            throws UnrecognizedAnswerTypeException {
        final QuestionCard tmp = mBackupCard;
        mBackupCard = mVisibleCard;
        mVisibleCard = tmp;
        mVisibleCard.showQuestionOnCard(question, answerOrNull);
        final View viewShowing = mBackupCard.getView();
        final View viewToShow = mVisibleCard.getView();
        viewShowing.setVisibility(View.VISIBLE);
        viewToShow.setVisibility(View.VISIBLE);

        Animation exit = null;
        Animation entrance = null;
        switch(direction) {
        case FORWARD:
            exit = exitLeft();
            entrance = enterRight();
            break;
        case BACKWARD:
            exit = exitRight();
            entrance = enterLeft();
            break;
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
        viewShowing.startAnimation(exit);
        viewToShow.startAnimation(entrance);
        invalidate();
    }

    public void replaceTo(Survey.Question question, String answerOrNull)
            throws UnrecognizedAnswerTypeException {
        mVisibleCard.showQuestionOnCard(question, answerOrNull);
        removeAllViews();
        addView(mVisibleCard.getView());
        addView(mBackupCard.getView());
        invalidate();
    }

    /**
     * Taken (almost) verbatum from system FrameLayout
     * Completely ignores margins and child states.
     * Should probably only take Card views into account.
     */
    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final int count = getChildCount();

        final boolean measureMatchParentChildren =
                MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
                MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
        mMatchParentChildren.clear();

        int maxHeight = 0;
        int maxWidth = 0;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = child.getLayoutParams();
                final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
                final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, 0, lp.height);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
                if (measureMatchParentChildren) {
                    if (lp.width == LayoutParams.MATCH_PARENT ||
                            lp.height == LayoutParams.MATCH_PARENT) {
                        mMatchParentChildren.add(child);
                    }
                }
            }
        }

        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec),
                    resolveSize(maxHeight, heightMeasureSpec));

        for (final View child:mMatchParentChildren) {
            final LayoutParams lp = child.getLayoutParams();
            int childWidthMeasureSpec;
            int childHeightMeasureSpec;

            if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
            } else {
                childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
            }

            if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
            } else {
                childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec, 0, lp.height);
            }

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        final View visible = mVisibleCard.getView();
        int visibleWidth = 0;
        if (visible.getVisibility() != View.GONE) {
            visibleWidth = visible.getMeasuredWidth();
            final int visibleHeight = visible.getMeasuredHeight();
            visible.layout(0, 0, visibleWidth, visibleHeight);
        }
        final View backup = mBackupCard.getView();
        if (backup.getVisibility() != View.GONE) {
            final int backupWidth = backup.getMeasuredWidth();
            final int backupHeight = backup.getMeasuredHeight();
            backup.layout(visibleWidth, 0, visibleWidth + backupWidth, backupHeight);
        }
    }

    private void initCards(Context context) {
        // Nice to have- if CardCarouselLayout rendered a card by default, we could see it in the
        // UI designer and make decisions.
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View v1 = inflater.inflate(R.layout.com_mixpanel_android_question_card, this, false);
        mVisibleCard = new QuestionCard(v1);
        final View v2 = inflater.inflate(R.layout.com_mixpanel_android_question_card, this, false);
        mBackupCard = new QuestionCard(v2);
        this.addView(v1);
        this.addView(v2);
    }

    private static int EXIT_ANGLE = 45;
    private static float EXIT_SIZE = 0.8f;
    private static float EXIT_ROTATION_CENTER_X = 0.5f;
    private static float EXIT_ROTATION_CENTER_Y = 0.5f;
    private Animation enterRight() {
        final AnimationSet set = new AnimationSet(false);
        final RotateAnimation rotateIn = new RotateAnimation(EXIT_ANGLE, 0,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        rotateIn.setDuration(ANIMATION_ROTATION_MILLIS);
        set.addAnimation(rotateIn);

        final ScaleAnimation scaleUp = new ScaleAnimation(EXIT_SIZE, 1, EXIT_SIZE, 1,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        scaleUp.setDuration(ANIMATION_ROTATION_MILLIS);
        set.addAnimation(scaleUp);

        final TranslateAnimation slideX = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 1.3f,
                Animation.RELATIVE_TO_PARENT, 0,
                Animation.RELATIVE_TO_PARENT, 0,
                Animation.RELATIVE_TO_PARENT, 0
        );
        slideX.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideX);
        return set;
    }

    private Animation exitRight() {
        final AnimationSet set = new AnimationSet(false);
        final RotateAnimation rotateOut = new RotateAnimation(0, EXIT_ANGLE,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        rotateOut.setDuration(ANIMATION_ROTATION_MILLIS);
        rotateOut.setStartOffset(ANIMATION_DURATION_MILLIS - ANIMATION_ROTATION_MILLIS);
        set.addAnimation(rotateOut);

        final ScaleAnimation scaleDown = new ScaleAnimation(1, EXIT_SIZE, 1, EXIT_SIZE,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        scaleDown.setDuration(ANIMATION_ROTATION_MILLIS);
        scaleDown.setStartOffset(ANIMATION_DURATION_MILLIS - ANIMATION_ROTATION_MILLIS);
        set.addAnimation(scaleDown);

        final TranslateAnimation slideX = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, -1.0f,
                Animation.RELATIVE_TO_PARENT, 0.3f,
                Animation.RELATIVE_TO_PARENT, 0,
                Animation.RELATIVE_TO_PARENT, 0
        );
        slideX.setInterpolator(new AccelerateInterpolator());
        slideX.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideX);
        return set;
    }

    private Animation enterLeft() {
        final AnimationSet set = new AnimationSet(false);
        final RotateAnimation rotateIn = new RotateAnimation(-EXIT_ANGLE, 0,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        rotateIn.setDuration(ANIMATION_ROTATION_MILLIS);
        set.addAnimation(rotateIn);

        final ScaleAnimation scaleUp = new ScaleAnimation(EXIT_SIZE, 1, EXIT_SIZE, 1,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        scaleUp.setDuration(ANIMATION_ROTATION_MILLIS);
        set.addAnimation(scaleUp);

        final TranslateAnimation slideX = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, -1.3f,
                Animation.RELATIVE_TO_PARENT, 0,
                Animation.RELATIVE_TO_PARENT, 0,
                Animation.RELATIVE_TO_PARENT, 0
        );
        slideX.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideX);

        return set;
    }

    private Animation exitLeft() {
        final AnimationSet set = new AnimationSet(false);
        final RotateAnimation rotateOut = new RotateAnimation(0, -EXIT_ANGLE,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        rotateOut.setDuration(ANIMATION_DURATION_MILLIS);
        rotateOut.setStartOffset(ANIMATION_DURATION_MILLIS - ANIMATION_ROTATION_MILLIS);
        set.addAnimation(rotateOut);

        final ScaleAnimation scaleDown = new ScaleAnimation(1, EXIT_SIZE, 1, EXIT_SIZE,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_X,
                Animation.RELATIVE_TO_SELF, EXIT_ROTATION_CENTER_Y
        );
        scaleDown.setDuration(ANIMATION_DURATION_MILLIS);
        scaleDown.setStartOffset(ANIMATION_DURATION_MILLIS - ANIMATION_ROTATION_MILLIS);
        set.addAnimation(scaleDown);

        final TranslateAnimation slideX = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, -1,
                Animation.RELATIVE_TO_PARENT, -2.3f,
                Animation.RELATIVE_TO_PARENT, 0,
                Animation.RELATIVE_TO_PARENT, 0
        );
        slideX.setInterpolator(new AccelerateInterpolator());
        slideX.setDuration(ANIMATION_DURATION_MILLIS);
        set.addAnimation(slideX);

        return set;
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

            final TextView choiceText = (TextView) convertView.findViewById(R.id.com_mixpanel_android_multiple_choice_answer_text);
            final String choice = mChoices.get(position);
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

    private class QuestionCard {

        public QuestionCard(final View cardView) {
            mCardView = cardView;
            mPromptView = (TextView) cardView.findViewWithTag("com_mixpanel_android_TAG_prompt_text");
            mEditAnswerView = (EditText) cardView.findViewWithTag("com_mixpanel_android_TAG_text_answer");
            mChoiceView = (ListView) cardView.findViewWithTag("com_mixpanel_android_TAG_choice_list");
            mEditAnswerView.setText("");
            mEditAnswerView.setOnEditorActionListener(new OnEditorActionListener() {
                @Override
                public boolean onEditorAction(final TextView view, final int actionId, final KeyEvent event) {
                    final boolean enterKeyDown =
                            event != null &&
                            event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == KeyEvent.ACTION_DOWN &&
                            0 == (event.getFlags() & KeyEvent.FLAG_CANCELED);

                    if (enterKeyDown || actionId == EditorInfo.IME_ACTION_DONE) {
                        view.clearComposingText();
                        if (null != mListener) {
                            final String answer = view.getText().toString();
                            mListener.onQuestionAnswered(mQuestion, answer);
                        }
                        return true;
                    }
                    return false;
                }
            });
            mChoiceView.setOnItemClickListener(new OnItemClickListener(){
                @Override
                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                    if (null != mListener) {
                        final String answer = parent.getItemAtPosition(position).toString();
                        // Wait for the checkmark to show before reporting the selection
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onQuestionAnswered(mQuestion, answer);
                            }
                        }, ANIMATION_DURATION_MILLIS/2);
                    }
                }
            });
        }

        public View getView() {
            return mCardView;
        }

        public void showQuestionOnCard(Survey.Question question, String answerOrNull)
            throws UnrecognizedAnswerTypeException {
            mQuestion = question;
            mPromptView.setText(mQuestion.getPrompt());
            final InputMethodManager inputMethodManager =
                    (InputMethodManager) mCardView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            final Survey.QuestionType questionType = question.getType();
            if (Survey.QuestionType.TEXT == questionType) {
                mChoiceView.setVisibility(View.GONE);
                mEditAnswerView.setVisibility(View.VISIBLE);
                if (null != answerOrNull) {
                    mEditAnswerView.setText(answerOrNull);
                }
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    mEditAnswerView.requestFocus();
                    inputMethodManager.showSoftInput(mEditAnswerView, 0);
                } else {
                    // Landscape soft keyboard will obscure the question
                    inputMethodManager.hideSoftInputFromWindow(mCardView.getWindowToken(), 0);
                }
            } else if (Survey.QuestionType.MULTIPLE_CHOICE == questionType) {
                inputMethodManager.hideSoftInputFromWindow(mCardView.getWindowToken(), 0);
                mChoiceView.setVisibility(View.VISIBLE);
                mEditAnswerView.setVisibility(View.GONE);
                final ChoiceAdapter answerAdapter = new ChoiceAdapter(question.getChoices(), LayoutInflater.from(getContext()));
                mChoiceView.setAdapter(answerAdapter);
                mChoiceView.clearChoices();
                if (null != answerOrNull) {
                    for (int i = 0; i < answerAdapter.getCount(); i++) {
                        final String item = answerAdapter.getItem(i);
                        if (item.equals(answerOrNull)) {
                            mChoiceView.setItemChecked(i, true);
                        }
                    }
                }
            } else {
                throw new UnrecognizedAnswerTypeException("No way to display question type " + questionType);
            }
            mCardView.invalidate();
        }

        private Survey.Question mQuestion;
        private final View mCardView;
        private final TextView mPromptView;
        private final TextView mEditAnswerView;
        private final ListView mChoiceView;
    }

    private final List<View> mMatchParentChildren = new ArrayList<View>(1);
    private QuestionCard mVisibleCard;
    private QuestionCard mBackupCard;
    private OnQuestionAnsweredListener mListener = null;

    private static final long ANIMATION_DURATION_MILLIS = 330;
    private static final long ANIMATION_ROTATION_MILLIS = (ANIMATION_DURATION_MILLIS * 6) / 10;
}
