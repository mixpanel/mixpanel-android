# Release Process for Mixpanel Android SDK

## Overview

Semantic versioning (X.Y.Z) per module, published to Maven Central via the Central Portal.
**Releases are driven entirely by GitHub Actions — there is no local release script.**
(The old `release.sh` / `uploadArchives` / NEXUS-credential flow is gone.)

The source of truth for what can be released is **`.github/modules.json`** — one entry per
module with its `tag_prefix`, `gradle_properties`, `changelog`, `readme`,
`gradle_task_prefix`, and `artifact_id`:

| Module key | Artifact | Tag prefix | Version file |
|---|---|---|---|
| `analytics` | `mixpanel-android` | `v` | `analytics/gradle.properties` |
| `common` | `mixpanel-android-common` | `common-v` | `common/gradle.properties` |
| `openfeature` | `mixpanel-android-openfeature` | `open-feature-v` | `openfeature-provider/gradle.properties` |
| `session-replay` | `mixpanel-android-session-replay` | `session-replay-v` | `session-replay/gradle.properties` |

## Release steps

### 1. Prepare (workflow: `.github/workflows/prepare-release.yml`)

Manually dispatched with inputs `module` (a key from modules.json) and `version`.
It bumps `VERSION_NAME` in the module's `gradle.properties`, updates the module README,
generates the changelog (`.github/scripts/generate-changelog.sh`), and opens a release PR.

### 2. Review & merge the release PR

Standard pre-release sanity: tests green, demo app builds, no new lint/animalsniffer
warnings, API compatibility preserved.

### 3. Publish (workflow: `.github/workflows/release-maven-central.yml`)

Parameterized by `module`; resolves the module config from modules.json, validates the
version against `gradle.properties`, builds and tests, publishes to OSSRH staging
(staging/snapshot URLs are hardcoded in `MavenPublishConventionPlugin.kt` — the
`RELEASE_REPOSITORY_URL` entry in `analytics/gradle.properties` is dead config, read by
nothing), triggers the Central Portal upload with
`publishing_type=user_managed`, and creates a draft GitHub release + tag
(`<tag_prefix><version>`).

Credentials (repo secrets → env, wired in
`build-logic/convention/src/main/kotlin/MavenPublishConventionPlugin.kt`):

- `MAVEN_CENTRAL_USERNAME` → `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_TOKEN` → `MAVEN_CENTRAL_PASSWORD`
- `GPG_PRIVATE_KEY` → `SIGNING_KEY`
- `GPG_PASSPHRASE` → `SIGNING_PASSWORD`

### 4. Release from the Portal

Deployments appear at https://central.sonatype.com/publishing/deployments — finish with a
manual release from the Portal UI, then publish the draft GitHub release.

There is also `.github/workflows/release-snapshot.yml` for snapshot publishing.

## Ordering constraint

`:analytics` consumes `:common` via its published Maven coordinate — release `common`
first when the main SDK needs `:common` changes (see root AGENTS.md, Subprojects).
