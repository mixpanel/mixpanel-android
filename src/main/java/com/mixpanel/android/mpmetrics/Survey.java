package com.mixpanel.android.mpmetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents a Survey, configured in Mixpanel.
 *
 * You only need to work with this class if you call getSurveyIfAvailable() and want to
 * display a custom interface for the survey yourself.
 */
public class Survey implements Parcelable {

    public static Creator<Survey> CREATOR = new Creator<Survey>() {
        @Override
        public Survey createFromParcel(final Parcel source) {
            final String jsonString = source.readString();
            try {
                final JSONObject json = new JSONObject(jsonString);
                return new Survey(json);
            } catch (JSONException e) {
                throw new RuntimeException("Corrupted JSON object written to survey parcel.", e);
            } catch (BadDecideObjectException e) {
                throw new RuntimeException("Unexpected or incomplete object written to survey parcel.", e);
            }
        }

        @Override
        public Survey[] newArray(final int size) {
            return new Survey[size];
        }
    };

    /* package */ Survey(JSONObject description) throws BadDecideObjectException {
        try {
            mDescription = description;
            mId = description.getInt("id");
            final JSONArray collectionsJArray = description.getJSONArray("collections");
            final JSONObject collection0 = collectionsJArray.getJSONObject(0);
            mCollectionId = collection0.getInt("id");

            final JSONArray questionsJArray = description.getJSONArray("questions");
            if (questionsJArray.length() == 0) {
                throw new BadDecideObjectException("Survey has no questions.");
            }
            final List<Question> questionsList = new ArrayList<Question>(questionsJArray.length());
            for (int i = 0; i < questionsJArray.length(); i++) {
                final JSONObject q = questionsJArray.getJSONObject(i);
                questionsList.add(new Question(q));
            }
            mQuestions = Collections.unmodifiableList(questionsList);
        } catch (final JSONException e) {
            throw new BadDecideObjectException("Survey JSON was unexpected or bad", e);
        }
    }

    /* package */ String toJSON() {
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(toJSON());
    }

    public enum QuestionType {
        UNKNOWN {
            @Override
           public String toString() {
                return "*unknown_type*";
            }
        },
        MULTIPLE_CHOICE {
            @Override
            public String toString() {
                return "multiple_choice";
            }
        },
        TEXT {
            @Override
            public String toString() {
                return "text";
            }
        };
    };

    public class Question {
        private Question(JSONObject question) throws JSONException, BadDecideObjectException {
            mQuestionId = question.getInt("id");
            mQuestionType = question.getString("type");
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
                throw new BadDecideObjectException("Question is multiple choice but has no answers:" + question.toString());
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
