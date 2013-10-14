package com.mixpanel.android.mpmetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Survey {

    /* package */ Survey(JSONObject description) throws JSONException {
        mId = description.getInt("id");
        JSONArray collectionsJArray = description.getJSONArray("collections");
        List<Integer> collectionsList = new ArrayList<Integer>(collectionsJArray.length());
        for (int i = 0; i < collectionsJArray.length(); i++) {
            JSONObject o = collectionsJArray.getJSONObject(i);
            collectionsList.add(o.getInt("id"));
        }
        mCollections = Collections.unmodifiableList(collectionsList);

        JSONArray questionsJArray = description.getJSONArray("questions");
        List<Question> questionsList = new ArrayList<Question>(questionsJArray.length());
        for (int i = 0; i < questionsJArray.length(); i++) {
            JSONObject q = questionsJArray.getJSONObject(i);
            questionsList.add(new Question(q));
        }
        mQuestions = Collections.unmodifiableList(questionsList);

    }

    public int getId() {
        return mId;
    }

    public List<Integer> getCollections() {
        return mCollections;
    }

    public List<Question> getQuestions() {
        return mQuestions;
    }

    public enum QuestionType {
        UNKNOWN,
        MULTIPLE_CHOICE,
        TEXT
    };

    public class Question {
        private Question(JSONObject question) throws JSONException {
            mQuestionId = question.getInt("id");
            mQuestionType = question.getString("type").intern();
            mPrompt = question.getString("prompt");

            List<String> choicesList = Collections.<String>emptyList();
            if (question.has("extra_data")) {
                JSONObject extraData = question.getJSONObject("extra_data");
                if (extraData.has("$choices")) {
                    JSONArray choices = extraData.getJSONArray("$choices");
                    choicesList = new ArrayList<String>(choices.length());
                    for (int i = 0; i < choices.length(); i++) {
                        choicesList.add(choices.getString(i));
                    }
                }
            }
            mChoices = Collections.unmodifiableList(choicesList);
        }

        public final int getId() {
            return mQuestionId;
        }

        public final String getPrompt() {
            return mPrompt;
        }

        public final List<String> getChoices() {
            return mChoices;
        }

        public final QuestionType getType() {
            if ("multiple_choice".equals(mQuestionType)) {
                return QuestionType.MULTIPLE_CHOICE;
            }
            if ("text".equals(mQuestionType)) {
                return QuestionType.TEXT;
            }
            return QuestionType.UNKNOWN;
        }

        private final int mQuestionId;
        private final String mQuestionType;
        private final String mPrompt;
        private final List<String> mChoices;
    }

    private final int mId;
    private final List<Integer> mCollections;
    private final List<Question> mQuestions;
}
