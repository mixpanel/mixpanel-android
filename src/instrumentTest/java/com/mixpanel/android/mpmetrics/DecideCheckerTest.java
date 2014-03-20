package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.test.AndroidTestCase;

import java.io.UnsupportedEncodingException;
import java.util.List;

public class DecideCheckerTest extends AndroidTestCase {

    @Override
    public void setUp() {
        mDecideChecker = new DecideChecker(getContext(), MPConfig.getInstance(getContext()));
        mPoster = new MockPoster();
        mDecideUpdates1 = new DecideUpdates("TOKEN 1", "DISTINCT ID 1", null);
        mDecideUpdates2 = new DecideUpdates("TOKEN 2", "DISTINCT ID 2", null);
        mDecideUpdates3 = new DecideUpdates("TOKEN 3", "DISTINCT ID 3", null);

    }

    public void testReadEmptyLists() {
        mDecideChecker.addDecideCheck(mDecideUpdates1);

        mPoster.response = bytes("{}");
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        mPoster.response = bytes("{\"surveys\":[], \"notifications\":[]}");
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());
    }

    public void testReadSurvey1() {
        mDecideChecker.addDecideCheck(mDecideUpdates1);

        mPoster.response = bytes(
            "{\"surveys\":[ {" +
                    "   \"id\":291," +
                    "   \"questions\":[" +
                    "       {\"id\":275,\"type\":\"multiple_choice\",\"extra_data\":{\"$choices\":[\"Option 1\",\"Option 2\"]},\"prompt\":\"Multiple Choice Prompt\"}," +
                    "       {\"id\":277,\"type\":\"text\",\"extra_data\":{},\"prompt\":\"Text Field Prompt\"}]," +
                    "   \"collections\":[{\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\",\"id\":141}]}" +
                    "]}"
        );

        mDecideChecker.runDecideChecks(mPoster);
        final Survey found = mDecideUpdates1.popSurvey();
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        assertEquals(found.getId(), 291);
        assertEquals(found.getCollectionId(), 141);

        List<Survey.Question> questions = found.getQuestions();
        assertEquals(questions.size(), 2);

        Survey.Question mcQuestion = questions.get(0);
        assertEquals(mcQuestion.getId(), 275);
        assertEquals(mcQuestion.getPrompt(), "Multiple Choice Prompt");
        assertEquals(mcQuestion.getType(), Survey.QuestionType.MULTIPLE_CHOICE);
        List<String> mcChoices = mcQuestion.getChoices();
        assertEquals(mcChoices.size(), 2);
        assertEquals(mcChoices.get(0), "Option 1");
        assertEquals(mcChoices.get(1), "Option 2");

        Survey.Question textQuestion = questions.get(1);
        assertEquals(textQuestion.getId(), 277);
        assertEquals(textQuestion.getPrompt(), "Text Field Prompt");
        assertEquals(textQuestion.getType(), Survey.QuestionType.TEXT);
        List<String> textChoices = textQuestion.getChoices();
        assertEquals(textChoices.size(), 0);
    }

    public void testReadSurvey2() {
        mDecideChecker.addDecideCheck(mDecideUpdates1);
        mPoster.response = bytes(
                "{\"surveys\":[{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}],\"id\":299,\"questions\":[{\"prompt\":\"PROMPT1\",\"extra_data\":{\"$choices\":[\"Answer1,1\",\"Answer1,2\",\"Answer1,3\"]},\"type\":\"multiple_choice\",\"id\":287},{\"prompt\":\"How has the demo affected you?\",\"extra_data\":{\"$choices\":[\"I laughed, I cried, it was better than \\\"Cats\\\"\",\"I want to see it again, and again, and again.\"]},\"type\":\"multiple_choice\",\"id\":289}]}]}"
        );

        mDecideChecker.runDecideChecks(mPoster);
        final Survey found = mDecideUpdates1.popSurvey();
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        assertEquals(found.getId(), 299);
        assertEquals(found.getCollectionId(), 151);

        List<Survey.Question> questions = found.getQuestions();
        assertEquals(questions.size(), 2);

        Survey.Question mcQuestion = questions.get(0);
        assertEquals(mcQuestion.getId(), 287);
        assertEquals(mcQuestion.getPrompt(), "PROMPT1");
        assertEquals(mcQuestion.getType(), Survey.QuestionType.MULTIPLE_CHOICE);
        List<String> mcChoices = mcQuestion.getChoices();
        assertEquals(mcChoices.size(), 3);
        assertEquals(mcChoices.get(0), "Answer1,1");
        assertEquals(mcChoices.get(1), "Answer1,2");
        assertEquals(mcChoices.get(2), "Answer1,3");
    }

    public void testBadDecideResponses() {
        mDecideChecker.addDecideCheck(mDecideUpdates1);

        // Corrupted or crazy responses.
        mPoster.response = bytes("{ WONT PARSE");
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        // Valid JSON but bad (no name)
        mPoster.response = bytes(
                "{\"surveys\":{\"id\":3,\"collections\":[{\"id\": 9}],\"questions\":[{\"id\":12,\"type\":\"text\",\"prompt\":\"P\",\"extra_data\":{}}]}"
        );
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        // Just pure craziness
        mPoster.response = bytes("null");
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        // Valid JSON that isn't relevant
        mPoster.response = bytes("{\"Ziggy Startdust and the Spiders from Mars\":\"The Best Ever Number One\"}");
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        // Valid survey with no questions
        mPoster.response = bytes(
                "{\"surveys\":[{\"collections\":[{\"id\":151,\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\"}],\"id\":299,\"questions\":[]}]}"
        );
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());

        // Valid survey with a question with no choices
        mPoster.response = bytes(
                "{\"surveys\":[ {" +
                        "   \"id\":291," +
                        "   \"questions\":[" +
                        "       {\"id\":275,\"type\":\"multiple_choice\",\"extra_data\":{\"$choices\":[]},\"prompt\":\"Multiple Choice Prompt\"}," +
                        "   \"collections\":[{\"selector\":\"\\\"@mixpanel\\\" in properties[\\\"$email\\\"]\",\"id\":141}]}" +
                        "]}"
        );
        mDecideChecker.runDecideChecks(mPoster);
        assertNull(mDecideUpdates1.popSurvey());
        assertNull(mDecideUpdates1.popNotification());
    }

    public void testDecideResponses() {
        {
            final String nonsense = "I AM NONSENSE";
            final DecideChecker.Result parseNonsense = DecideChecker.parseDecideResponse(nonsense);
            assertTrue(parseNonsense.notifications.isEmpty());
            assertTrue(parseNonsense.surveys.isEmpty());
        }

        {
            final String allNull = "null";
            final DecideChecker.Result parseAllNull = DecideChecker.parseDecideResponse(allNull);
            assertTrue(parseAllNull.notifications.isEmpty());
            assertTrue(parseAllNull.surveys.isEmpty());
        }

        {
            final String elementsNull = "{\"surveys\": null, \"notifications\": null}";
            final DecideChecker.Result parseElementsNull = DecideChecker.parseDecideResponse(elementsNull);
            assertTrue(parseElementsNull.notifications.isEmpty());
            assertTrue(parseElementsNull.surveys.isEmpty());
        }

        {
            final String elementsEmpty = "{\"surveys\": [], \"notifications\": []}";
            final DecideChecker.Result parseElementsEmpty = DecideChecker.parseDecideResponse(elementsEmpty);
            assertTrue(parseElementsEmpty.notifications.isEmpty());
            assertTrue(parseElementsEmpty.surveys.isEmpty());
        }

        {
            final String notificationOnly = "{\"notifications\":[{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"takeover\"}]}";
            final DecideChecker.Result parseNotificationOnly = DecideChecker.parseDecideResponse(notificationOnly);
            assertEquals(parseNotificationOnly.notifications.size(), 1);

            final InAppNotification parsed = parseNotificationOnly.notifications.get(0);
            assertEquals(parsed.getBody(), "Hook me up, yo!");
            assertEquals(parsed.getTitle(), "Tranya?");
            assertEquals(parsed.getMessageId(), 1781);
            assertEquals(parsed.getImageUrl(), "http://mixpanel.com/Balok.jpg");
            assertEquals(parsed.getCallToAction(), "I'm Down!");
            assertEquals(parsed.getCallToActionUrl(), "http://www.mixpanel.com");
            assertEquals(parsed.getId(), 119911);
            assertEquals(parsed.getType(), InAppNotification.Type.TAKEOVER);

            assertTrue(parseNotificationOnly.surveys.isEmpty());
        }

        {
            final String surveyOnly = "{\"notifications\":[],\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"All users 2\"},{\"id\":3329,\"name\":\"all 2\"}],\"id\":397,\"questions\":[{\"prompt\":\"prompt text\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"Demo survey\"}]}";
            final DecideChecker.Result parseSurveyOnly = DecideChecker.parseDecideResponse(surveyOnly);
            assertTrue(parseSurveyOnly.notifications.isEmpty());

            assertEquals(parseSurveyOnly.surveys.size(), 1);
            final Survey parsed = parseSurveyOnly.surveys.get(0);
            assertEquals(parsed.getId(), 397);
            assertTrue(parsed.getCollectionId() == 3319 || parsed.getCollectionId() == 3329);
            final List<Survey.Question> questions = parsed.getQuestions();
            assertEquals(questions.size(), 1);
            final Survey.Question question = questions.get(0);
            assertEquals(question.getType(), Survey.QuestionType.TEXT);
            assertEquals(question.getPrompt(), "prompt text");
            assertEquals(question.getId(), 457);
            assertTrue(question.getChoices().isEmpty());

        }

        {
            final String both = "{\"notifications\":[{\"body\":\"Hook me up, yo!\",\"title\":\"Tranya?\",\"message_id\":1781,\"image_url\":\"http://mixpanel.com/Balok.jpg\",\"cta\":\"I'm Down!\",\"cta_url\":\"http://www.mixpanel.com\",\"id\":119911,\"type\":\"mini\"}],\"surveys\":[{\"collections\":[{\"id\":3319,\"name\":\"All users 2\"},{\"id\":3329,\"name\":\"all 2\"}],\"id\":397,\"questions\":[{\"prompt\":\"prompt text\",\"extra_data\":{},\"type\":\"text\",\"id\":457}],\"name\":\"Demo survey\"}]}";
            final DecideChecker.Result parseBoth = DecideChecker.parseDecideResponse(both);

            final InAppNotification parsedNotification = parseBoth.notifications.get(0);
            assertEquals(parsedNotification.getBody(), "Hook me up, yo!");
            assertEquals(parsedNotification.getTitle(), "Tranya?");
            assertEquals(parsedNotification.getMessageId(), 1781);
            assertEquals(parsedNotification.getImageUrl(), "http://mixpanel.com/Balok.jpg");
            assertEquals(parsedNotification.getCallToAction(), "I'm Down!");
            assertEquals(parsedNotification.getCallToActionUrl(), "http://www.mixpanel.com");
            assertEquals(parsedNotification.getId(), 119911);
            assertEquals(parsedNotification.getType(), InAppNotification.Type.MINI);


            assertEquals(parseBoth.surveys.size(), 1);
            final Survey parsedSurvey = parseBoth.surveys.get(0);
            assertEquals(parsedSurvey.getId(), 397);
            assertTrue(parsedSurvey.getCollectionId() == 3319 || parsedSurvey.getCollectionId() == 3329);
            final List<Survey.Question> questions = parsedSurvey.getQuestions();
            assertEquals(questions.size(), 1);
            final Survey.Question question = questions.get(0);
            assertEquals(question.getType(), Survey.QuestionType.TEXT);
            assertEquals(question.getPrompt(), "prompt text");
            assertEquals(question.getId(), 457);
            assertTrue(question.getChoices().isEmpty());
        }
    }

    private byte[] bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("This is not an android device, or a compatible java. WHO ARE YOU?");
        }
    }

    private class MockPoster extends ServerMessage {
        @Override
        public Result get(Context context, String endpointUrl, String fallbackUrl) {
            this.endpointUrl = endpointUrl;
            this.fallbackUrl = fallbackUrl;
            return new Result(Status.SUCCEEDED, response);
        }

        public byte[] response = null;
        public String endpointUrl = null;
        public String fallbackUrl = null;
    }

    private DecideChecker mDecideChecker;
    private MockPoster mPoster;
    private DecideUpdates mDecideUpdates1, mDecideUpdates2, mDecideUpdates3;
}
