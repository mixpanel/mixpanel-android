# mixpanel-android-common

##### _April 27, 2026_ - [common-v1.0.1](https://github.com/mixpanel/mixpanel-android/releases/tag/common-v1.0.1)

Internal shared utilities for Mixpanel's Android SDKs. **Not intended for direct use in application code** — public types are annotated `@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)` and may change without notice. Application code should depend on `mixpanel-android` (or another Mixpanel SDK) instead, which pulls this artifact in transitively.

Source previously lived in [mixpanel/mixpanel-android-common](https://github.com/mixpanel/mixpanel-android-common); the `:common` module now ships from this repository.

## Components

### MixpanelEventBridge

`eventbridge/MixpanelEventBridge.kt` — process-wide event dispatcher used by the main SDK to broadcast tracked events to other Mixpanel libraries via Kotlin `SharedFlow`.

### JsonLogic

`jsonlogic/` — Kotlin implementation of a restricted JsonLogic subset, scoped to the operator set needed for Event Trigger rule alignment across Mixpanel SDKs.

Supported operators: strict equality (`===`, `!==`), numeric comparison (`<`, `<=`, `>`, `>=`), logic (`and`, `or`), membership (`in`), and data access (`var`).

## License

See [LICENSE](../LICENSE) for details.
