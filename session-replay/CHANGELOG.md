# Changelog

## v1.3.0 (2026-04-27)

### Features
- Add event-triggered recording support

### Bug Fixes
- fix: touch targets for window offsets (such as camera notch)

---

## v1.2.0 (2026-04-09)

### What's Changed
- fix: schedule screenshot on view removal
- fix: correct touch coordinate mapping by removing device frame
- fix: Dialog window sizing and touch tracking
- feat: debug mask overlay windowed
- chore: Update a large number of dated Android dependencies

---

## v1.1.2 (2026-03-25)

### What's Changed
- updated platform to $os in setting api query param
- chore: add 30-day dependabot cooldown
- chore: pin GitHub Actions to commit SHAs
- fix: capitalize $os query parameter value to Android

---

## v1.1.1 (2026-03-09)

### What's Changed
- fix: flushInterval documentation should state seconds, not milliseconds

---

## v1.1.0 (2026-03-06)

### What's Changed
- fix: optimize node checks to skip invisible Compose views from masking
- feat: Add remote configuration for Session Replay

---

## v1.0.6 (2026-02-06)

### What's Changed
- chore: Ktlint Convention Plugin configuration
- fix: Don't mask GONE or INVISIBLE XML Views
- chore: Publish to Maven Central through GitHub Actions
- chore: Publish snapshots to MavenCentral on each merge to main
- fix: Use SupervisorJob and ensure proper resource cleanup in MPTracker
- fix: bump flush dequeue to 50 to prevent from falling behind
- fix: flush events when stopRecording is called
- chore: add binary validation to prevent accidental visibility leaks
- fix: detect fragment animations
- fix: Mask improvements when Semantics are merged
- fix: prevent flush interval overflow & disable w/ interval = 0

---

## v1.0.5 (2026-01-13)

### What's Changed
- fix: Delay initialization (including networking) until app foreground
- feat: Use PixelCopy by default instead of Canvas.save
- fix: Mask fix for ModalBottomSheet and likely other similar views
- fix: Add mask coordinate validation to prevent masking leaks
- feat: Add KtLint GitHub Action for PR checks
- fix: discard capture during active transition

---

## v1.0.4 (2025-11-21)

### What's Changed
- refactor: Screenshot capture to use coroutines instead of Handler
- feat(performance): Use RGB_565 for screenshot bitmaps
- refactor: ScreenRecorder to capture screenshots at 1x density (logical pixels)
- feat(perf): Add BitmapPool for session replay screenshots
- fix(masking): Fix safe container logic and always mask input fields
- perf: processSubviews method optimisations
- perf: Optimise SemanticNode checks to detect Images
- perf: Optimize production builds by conditionalizing screenshot size logging

---

## v1.0.3 (2025-10-08)

### What's Changed
- feat: Detect Jetpack Compose Image views for auto-masking
- feat: Add isRecording() method to MPSessionReplayInstance

---

## v1.0.2 (2025-09-24)

### What's Changed
- fix: Synchronize Compose semantic node access on main thread
- refactor: Improve view-to-bitmap conversion with PixelCopy fallback
- feat: Add library name and version override support

---

## v1.0.0 (2025-07-24)

### What's Changed
- chore: Apply ktlint formatting to all .kt files
- fix: Initialize FlushService with wifiOnly from config
- refactor: Replace custom rootViewCache implementation with Curtains.rootViews
- feat: Enable auto start recording
- refactor: Standardize package name and reorganize directory structure
- fix: Only capture main screenshots, migrate flush service to lifecycle scope coroutine
- feat: Add public identify and getDistinctId methods
- feat: Make flushInterval configurable in MPSessionReplayConfig
- feat: Add enableLogging flag to MPSessionReplayConfig
- feat: Add support for SDK reinitialisation
- feat: Add SettingsService for remote configuration

---

## v0.1.0 (2025-05-27)

### What's Changed
- feat: Add safe view support for XML views
- test: Added tests for safe view handling
- refactor: Group extension functions in a dedicated package
- refactor: Renamed AutoMaskedView enums to match iOS SDK naming
- fix: MPSessionReplayConfig: Configure JSON decoder to ignore unknown keys
- feat: Handle SDK's late initialisation use case

---

## v0.0.8 (2025-03-19)

Initial pre-release version.

---

## v0.0.7 (2025-03-18)

Initial pre-release version.

---

## v0.0.1 (2024-09-23)

Initial pre-release version.
