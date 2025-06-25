# Cursor Rules for Mixpanel Android SDK

This directory contains MDC (Multi-file Development Context) rules that guide AI assistants when working with the Mixpanel Android SDK codebase.

## Structure

### Always Rules (`always/`)
Universal patterns that apply to all SDK code:
- **core-conventions.mdc** - Java coding standards, visibility, thread safety
- **architecture-principles.mdc** - System design, data flow, component boundaries
- **code-quality.mdc** - Performance, memory management, error handling

### Component Rules (`components/`)
Specific patterns for major SDK components:
- **api-design.mdc** - Public API surface design patterns
- **database-layer.mdc** - SQLite operations and persistence
- **threading-model.mdc** - HandlerThread and message passing

### Feature Rules (`features/`)
Domain-specific patterns:
- **testing-patterns.mdc** - Instrumented test patterns
- **android-patterns.mdc** - Android SDK best practices

### Workflow Rules (`workflows/`)
Multi-step procedures:
- **adding-features.mdc** - Complete feature development workflow
- **debugging-issues.mdc** - Systematic debugging approach

## Key Principles

1. **Never Crash** - The SDK must never crash the host application
2. **Thread Safety** - All public APIs must be thread-safe
3. **Defensive Programming** - Validate inputs, handle nulls gracefully
4. **No Unit Tests** - Only instrumented tests for real device validation
5. **Minimal Dependencies** - Avoid external libraries

## Usage

These rules are automatically loaded by Cursor when editing matching files. They provide:
- Real-time code suggestions
- Pattern enforcement
- Architecture guidance
- Best practice reminders

## Maintenance

Run `/user:rules-sync` to update rules when patterns change or new conventions are adopted.