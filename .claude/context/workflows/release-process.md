# Release Process for Mixpanel Android SDK

## Release Overview

The Mixpanel Android SDK follows semantic versioning (X.Y.Z) and publishes to Maven Central via Sonatype OSSRH.

## Pre-Release Checklist

### 1. Code Preparation
- [ ] All features merged to master
- [ ] All tests passing
- [ ] No ProGuard warnings
- [ ] Demo app builds and runs
- [ ] API compatibility verified

### 2. Version Update

**File:** `gradle.properties`
```properties
VERSION_NAME=8.3.0
```

**Semantic Versioning Rules:**
- **Major (X.0.0)**: Breaking API changes
- **Minor (X.Y.0)**: New features, backwards compatible
- **Patch (X.Y.Z)**: Bug fixes only

### 3. Update CHANGELOG.md

```markdown
## Version 8.3.0 (January 15, 2024)

### Features
- Added feature flags refresh callback
- New group analytics API

### Improvements
- Reduced memory usage by 15%
- Optimized batch processing

### Fixes
- Fixed race condition in session tracking
- Resolved ProGuard configuration issue

### Breaking Changes (if major version)
- Removed deprecated methods
```

## Build Verification

### 1. Clean Build
```bash
# Clean everything
./gradlew clean

# Build library
./gradlew build

# Verify no warnings
```

### 2. Run All Tests
```bash
# Unit tests (if any)
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Generate test report
./gradlew createDebugCoverageReport
```

### 3. Lint Verification
```bash
# Run lint checks
./gradlew lint

# Review lint report at:
# build/reports/lint-results.html
```

### 4. ProGuard Testing
```bash
# Build release variant
./gradlew assembleRelease

# Test ProGuard rules
./gradlew testReleaseUnitTest
```

### 5. Documentation Generation
```bash
# Generate JavaDocs
./gradlew androidJavadocs

# Review at build/docs/javadoc/
```

## Demo App Verification

### 1. Update Demo Dependencies
**File:** `mixpaneldemo/build.gradle`
```gradle
dependencies {
    implementation project(':mixpanel-android')
    // Or for testing published version:
    // implementation 'com.mixpanel.android:mixpanel-android:8.3.0'
}
```

### 2. Test Demo Features
```bash
# Build demo app
./gradlew :mixpaneldemo:assembleDebug

# Install on device
./gradlew :mixpaneldemo:installDebug
```

**Manual Testing Checklist:**
- [ ] Track events
- [ ] Update people properties
- [ ] Test feature flags
- [ ] Verify flush behavior
- [ ] Check offline mode
- [ ] Test configuration options

## Publishing Process

### 1. Configure Credentials

**File:** `~/.gradle/gradle.properties`
```properties
NEXUS_USERNAME=your-sonatype-username
NEXUS_PASSWORD=your-sonatype-password
signing.keyId=YOUR-KEY-ID
signing.password=your-key-password
signing.secretKeyRingFile=/path/to/secring.gpg
```

### 2. Run Release Script

```bash
# Make script executable
chmod +x release.sh

# Run release
./release.sh 8.3.0
```

**What the script does:**
1. Updates version in gradle.properties
2. Builds release artifacts
3. Signs artifacts with GPG
4. Uploads to Sonatype staging
5. Creates git tag

### 3. Manual Publishing Steps

If not using release script:

```bash
# Build and sign artifacts
./gradlew clean build

# Upload to Sonatype
./gradlew uploadArchives

# Create git tag
git tag v8.3.0
git push origin v8.3.0
```

### 4. Sonatype Release

1. Log into [Sonatype OSSRH](https://oss.sonatype.org/)
2. Go to "Staging Repositories"
3. Find your repository (com.mixpanel-XXXX)
4. Click "Close" and verify
5. Click "Release" to publish

### 5. Verify Publication

**Maven Central (may take 2-4 hours):**
```bash
# Check availability
curl https://repo1.maven.org/maven2/com/mixpanel/android/mixpanel-android/8.3.0/

# Test in new project
dependencies {
    implementation 'com.mixpanel.android:mixpanel-android:8.3.0'
}
```

## Post-Release Tasks

### 1. GitHub Release

```bash
# Create release on GitHub
gh release create v8.3.0 \
  --title "Version 8.3.0" \
  --notes-file CHANGELOG.md \
  --target master
```

### 2. Update Documentation

- [ ] Update README.md version references
- [ ] Update integration guides
- [ ] Update API documentation
- [ ] Notify documentation team

### 3. Communication

**Internal:**
- [ ] Update internal version tracking
- [ ] Notify customer success team
- [ ] Update support documentation

**External:**
- [ ] Blog post for major features
- [ ] Update Stack Overflow answers
- [ ] Tweet from @mixpanel

### 4. Version Bump

**Prepare for next version:**
```properties
# gradle.properties
VERSION_NAME=8.3.1-SNAPSHOT
```

## Rollback Procedure

If critical issues found:

### 1. Immediate Actions
```bash
# Delete tag
git tag -d v8.3.0
git push origin :refs/tags/v8.3.0

# Revert commits if needed
git revert <commit-hash>
```

### 2. Sonatype Actions
- Contact Sonatype support for removal
- Cannot remove from Maven Central mirrors

### 3. Mitigation
- Release patch version immediately
- Communicate known issues
- Update documentation

## Release Artifacts

### Published Files
```
mixpanel-android-8.3.0.aar       # Main library
mixpanel-android-8.3.0.pom       # Maven metadata
mixpanel-android-8.3.0-sources.jar   # Source code
mixpanel-android-8.3.0-javadoc.jar   # Documentation
```

### Signatures
Each artifact includes:
- `.asc` - GPG signature
- `.md5` - MD5 checksum
- `.sha1` - SHA1 checksum

## Integration Testing

### Test New Release
```gradle
// Create test project
android {
    compileSdkVersion 34
    
    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
    }
}

dependencies {
    implementation 'com.mixpanel.android:mixpanel-android:8.3.0'
}
```

### Compatibility Matrix
Test with:
- [ ] Min SDK version (21)
- [ ] Target SDK version (34)
- [ ] Latest Android Studio
- [ ] ProGuard enabled
- [ ] R8 enabled
- [ ] MultiDex scenarios

## Common Issues

### Build Failures
- Check signing configuration
- Verify network connectivity
- Ensure clean build

### Upload Failures
- Check Sonatype credentials
- Verify repository permissions
- Check artifact signatures

### Publication Delays
- Maven Central sync: 2-4 hours
- Mirror propagation: up to 24 hours
- Use Sonatype directly if urgent

This comprehensive release process ensures high-quality, reliable releases of the Mixpanel Android SDK to thousands of applications worldwide.