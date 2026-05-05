# Changelog

## v1.3.0 (2026-04-27)

### Features
- Add event-triggered recording support by @rahul-mixpanel ([#194](https://github.com/mixpanel/mixpanel-android-session-replay/pull/194))

### Bug Fixes
- fix: touch targets for window offsets (such as camera notch) by @tylerjroach ([#197](https://github.com/mixpanel/mixpanel-android-session-replay/pull/197))

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.2.0...v1.3.0)

---

## v1.2.0 (2026-04-09)

### What's Changed
- fix: schedule screenshot on view removal by @tylerjroach in [#176](https://github.com/mixpanel/mixpanel-android-session-replay/pull/176)
- fix: correct touch coordinate mapping by removing device frame by @tylerjroach in [#175](https://github.com/mixpanel/mixpanel-android-session-replay/pull/175)
- fix: Dialog window sizing and touch tracking by @tylerjroach in [#177](https://github.com/mixpanel/mixpanel-android-session-replay/pull/177)
- feat: debug mask overlay windowed by @tylerjroach in [#179](https://github.com/mixpanel/mixpanel-android-session-replay/pull/179)
- chore: Update a large number of dated Android dependencies by @tylerjroach in [#186](https://github.com/mixpanel/mixpanel-android-session-replay/pull/186)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.1.2...v1.2.0)

---

## v1.1.2 (2026-03-25)

### What's Changed
- updated platform to $os in setting api query param by @ketanmixpanel in [#160](https://github.com/mixpanel/mixpanel-android-session-replay/pull/160)
- chore: add 30-day dependabot cooldown by @austinpray-mixpanel in [#163](https://github.com/mixpanel/mixpanel-android-session-replay/pull/163)
- chore: pin GitHub Actions to commit SHAs by @austinpray-mixpanel in [#161](https://github.com/mixpanel/mixpanel-android-session-replay/pull/161)
- fix: capitalize $os query parameter value to Android by @rahul-mixpanel in [#173](https://github.com/mixpanel/mixpanel-android-session-replay/pull/173)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.1.1...v1.1.2)

---

## v1.1.1 (2026-03-09)

### What's Changed
- fix: flushInterval documentation should state seconds, not milliseconds by @tylerjroach in [#156](https://github.com/mixpanel/mixpanel-android-session-replay/pull/156)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.1.0...v1.1.1)

---

## v1.1.0 (2026-03-06)

### What's Changed
- fix: optimize node checks to skip invisible Compose views from masking by @rahul-mixpanel in [#151](https://github.com/mixpanel/mixpanel-android-session-replay/pull/151)
- feat: Add remote configuration for Session Replay by @rahul-mixpanel in [#153](https://github.com/mixpanel/mixpanel-android-session-replay/pull/153)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.0.6...v1.1.0)

---

## v1.0.6 (2026-02-06)

### What's Changed
- chore: Ktlint Convention Plugin configuration by @tylerjroach in [#128](https://github.com/mixpanel/mixpanel-android-session-replay/pull/128)
- fix: Don't mask GONE or INVISIBLE XML Views by @tylerjroach in [#122](https://github.com/mixpanel/mixpanel-android-session-replay/pull/122)
- chore: Publish to Maven Central through GitHub Actions by @tylerjroach in [#129](https://github.com/mixpanel/mixpanel-android-session-replay/pull/129)
- chore: Publish snapshots to MavenCentral on each merge to main by @tylerjroach in [#130](https://github.com/mixpanel/mixpanel-android-session-replay/pull/130)
- fix: Use SupervisorJob and ensure proper resource cleanup in MPTracker by @rahul-mixpanel in [#103](https://github.com/mixpanel/mixpanel-android-session-replay/pull/103)
- fix: bump flush dequeue to 50 to prevent from falling behind by @tylerjroach in [#138](https://github.com/mixpanel/mixpanel-android-session-replay/pull/138)
- fix: flush events when stopRecording is called by @tylerjroach in [#139](https://github.com/mixpanel/mixpanel-android-session-replay/pull/139)
- chore: add binary validation to prevent accidental visibility leaks by @tylerjroach in [#142](https://github.com/mixpanel/mixpanel-android-session-replay/pull/142)
- fix: detect fragment animations by @tylerjroach in [#137](https://github.com/mixpanel/mixpanel-android-session-replay/pull/137)
- fix: Mask improvements when Semantics are merged by @tylerjroach in [#123](https://github.com/mixpanel/mixpanel-android-session-replay/pull/123)
- fix: prevent flush interval overflow & disable w/ interval = 0 by @tylerjroach in [#144](https://github.com/mixpanel/mixpanel-android-session-replay/pull/144)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.0.5...v1.0.6)

---

## v1.0.5 (2026-01-13)

### What's Changed
- fix: Delay initialization (including networking) until app foreground by @tylerjroach in [#107](https://github.com/mixpanel/mixpanel-android-session-replay/pull/107)
- feat: Use PixelCopy by default instead of Canvas.save by @tylerjroach in [#108](https://github.com/mixpanel/mixpanel-android-session-replay/pull/108)
- fix: Mask fix for ModalBottomSheet and likely other similar views by @tylerjroach in [#114](https://github.com/mixpanel/mixpanel-android-session-replay/pull/114)
- fix: Add mask coordinate validation to prevent masking leaks by @tylerjroach in [#115](https://github.com/mixpanel/mixpanel-android-session-replay/pull/115)
- feat: Add KtLint GitHub Action for PR checks by @rahul-mixpanel in [#109](https://github.com/mixpanel/mixpanel-android-session-replay/pull/109)
- fix: discard capture during active transition by @tylerjroach in [#118](https://github.com/mixpanel/mixpanel-android-session-replay/pull/118)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.0.4...v1.0.5)

---

## v1.0.4 (2025-11-21)

### What's Changed
- refactor: Screenshot capture to use coroutines instead of Handler by @rahul-mixpanel in [#80](https://github.com/mixpanel/mixpanel-android-session-replay/pull/80)
- feat(performance): Use RGB_565 for screenshot bitmaps by @rahul-mixpanel in [#87](https://github.com/mixpanel/mixpanel-android-session-replay/pull/87)
- refactor: ScreenRecorder to capture screenshots at 1x density (logical pixels) by @rahul-mixpanel in [#90](https://github.com/mixpanel/mixpanel-android-session-replay/pull/90)
- feat(perf): Add BitmapPool for session replay screenshots by @rahul-mixpanel in [#88](https://github.com/mixpanel/mixpanel-android-session-replay/pull/88)
- fix(masking): Fix safe container logic and always mask input fields by @rahul-mixpanel in [#92](https://github.com/mixpanel/mixpanel-android-session-replay/pull/92)
- perf: processSubviews method optimisations by @rahul-mixpanel in [#96](https://github.com/mixpanel/mixpanel-android-session-replay/pull/96)
- perf: Optimise SemanticNode checks to detect Images by @rahul-mixpanel in [#97](https://github.com/mixpanel/mixpanel-android-session-replay/pull/97)
- perf: Optimize production builds by conditionalizing screenshot size logging by @Copilot in [#99](https://github.com/mixpanel/mixpanel-android-session-replay/pull/99)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.0.3...v1.0.4)

---

## v1.0.3 (2025-10-08)

### What's Changed
- feat: Detect Jetpack Compose Image views for auto-masking by @rahul-mixpanel in [#74](https://github.com/mixpanel/mixpanel-android-session-replay/pull/74)
- feat: Add isRecording() method to MPSessionReplayInstance by @rahul-mixpanel in [#75](https://github.com/mixpanel/mixpanel-android-session-replay/pull/75)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.0.2...v1.0.3)

---

## v1.0.2 (2025-09-24)

### What's Changed
- fix: Synchronize Compose semantic node access on main thread by @rahul-mixpanel in [#66](https://github.com/mixpanel/mixpanel-android-session-replay/pull/66)
- refactor: Improve view-to-bitmap conversion with PixelCopy fallback by @rahul-mixpanel in [#71](https://github.com/mixpanel/mixpanel-android-session-replay/pull/71)
- feat: Add library name and version override support by @jaredmixpanel in [#72](https://github.com/mixpanel/mixpanel-android-session-replay/pull/72)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v1.0.0...v1.0.2)

---

## v1.0.0 (2025-07-24)

### What's Changed
- chore: Apply ktlint formatting to all .kt files by @jaredmixpanel in [#42](https://github.com/mixpanel/mixpanel-android-session-replay/pull/42)
- fix: Initialize FlushService with wifiOnly from config by @rahul-mixpanel in [#43](https://github.com/mixpanel/mixpanel-android-session-replay/pull/43)
- refactor: Replace custom rootViewCache implementation with Curtains.rootViews by @rahul-mixpanel in [#44](https://github.com/mixpanel/mixpanel-android-session-replay/pull/44)
- feat: Enable auto start recording by @rahul-mixpanel in [#52](https://github.com/mixpanel/mixpanel-android-session-replay/pull/52)
- refactor: Standardize package name and reorganize directory structure by @rahul-mixpanel in [#53](https://github.com/mixpanel/mixpanel-android-session-replay/pull/53)
- fix: Only capture main screenshots, migrate flush service to lifecycle scope coroutine by @jaredmixpanel in [#54](https://github.com/mixpanel/mixpanel-android-session-replay/pull/54)
- feat: Add public identify and getDistinctId methods by @jaredmixpanel in [#55](https://github.com/mixpanel/mixpanel-android-session-replay/pull/55)
- feat: Make flushInterval configurable in MPSessionReplayConfig by @rahul-mixpanel in [#57](https://github.com/mixpanel/mixpanel-android-session-replay/pull/57)
- feat: Add enableLogging flag to MPSessionReplayConfig by @rahul-mixpanel in [#56](https://github.com/mixpanel/mixpanel-android-session-replay/pull/56)
- feat: Add support for SDK reinitialisation by @rahul-mixpanel in [#61](https://github.com/mixpanel/mixpanel-android-session-replay/pull/61)
- feat: Add SettingsService for remote configuration by @jaredmixpanel in [#58](https://github.com/mixpanel/mixpanel-android-session-replay/pull/58)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v0.1.0...v1.0.0)

---

## v0.1.0 (2025-05-27)

### What's Changed
- feat: Add safe view support for XML views by @rahul-mixpanel in [#35](https://github.com/mixpanel/mixpanel-android-session-replay/pull/35)
- test: Added tests for safe view handling by @rahul-mixpanel in [#36](https://github.com/mixpanel/mixpanel-android-session-replay/pull/36)
- refactor: Group extension functions in a dedicated package by @rahul-mixpanel in [#37](https://github.com/mixpanel/mixpanel-android-session-replay/pull/37)
- refactor: Renamed AutoMaskedView enums to match iOS SDK naming by @rahul-mixpanel in [#38](https://github.com/mixpanel/mixpanel-android-session-replay/pull/38)
- fix: MPSessionReplayConfig: Configure JSON decoder to ignore unknown keys by @rahul-mixpanel in [#39](https://github.com/mixpanel/mixpanel-android-session-replay/pull/39)
- feat: Handle SDK's late initialisation use case by @rahul-mixpanel in [#40](https://github.com/mixpanel/mixpanel-android-session-replay/pull/40)

[Full Changelog](https://github.com/mixpanel/mixpanel-android-session-replay/compare/v0.0.8...v0.1.0)

---

## v0.0.8 (2025-03-19)

Initial pre-release version.

---

## v0.0.7 (2025-03-18)

Initial pre-release version.

---

## v0.0.1 (2024-09-23)

Initial pre-release version.
