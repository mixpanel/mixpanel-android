# AGENTS.md - Instrumented Tests

This directory contains Android instrumented tests that validate the SDK's behavior on real devices.

## Test Philosophy

**CRITICAL**: This SDK uses instrumented tests ONLY. No unit tests. All tests must run on an Android device or emulator to validate real behavior.

## Test Structure

Every test class MUST follow this pattern:

```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ComponentTest {
    private Context mContext;
    private MixpanelAPI mMixpanel;
    private BlockingQueue<String> mMessages;
    
    @Before
    public void setUp() throws Exception {
        // 1. Get instrumentation context (NOT test context)
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // 2. Clear all preferences
        SharedPreferences prefs = mContext.getSharedPreferences(
            "com.mixpanel.android.mpmetrics.MixpanelAPI_" + TEST_TOKEN,
            Context.MODE_PRIVATE
        );
        prefs.edit().clear().commit();
        
        // 3. Create test instance with message queue
        mMessages = new LinkedBlockingQueue<>();
        mMixpanel = TestUtils.createMixpanelApiWithMockedMessages(
            mContext, mMessages
        );
    }
    
    @After
    public void tearDown() {
        mMixpanel.flush();
        // Clean up any test data
    }
}
```

## Async Testing Pattern

**MANDATORY**: Use BlockingQueue for all async verification:

```java
@Test
public void testAsyncBehavior() throws Exception {
    // 1. Perform operation
    mMixpanel.track("TestEvent");
    
    // 2. Wait with timeout (2 seconds typical)
    String message = mMessages.poll(2, TimeUnit.SECONDS);
    
    // 3. Always provide assertion messages
    assertNotNull("Event message should be queued within timeout", message);
    
    // 4. Verify content
    JSONObject event = new JSONObject(message);
    assertEquals("Event name should match", "TestEvent", event.getString("event"));
}
```

## Common Test Scenarios

### Basic Functionality Test
```java
@Test
public void testBasicFunctionality() throws Exception {
    // Test the happy path
    mMixpanel.track("Event", null);
    
    String message = mMessages.poll(2, TimeUnit.SECONDS);
    assertNotNull("Event should be queued", message);
}
```

### Error Handling Test
```java
@Test
public void testErrorHandling() throws Exception {
    // Should not crash
    mMixpanel.track(null, null);
    mMixpanel.track("", null);
    
    // May or may not queue messages
    assertTrue("App should not crash", true);
}
```

### Thread Safety Test
```java
@Test
public void testThreadSafety() throws Exception {
    final int THREAD_COUNT = 10;
    final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
    
    // Launch concurrent operations
    for (int i = 0; i < THREAD_COUNT; i++) {
        final int id = i;
        new Thread(() -> {
            mMixpanel.track("Event " + id);
            latch.countDown();
        }).start();
    }
    
    // Wait for completion
    assertTrue("All threads should complete", 
        latch.await(5, TimeUnit.SECONDS));
    
    // Verify all events recorded
    Thread.sleep(500); // Let queue settle
    List<String> messages = new ArrayList<>();
    mMessages.drainTo(messages);
    assertEquals("All events should be queued", THREAD_COUNT, messages.size());
}
```

### Database Test
```java
@Test
public void testDatabaseOperations() throws Exception {
    MPDbAdapter db = new MPDbAdapter(mContext);
    
    // Test with real SQLite
    JSONObject data = new JSONObject();
    data.put("key", "value");
    
    int count = db.addJSON(data, "token", MPDbAdapter.Table.EVENTS);
    assertEquals("Should add one record", 1, count);
    
    // Clean up
    db.cleanupEvents(System.currentTimeMillis() + 1000, MPDbAdapter.Table.EVENTS);
}
```

## Running Tests

```bash
# Run all tests in this directory
./gradlew connectedAndroidTest

# Run specific test class (use full class name)
./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest

# Run specific test method
./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest#testEventQueuing

# Run with coverage
./gradlew createDebugCoverageReport
```

## Test Guidelines

1. **Real Components Only** - No mocking Android framework
2. **Descriptive Assertions** - Always explain what should happen
3. **Reasonable Timeouts** - 2-5 seconds for async operations
4. **Clean State** - Clear preferences and data in setUp
5. **Resource Cleanup** - Always clean up in tearDown/finally

## Adding New Tests

When adding tests for new features:

1. Create test class following naming: `[Feature]Test.java`
2. Test success cases, error cases, and edge cases
3. Verify thread safety if applicable
4. Test offline behavior if relevant
5. Include performance/stress tests for critical paths

## DO NOT

- Create unit tests (src/test/) - instrumented only
- Mock Android components - use real implementations
- Use arbitrary Thread.sleep() - use BlockingQueue.poll()
- Skip timeout handling - always use timeouts
- Leave resources open - clean up everything

Remember: These tests are the safety net for a critical SDK. Comprehensive tests prevent production issues.