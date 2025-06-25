# GitHub Copilot Instructions Guide

This directory contains instructions that enhance GitHub Copilot's understanding of the Mixpanel Android SDK codebase.

## Structure

### Core Instructions (`copilot-instructions.md`)
The main file loaded by Copilot for every coding session. Contains:
- Critical SDK principles (never crash, thread-safe, minimal deps)
- Essential code patterns (visibility, error handling, threading)
- Architecture rules (public API, data flow)
- Testing approach (instrumented only)

Kept under 500 lines to fit in Copilot's context window alongside your actual code.

### Specialized Instructions (`instructions/`)
Focused guidance for specific tasks:
- **code-generation.instructions.md** - Patterns for new code
- **test-generation.instructions.md** - Test structure and patterns
- **code-review.instructions.md** - Review checklist and anti-patterns

### Prompt Templates (`prompts/`)
Reusable templates for common tasks:
- **new-feature.prompt.md** - Implementing new SDK features
- **refactor-to-pattern.prompt.md** - Updating code to follow patterns
- **debug-issue.prompt.md** - Systematic debugging approach

## How Copilot Uses These

1. **Automatic Loading**: `copilot-instructions.md` loads automatically
2. **Context Awareness**: Copilot references patterns during code completion
3. **Pattern Matching**: Suggests code following established conventions
4. **Error Prevention**: Warns about anti-patterns before they're written

## Key Patterns Enforced

### Never Crash Philosophy
```java
// Copilot will suggest this pattern:
try {
    riskyOperation();
} catch (Exception e) {
    MPLog.e(LOGTAG, "Operation failed", e);
}
```

### Thread Safety
```java
// Copilot knows to use:
private final Object mLock = new Object();
synchronized (mLock) {
    // critical section
}
```

### API Design
```java
// Copilot suggests overloads:
public void track(String event) {
    track(event, null);
}
```

## Using Prompt Templates

1. Copy the appropriate template
2. Fill in the placeholders
3. Paste into Copilot chat
4. Review generated code against patterns

Example:
```
"I need to add user segmentation to the Mixpanel Android SDK that groups users by behavior.

Requirements:
- Public API method: `mixpanel.setSegment(String segmentName)`
- Should store data in people properties
- Configuration option: `segmentationEnabled` (default: true)
- Must be thread-safe and handle offline mode

Please generate:
1. Public API method in MixpanelAPI.java
..."
```

## Maintenance

When patterns change:
1. Update core instructions first
2. Update specialized instructions
3. Test with sample code generation
4. Verify Copilot suggestions match new patterns

## Benefits

- **Consistency**: All Copilot suggestions follow SDK patterns
- **Speed**: No need to explain patterns repeatedly
- **Quality**: Reduces review cycles by getting code right initially
- **Onboarding**: New developers get pattern-aware suggestions immediately