# Test Generation Instructions - Mixpanel Android SDK

Generate **instrumented tests only** - no unit tests. All tests require an Android device/emulator.

## Test Structure
```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FeatureTest {
    private Context mContext;
    private MixpanelAPI mMixpanel;
    private BlockingQueue<String> mMessages;
    
    @Before
    public void setUp() throws Exception {
        // Get instrumentation context
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Clear preferences
        SharedPreferences prefs = mContext.getSharedPreferences(
            "com.mixpanel.android.mpmetrics.MixpanelAPI_" + TEST_TOKEN,
            Context.MODE_PRIVATE
        );
        prefs.edit().clear().commit();
        
        // Create test instance
        mMessages = new LinkedBlockingQueue<>();
        mMixpanel = TestUtils.createMixpanelApiWithMockedMessages(
            mContext, mMessages
        );
    }
    
    @After
    public void tearDown() {
        // Clean up
        mMixpanel.flush();
    }
}
```

## Async Testing Pattern
```java
@Test
public void testAsyncOperation() throws Exception {
    // Perform operation
    mMixpanel.track("Event", null);
    
    // Wait for result with timeout
    String message = mMessages.poll(2, TimeUnit.SECONDS);
    assertNotNull("Expected message within timeout", message);
    
    // Verify content
    JSONObject json = new JSONObject(message);
    assertEquals("Event", json.getString("event"));
}
```

## Database Testing
```java
@Test
public void testPersistence() throws Exception {
    MPDbAdapter db = new MPDbAdapter(mContext);
    
    // Test with real SQLite
    JSONObject data = new JSONObject();
    data.put("test", "value");
    
    int count = db.addJSON(data, "token", MPDbAdapter.Table.EVENTS);
    assertEquals(1, count);
    
    // Verify retrieval
    String[] events = db.generateDataString(MPDbAdapter.Table.EVENTS, "token", 10);
    assertEquals(1, events.length);
    
    // Clean up
    db.cleanupEvents(System.currentTimeMillis() + 1000, MPDbAdapter.Table.EVENTS);
}
```

## Thread Safety Testing
```java
@Test
public void testConcurrentAccess() throws Exception {
    final int THREAD_COUNT = 10;
    final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
    
    for (int i = 0; i < THREAD_COUNT; i++) {
        final int threadId = i;
        new Thread(() -> {
            mMixpanel.track("Thread " + threadId);
            latch.countDown();
        }).start();
    }
    
    assertTrue("Threads should complete", latch.await(5, TimeUnit.SECONDS));
    
    // Verify all events recorded
    Thread.sleep(500); // Let queue settle
    List<String> messages = new ArrayList<>();
    mMessages.drainTo(messages);
    assertEquals(THREAD_COUNT, messages.size());
}
```

## Error Handling Tests
```java
@Test
public void testInvalidInput() throws Exception {
    // Should not crash
    mMixpanel.track(null, null);
    mMixpanel.track("", null);
    
    // Invalid JSON
    JSONObject props = new JSONObject();
    props.put("invalid", Double.NaN);
    mMixpanel.track("Event", props);
    
    // Verify graceful handling
    assertTrue("App should not crash", true);
}
```

## Test Patterns
- Always use descriptive assertion messages
- Test both success and failure cases
- Verify thread safety with concurrent tests
- Use realistic timeouts (2-5 seconds)
- Clean up resources in @After
- Test with real components, not mocks