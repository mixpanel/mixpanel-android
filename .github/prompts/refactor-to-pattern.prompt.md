# Refactor to Pattern Prompt Template

Use this template when refactoring existing code to follow SDK patterns:

## Prompt Structure

"I need to refactor [CLASS/METHOD] in the Mixpanel Android SDK to follow established patterns.

Current issues:
- [ISSUE_1]
- [ISSUE_2]

The refactored code should:
- Use package-private visibility (not public)
- Handle errors without throwing exceptions
- Use synchronized blocks with dedicated lock objects
- Follow the 'm' prefix convention for member variables
- Clean up resources in finally blocks

Please refactor the code while maintaining backwards compatibility."

## Common Refactoring Scenarios

### Thread Safety
"I need to refactor UserPropertyManager to be thread-safe.

Current issues:
- Direct field access without synchronization
- HashMap used instead of synchronized collection

The refactored code should:
- Use synchronized blocks with mPropertyLock object
- Make fields private final where possible
- Use defensive copies for returned collections"

### Error Handling
"I need to refactor NetworkManager to handle errors gracefully.

Current issues:
- Throws IOException to callers
- No retry logic for failures

The refactored code should:
- Catch all exceptions and log with MPLog
- Return default values on failure
- Implement exponential backoff for retries"

### Resource Management
"I need to refactor DatabaseQueryHelper to properly manage resources.

Current issues:
- Cursor not closed in all code paths
- Database connection leaked on error

The refactored code should:
- Use try-finally blocks for all Cursor operations
- Close database connections even on exception
- Follow the MPDbAdapter patterns"

### API Design
"I need to refactor ConfigurationBuilder to follow builder pattern.

Current issues:
- Setters return void
- No build() method
- Mutable after creation

The refactored code should:
- Return 'this' from all setters for chaining
- Implement build() method
- Make built object immutable"