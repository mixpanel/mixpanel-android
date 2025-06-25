# Testing Strategy for Mixpanel Android SDK

## Testing Philosophy

The SDK uses **instrumented tests only** - all tests require an Android device or emulator. This ensures tests validate real Android behavior rather than mocked implementations.

## Test Structure

### Location
```
src/androidTest/java/com/mixpanel/android/
├── mpmetrics/           # Core SDK tests
│   ├── MixpanelBasicTest.java
│   ├── MPDbAdapterTest.java
│   ├── PersistentIdentityTest.java
│   ├── DecideCheckerTest.java
│   ├── HttpServiceTest.java
│   └── [other test classes]
└── util/                # Utility tests
    └── HttpServiceTest.java
```

### Test Setup Pattern

```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MixpanelFeatureTest {
    private Context mContext;
    private MixpanelAPI mMixpanel;
    private BlockingQueue<String> mMessages;
    private Future<SharedPreferences> mReferrerPreferences;
    
    @Before
    public void setUp() throws Exception {
        // Clear all preferences
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Clear existing preferences
        SharedPreferences prefs = mContext.getSharedPreferences(
            "com.mixpanel.android.mpmetrics.MixpanelAPI_" + TOKEN, 
            Context.MODE_PRIVATE
        );
        prefs.edit().clear().commit();
        
        // Create test instance with mocked messages
        mMessages = new LinkedBlockingQueue<>();
        mMixpanel = TestUtils.createMixpanelApiWithMockedMessages(
            mContext, mMessages, false
        );
        
        // Set up other test dependencies
        mReferrerPreferences = new TestUtils.EmptyPreferences(mContext);
    }
    
    @After
    public void tearDown() {
        // Clean up resources
        mMixpanel.flush();
        
        // Clear test data
        cleanUpDatabase();
    }
}
```

## Core Testing Patterns

### 1. Message Queue Testing

**Using BlockingQueue for Async Verification:**
```java
@Test
public void testTrackEvent() throws Exception {
    // Track an event
    mMixpanel.track("Test Event", null);
    
    // Wait for message with timeout
    String message = mMessages.poll(2, TimeUnit.SECONDS);
    assertNotNull("Expected message within timeout", message);
    
    // Verify message content
    JSONObject jsonMessage = new JSONObject(message);
    assertEquals("Test Event", jsonMessage.getString("event"));
    assertTrue(jsonMessage.has("properties"));
}

@Test
public void testBatchedEvents() throws Exception {
    // Track multiple events
    for (int i = 0; i < 10; i++) {
        mMixpanel.track("Event " + i, null);
    }
    
    // Verify all events queued
    List<String> messages = new ArrayList<>();
    mMessages.drainTo(messages, 10);
    assertEquals(10, messages.size());
}
```

### 2. Database Testing

**Testing Persistence:**
```java
@Test
public void testEventPersistence() throws Exception {
    MPDbAdapter db = new MPDbAdapter(mContext);
    
    // Add events to database
    JSONObject event = new JSONObject();
    event.put("event", "Test");
    event.put("properties", new JSONObject());
    
    int count = db.addJSON(event, "token", MPDbAdapter.Table.EVENTS);
    assertEquals(1, count);
    
    // Retrieve events
    String[] events = db.generateDataString(MPDbAdapter.Table.EVENTS, "token", 10);
    assertNotNull(events);
    assertEquals(1, events.length);
    
    // Clean up
    db.cleanupEvents(System.currentTimeMillis() + 1000, MPDbAdapter.Table.EVENTS);
}
```

### 3. Configuration Testing

**Testing Configuration Options:**
```java
@Test
public void testCustomConfiguration() throws Exception {
    // Create with custom options
    MixpanelOptions options = new MixpanelOptions.Builder()
        .flushInterval(30000)
        .trackAutomaticEvents(false)
        .build();
    
    MixpanelAPI customMixpanel = MixpanelAPI.getInstance(
        mContext, "custom_token", options
    );
    
    // Verify configuration applied
    MPConfig config = customMixpanel.getConfig();
    assertEquals(30000, config.getFlushInterval());
    assertFalse(config.getAutoShowMixpanelUpdates());
}
```

### 4. Thread Safety Testing

**Concurrent Access Tests:**
```java
@Test
public void testConcurrentTracking() throws Exception {
    final int THREAD_COUNT = 10;
    final int EVENTS_PER_THREAD = 100;
    final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
    
    // Launch concurrent threads
    for (int i = 0; i < THREAD_COUNT; i++) {
        final int threadId = i;
        new Thread(() -> {
            for (int j = 0; j < EVENTS_PER_THREAD; j++) {
                mMixpanel.track("Thread " + threadId + " Event " + j, null);
            }
            latch.countDown();
        }).start();
    }
    
    // Wait for completion
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    
    // Verify all events recorded
    Thread.sleep(1000); // Let queue settle
    List<String> allMessages = new ArrayList<>();
    mMessages.drainTo(allMessages);
    assertEquals(THREAD_COUNT * EVENTS_PER_THREAD, allMessages.size());
}
```

### 5. Identity Management Testing

```java
@Test
public void testIdentityTransition() throws Exception {
    // Start anonymous
    String anonId = mMixpanel.getDistinctId();
    assertNotNull(anonId);
    
    // Track anonymous event
    mMixpanel.track("Anonymous Event", null);
    
    // Identify user
    mMixpanel.identify("user123");
    assertEquals("user123", mMixpanel.getDistinctId());
    
    // Create alias
    mMixpanel.alias("user123", anonId);
    
    // Verify alias message
    String aliasMessage = mMessages.poll(2, TimeUnit.SECONDS);
    JSONObject alias = new JSONObject(aliasMessage);
    assertEquals("$create_alias", alias.getString("event"));
}
```

### 6. Error Handling Testing

```java
@Test
public void testInvalidInput() throws Exception {
    // Test null event name
    mMixpanel.track(null, null);
    
    // Test empty event name
    mMixpanel.track("", null);
    
    // Test invalid JSON
    JSONObject badProps = new JSONObject();
    badProps.put("bad_key", Double.NaN);
    mMixpanel.track("Event", badProps);
    
    // SDK should handle gracefully - no crashes
    // May or may not queue messages depending on validation
}
```

### 7. Network Testing

```java
@Test
public void testOfflineBehavior() throws Exception {
    // Simulate offline
    TestUtils.setOfflineMode(mContext, true);
    
    // Track events while offline
    for (int i = 0; i < 5; i++) {
        mMixpanel.track("Offline Event " + i, null);
    }
    
    // Verify events stored in database
    MPDbAdapter db = new MPDbAdapter(mContext);
    String[] stored = db.generateDataString(
        MPDbAdapter.Table.EVENTS, TOKEN, 10
    );
    assertTrue(stored.length >= 5);
    
    // Simulate online
    TestUtils.setOfflineMode(mContext, false);
    
    // Flush should send stored events
    mMixpanel.flush();
}
```

## Test Utilities

### TestUtils Helper Methods

```java
public class TestUtils {
    // Create test instance with mocked message queue
    public static MixpanelAPI createMixpanelApiWithMockedMessages(
            Context context, BlockingQueue<String> messages) {
        // Returns MixpanelAPI with AnalyticsMessages that adds to queue
    }
    
    // Empty preferences for testing
    public static class EmptyPreferences implements SharedPreferences {
        // Returns defaults for all methods
    }
    
    // Set offline mode
    public static void setOfflineMode(Context context, boolean offline) {
        OfflineMode.set(context, offline);
    }
}
```

## Running Tests

### Command Line

```bash
# Run all tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest

# Run with coverage
./gradlew createDebugCoverageReport
```

### Android Studio

1. Right-click test class/method
2. Select "Run 'TestName'"
3. Choose connected device/emulator

## Test Coverage Areas

### Must Test
- [ ] Event tracking
- [ ] People properties
- [ ] Identity management
- [ ] Persistence (SQLite)
- [ ] Configuration options
- [ ] Thread safety
- [ ] Offline behavior
- [ ] Error handling

### Should Test
- [ ] Feature flags
- [ ] Groups
- [ ] Automatic events
- [ ] Session tracking
- [ ] Super properties
- [ ] Flush behavior

### Nice to Test
- [ ] Performance metrics
- [ ] Memory usage
- [ ] Battery impact
- [ ] Network efficiency

## Best Practices

1. **Use Real Android Components** - No mocking of Android framework
2. **Test on Multiple API Levels** - Minimum (21) and latest
3. **Verify Async Operations** - Use BlockingQueue pattern
4. **Clean State Between Tests** - Clear preferences and database
5. **Test Error Cases** - SDK should never crash
6. **Use Timeouts** - Prevent hanging tests
7. **Test Concurrency** - Multi-threaded access patterns

## Common Test Issues

### Flaky Tests
- Add delays for async operations
- Use proper synchronization
- Clear state thoroughly

### Slow Tests
- Use @SmallTest for quick tests
- Batch database operations
- Minimize network calls

### Device-Specific Failures
- Test on various screen sizes
- Test different Android versions
- Handle device capabilities

This comprehensive testing strategy ensures the SDK remains reliable across all Android devices and use cases.