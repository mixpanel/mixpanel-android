# CLAUDE.md - Mixpanel Android SDK Tests

This file provides specific guidance for writing and maintaining instrumented tests for the Mixpanel Android SDK.

## Testing Philosophy

The Mixpanel Android SDK uses **instrumented tests exclusively** - no unit tests. This ensures:
- Real device behavior validation
- Actual SQLite database operations
- True async timing verification
- Android framework integration testing

## Test Structure

### Basic Test Setup
```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MixpanelFeatureTest {
    private static final String TEST_TOKEN = "Test Token";
    private static final int TIMEOUT_SECONDS = 5;
    
    private MixpanelAPI mMixpanel;
    private TestUtils mTestUtils;
    
    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mTestUtils = new TestUtils(context);
        mMixpanel = mTestUtils.getCleanMixpanelAPI(TEST_TOKEN);
    }
    
    @After
    public void tearDown() {
        mTestUtils.cleanDatabase();
        mMixpanel.flush();
    }
}
```

## Key Testing Patterns

### 1. BlockingQueue for Async Operations
The most important pattern for testing async SDK behavior:

```java
public class AnalyticsMessagesTest {
    private BlockingQueue<AnalyticsMessageDescription> mMessages;
    
    @Before
    public void setUp() {
        mMessages = new LinkedBlockingQueue<>();
        // Mock the HttpService to capture messages
        HttpService mockService = new HttpService() {
            @Override
            public String performRequest(String endpoint, JSONObject message) {
                mMessages.add(new AnalyticsMessageDescription(endpoint, message));
                return "1\n";
            }
        };
    }
    
    @Test
    public void testEventQueuing() throws InterruptedException {
        mMixpanel.track("Test Event");
        mMixpanel.flush();
        
        // Wait for async processing
        AnalyticsMessageDescription msg = mMessages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull("Event should be sent", msg);
        assertEquals("Test Event", msg.getMessage().getString("event"));
    }
}
```

### 2. Database Testing
Test actual SQLite operations:

```java
@Test
public void testDatabasePersistence() {
    // Add events
    mMixpanel.track("Event 1");
    mMixpanel.track("Event 2");
    
    // Force database write
    mMixpanel.flush();
    
    // Verify database content
    MPDbAdapter db = mTestUtils.getDbAdapter();
    String[] events = db.generateDataString(Table.EVENTS, TEST_TOKEN);
    assertEquals(2, events.length);
}
```

### 3. Thread Safety Testing
Verify concurrent access handling:

```java
@Test
public void testConcurrentAccess() throws InterruptedException {
    final int THREAD_COUNT = 10;
    final CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
    final AtomicInteger successCount = new AtomicInteger(0);
    
    for (int i = 0; i < THREAD_COUNT; i++) {
        final int threadId = i;
        new Thread(() -> {
            try {
                mMixpanel.track("Thread " + threadId);
                mMixpanel.getPeople().set("thread", threadId);
                successCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        }).start();
    }
    
    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(THREAD_COUNT, successCount.get());
}
```

### 4. Error Handling Verification
Ensure SDK never crashes:

```java
@Test
public void testNullHandling() {
    // These should all handle gracefully
    mMixpanel.track(null);
    mMixpanel.track("Event", null);
    mMixpanel.getPeople().set(null, "value");
    mMixpanel.getPeople().set("key", null);
    
    // Verify SDK still functional
    mMixpanel.track("Valid Event");
    mMixpanel.flush();
    // Should complete without crashes
}
```

## Test Utilities

### TestUtils Helper Methods
```java
// Get clean instance for each test
MixpanelAPI api = mTestUtils.getCleanMixpanelAPI(token);

// Clear all data
mTestUtils.cleanDatabase();

// Get direct database access
MPDbAdapter db = mTestUtils.getDbAdapter();

// Wait for async operations
mTestUtils.waitForAsyncQueue();
```

### Custom Assertions
```java
private void assertEventSent(String eventName, BlockingQueue<JSONObject> queue) 
        throws InterruptedException, JSONException {
    JSONObject event = queue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    assertNotNull("Event should be sent within timeout", event);
    assertEquals(eventName, event.getString("event"));
}
```

## Testing Guidelines

### DO Test
- **Real workflows** - How developers actually use the SDK
- **Edge cases** - Null values, empty strings, invalid JSON
- **Concurrency** - Multiple threads accessing same instance
- **Persistence** - Data survives app restart
- **Error recovery** - SDK continues after failures

### DON'T Test
- **Implementation details** - Focus on public API
- **Timing specifics** - Allow reasonable timeouts
- **Mock everything** - Use real components when possible
- **UI interactions** - This is a data SDK

## Common Test Scenarios

### 1. Feature Flag Testing
```java
@Test
public void testFeatureFlagCaching() throws Exception {
    // Setup mock response
    when(mHttpService.fetchFeatureFlags()).thenReturn(mockFlags);
    
    // First fetch
    mMixpanel.isFeatureEnabled("test_flag");
    
    // Verify cached (no second network call)
    verify(mHttpService, times(1)).fetchFeatureFlags();
    
    // Multiple checks use cache
    for (int i = 0; i < 10; i++) {
        mMixpanel.isFeatureEnabled("test_flag");
    }
    verify(mHttpService, times(1)).fetchFeatureFlags();
}
```

### 2. Identity Management
```java
@Test
public void testIdentityTransition() {
    // Start anonymous
    String anonId = mMixpanel.getDistinctId();
    assertNotNull(anonId);
    
    // Identify user
    mMixpanel.identify("user123");
    assertEquals("user123", mMixpanel.getDistinctId());
    
    // Verify alias created
    mMixpanel.flush();
    // Check alias event was sent
}
```

### 3. Automatic Events
```java
@Test
public void testAutomaticEvents() {
    MixpanelOptions options = new MixpanelOptions()
        .setTrackAutomaticEvents(true);
    
    MixpanelAPI api = MixpanelAPI.getInstance(context, TEST_TOKEN, options);
    
    // Simulate app lifecycle
    api.onActivityCreated(mockActivity, null);
    api.onActivityStarted(mockActivity);
    
    // Verify $app_open event
    api.flush();
    // Assert event was tracked
}
```

## Performance Testing

```java
@Test
@LargeTest
public void testBulkEventPerformance() {
    long startTime = System.currentTimeMillis();
    
    // Track many events
    for (int i = 0; i < 1000; i++) {
        mMixpanel.track("Bulk Event " + i);
    }
    
    long trackTime = System.currentTimeMillis() - startTime;
    assertTrue("Tracking should be fast", trackTime < 1000);
    
    // Flush to server
    startTime = System.currentTimeMillis();
    mMixpanel.flush();
    
    long flushTime = System.currentTimeMillis() - startTime;
    assertTrue("Flush should complete reasonably", flushTime < 5000);
}
```

## Test Naming Conventions

- `testFeature_Scenario_ExpectedResult`
- `testErrorHandling_NullInput_DoesNotCrash`
- `testConcurrency_MultipleThreads_AllEventsTracked`
- `testPerformance_BulkOperations_CompletesQuickly`

## Running Tests

```bash
# Run all tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest --tests="*.MixpanelBasicTest"

# Run with coverage
./gradlew createDebugCoverageReport
```

## Writing New Tests

1. **Identify the scenario** - What are you testing?
2. **Setup clean state** - Use TestUtils for isolation
3. **Execute the operation** - Call the API under test
4. **Wait for async** - Use BlockingQueue or latches
5. **Assert the outcome** - Verify expected behavior
6. **Clean up** - Reset for next test

Remember: Tests are documentation. Write them clearly so others understand the SDK's expected behavior.