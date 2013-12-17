package com.mixpanel.android.mpmetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a Survey, configured in Mixpanel, suitable for showing to a user.
 *
 * The typical use of this class follows the pattern.
 * <pre>
 * {@code
 *   Activity parent = this;
 *   mixpanel.getPeople().checkForSurveys(new SurveyCallbacks() {
 *       {@literal @}Override
 *       public void foundSurvey(Survey survey) {
 *           if (survey != null) {
 *               mixpanel.getPeople().showSurvey(survey, parent);
 *           }
 *       }
 *   });
 * }
 * </pre>
 */
public class Survey {

    public static class BadSurveyException extends Exception {
        public BadSurveyException(String detailMessage) {
            super(detailMessage);
        }

        public BadSurveyException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        private static final long serialVersionUID = 4858739193395706341L;
    }

    public Survey(JSONObject description) throws BadSurveyException {
        try {
            mDescription = description;
            mId = description.getInt("id");
            final JSONArray collectionsJArray = description.getJSONArray("collections");
            final JSONObject collection0 = collectionsJArray.getJSONObject(0);
            mCollectionId = collection0.getInt("id");

            final JSONArray questionsJArray = description.getJSONArray("questions");
            if (questionsJArray.length() == 0) {
                throw new BadSurveyException("Survey has no questions.");
            }
            final List<Question> questionsList = new ArrayList<Question>(questionsJArray.length());
            for (int i = 0; i < questionsJArray.length(); i++) {
                final JSONObject q = questionsJArray.getJSONObject(i);
                questionsList.add(new Question(q));
            }
            mQuestions = Collections.unmodifiableList(questionsList);
        } catch (final JSONException e) {
            throw new BadSurveyException("Survey JSON was unexpected or bad", e);
        }
    }

    public String toJSON() {
        return mDescription.toString();
    }

    public int getId() {
        return mId;
    }

    public int getCollectionId() {
        return mCollectionId;
    }

    public List<Question> getQuestions() {
        return mQuestions;
    }

    public enum QuestionType {
        UNKNOWN,
        MULTIPLE_CHOICE,
        TEXT;

        @Override
        public String toString() {
            if (MULTIPLE_CHOICE == this) {
                return "multiple_choice";
            }
            if (TEXT == this) {
                return "text";
            }
            return "*unknown_type*";
        }
    };

    public class Question {
        private Question(JSONObject question) throws JSONException, BadSurveyException {
            mQuestionId = question.getInt("id");
            mQuestionType = question.getString("type").intern();
            mPrompt = question.getString("prompt");

            List<String> choicesList = Collections.<String>emptyList();
            if (question.has("extra_data")) {
                final JSONObject extraData = question.getJSONObject("extra_data");
                if (extraData.has("$choices")) {
                    final JSONArray choices = extraData.getJSONArray("$choices");
                    choicesList = new ArrayList<String>(choices.length());
                    for (int i = 0; i < choices.length(); i++) {
                        choicesList.add(choices.getString(i));
                    }
                }
            }
            mChoices = Collections.unmodifiableList(choicesList);
            if (getType() == QuestionType.MULTIPLE_CHOICE && mChoices.size() == 0) {
                throw new BadSurveyException("Question is multiple choice but has no answers:" + question.toString());
            }
        }

        public int getId() {
            return mQuestionId;
        }

        public String getPrompt() {
            return mPrompt;
        }

        public List<String> getChoices() {
            return mChoices;
        }

        public QuestionType getType() {
            if (QuestionType.MULTIPLE_CHOICE.toString().equals(mQuestionType)) {
                return QuestionType.MULTIPLE_CHOICE;
            }
            if (QuestionType.TEXT.toString().equals(mQuestionType)) {
                return QuestionType.TEXT;
            }
            return QuestionType.UNKNOWN;
        }

        private final int mQuestionId;
        private final String mQuestionType;
        private final String mPrompt;
        private final List<String> mChoices;
    }

    private final JSONObject mDescription;
    private final int mId;
    private final int mCollectionId;
    private final List<Question> mQuestions;
}
