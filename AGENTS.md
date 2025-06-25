# AGENTS.md - Mixpanel Android SDK

This file enables AI agents to work autonomously on the Mixpanel Android SDK codebase. It synthesizes patterns from local AI systems into comprehensive cloud execution instructions.

## Project Overview

**Mixpanel Android SDK** - A production analytics library used by thousands of Android applications worldwide. The SDK prioritizes reliability, thread safety, and backward compatibility above all else.

**Critical Context Files:**
- `CLAUDE.md` - Core patterns and conventions
- `.claude/context/discovered-patterns.md` - Detailed coding standards
- `.claude/context/architecture/system-design.md` - System architecture
- `.cursor/rules/` - Behavioral enforcement rules
- `.github/copilot-instructions.md` - Persistent coding guidance

## Environment Setup

Before beginning any task:

```bash
# Verify environment
./gradlew --version  # Should show Gradle with JDK 17
adb devices          # Should show connected device/emulator for tests

# Clean build to ensure fresh state
./gradlew clean

# Run quick verification
./gradlew build
```

## Core Principles (MANDATORY)

1. **NEVER CRASH THE HOST APP** - Wrap all operations in try-catch, fail silently with logging
2. **THREAD SAFETY** - All public APIs must handle concurrent access correctly
3. **NO EXTERNAL DEPENDENCIES** - Use only Android SDK and Java standard library
4. **BACKWARDS COMPATIBILITY** - Never break existing public APIs
5. **DEFENSIVE PROGRAMMING** - Check nulls, validate inputs, handle edge cases

## Task Categories

### ✅ GOOD for Delegation

**Test Coverage Tasks:**
- "Add comprehensive tests for feature flags functionality"
- "Create thread safety tests for all public APIs"
- "Add instrumented tests for offline mode behavior"

**Systematic Refactoring:**
- "Update all database operations to use new transaction pattern"
- "Add defensive null checks to all public methods"
- "Implement proper resource cleanup in all try-finally blocks"

**Documentation:**
- "Generate JavaDoc for all public API methods"
- "Create examples for each People API operation"
- "Document all configuration options with examples"

**Code Quality:**
- "Add MPLog statements to trace event flow"
- "Implement lazy initialization for expensive objects"
- "Ensure all constants follow CAPS_WITH_UNDERSCORES"

### ❌ POOR for Delegation

- UI/UX decisions (this is a library)
- Performance optimization without metrics
- Architecture changes
- API design decisions
- Breaking changes

## Code Patterns Reference

### Class Structure
```java
package com.mixpanel.android.mpmetrics;

// Package-private by default
class InternalHelper {
    private static final String LOGTAG = "MixpanelAPI.Helper";
    
    // Immutable fields
    private final Context mContext;
    private final Object mLock = new Object();
    
    InternalHelper(Context context) {
        mContext = context.getApplicationContext(); // Prevent leaks
    }
}
```

### Error Handling
```java
// ALWAYS wrap operations
try {
    riskyOperation();
} catch (Exception e) {
    MPLog.e(LOGTAG, "Operation failed", e);
    // Continue gracefully - NEVER re-throw
}
```

### Threading
```java
// Queue to background thread
Message msg = Message.obtain();
msg.what = ENQUEUE_EVENTS;
msg.obj = new EventDescription(event, properties, token);
mMessages.enqueueMessage(msg);
```

### Testing Pattern
```java
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FeatureTest {
    private BlockingQueue<String> mMessages;
    
    @Test
    public void testAsync() throws Exception {
        mMixpanel.track("Event");
        String message = mMessages.poll(2, TimeUnit.SECONDS);
        assertNotNull("Should receive message", message);
    }
}
```

## Validation Requirements

Before submitting any PR, you MUST:

### 1. Build Validation
```bash
./gradlew clean build
# Must pass with no errors or warnings
```

### 2. Test Validation
```bash
# Run all instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
# All tests must pass
```

### 3. Code Style Check
```bash
./gradlew lint
# Review any warnings in build/reports/lint-results.html
```

### 4. Manual Verification
```bash
# Test with demo app
./gradlew :mixpaneldemo:installDebug
# Manually verify the feature works
```

### 5. PR Checklist
- [ ] No new external dependencies added
- [ ] All new code has try-catch blocks
- [ ] Thread safety verified for concurrent access
- [ ] Instrumented tests added for new features
- [ ] No breaking changes to public APIs
- [ ] JavaDoc added for public methods
- [ ] Follows 'm' prefix convention for fields
- [ ] Uses application context (not Activity)

## Architecture Boundaries

**Data Flow:** MixpanelAPI → AnalyticsMessages → MPDbAdapter → HttpService

**NEVER:**
- Skip layers (e.g., MixpanelAPI calling HttpService directly)
- Create new public classes (keep internals package-private)
- Add Service or ContentProvider components
- Use reflection or dynamic class loading
- Create unit tests (instrumented only)

## Common Pitfalls

1. **Activity Context** - Always use application context
2. **Synchronization** - Use dedicated lock objects, not 'this'
3. **Resource Cleanup** - Always close Cursors in finally blocks
4. **Testing** - Must use BlockingQueue for async verification
5. **Configuration** - Respect hierarchy: Runtime > Manifest > Default

## PR Preparation

Your PR description should include:

```markdown
## Summary
- Added comprehensive tests for [feature]
- Improved thread safety in [component]
- Fixed resource leak in [class]

## Changes
- Added X new test cases
- Updated Y classes to follow patterns
- Cleaned up Z resources properly

## Testing
- [x] All tests pass locally
- [x] Demo app tested manually
- [x] No memory leaks detected
- [x] Thread safety verified

## Validation
- `./gradlew build` - ✅ Passed
- `./gradlew connectedAndroidTest` - ✅ All tests pass
- `./gradlew lint` - ✅ No new warnings
```

## Success Metrics

Your task is successful when:
1. All existing tests still pass
2. New tests provide meaningful coverage
3. Code follows established patterns exactly
4. No external dependencies introduced
5. PR can be merged without modification

## Quick Reference

- **Visibility**: Package-private by default
- **Fields**: Private final with 'm' prefix
- **Errors**: Catch all, log, continue
- **Threading**: HandlerThread + Messages
- **Testing**: Instrumented only with BlockingQueue
- **Context**: Application context always
- **Database**: Try-finally for Cursors
- **API**: Overload for convenience, null for optional

Remember: This SDK is critical infrastructure. Reliability > Features. When in doubt, check the patterns in `.claude/context/discovered-patterns.md`.