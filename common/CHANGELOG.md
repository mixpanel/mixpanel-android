# Changelog

## [common-v1.1.0](https://github.com/mixpanel/mixpanel-android/tree/common-v1.1.0) (2026-09-01)

### Features
* Add semver and date custom operator support [1001](https://github.com/mixpanel/mixpanel-android/pull/1001)

[Full Changelog](https://github.com/mixpanel/mixpanel-android/compare/common-v1.0.1...common-v1.1.0)

## [common-v1.0.1](https://github.com/mixpanel/mixpanel-android/tree/common-v1.0.1) (2026-04-27)

Initial release of `mixpanel-android-common`, providing shared utilities for Mixpanel Android SDKs:

- `MixpanelEventBridge` — event dispatcher that broadcasts Mixpanel analytics events to subscribers using Kotlin `SharedFlow`
- `Json Logic Evaluator` — JSON-based rule engine for evaluating conditional logic at runtime
