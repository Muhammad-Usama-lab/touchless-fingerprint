# SDK Build and Distribution Guide

## Building the AAR File

### Step 1: Build Release AAR

```bash
cd /home/notebook/Desktop/Muhammad/JAVA/touchless-fingerprint
./gradlew :BiometricsSdk:assembleRelease
```

### Step 2: Locate the AAR

The compiled AAR will be at:
```
BiometricsSdk/build/outputs/aar/BiometricsSdk-release.aar
```

### Step 3: Rename for Distribution (Optional)

```bash
cp BiometricsSdk/build/outputs/aar/BiometricsSdk-release.aar ./RMSTBiometricsSdk-1.0.0.aar
```

---

## What's Protected in the AAR

| Protected | Description |
|-----------|-------------|
| Source Code | Compiled to bytecode (.class files) |
| Implementation Details | Internal classes obfuscated |
| API URLs | Embedded in obfuscated code |
| Business Logic | Hand detection, image processing logic hidden |

| Visible to Client | Description |
|-------------------|-------------|
| Public API | `Biometrics`, `BiometricsResult`, `ProcessedFile` classes |
| Resources | Layouts, strings (can be obfuscated further if needed) |
| Manifest | Required activities and permissions |

---

## Distribution Options

### Option 1: Direct AAR File Sharing (Simplest)

Send the client:
1. `RMSTBiometricsSdk-1.0.0.aar` - The compiled SDK
2. `SDK_INTEGRATION_GUIDE.md` - Integration documentation

Client adds to their project:
```
app/libs/RMSTBiometricsSdk-1.0.0.aar
```

### Option 2: Private Maven Repository (Professional)

Host on:
- **GitHub Packages** - Free with GitHub
- **JitPack** (Private) - Easy setup
- **Artifactory** - Enterprise solution
- **AWS CodeArtifact** - AWS ecosystem

Client adds to `build.gradle`:
```gradle
repositories {
    maven { url "https://your-maven-repo.com/releases" }
}

dependencies {
    implementation 'com.rmst:biometrics-sdk:1.0.0'
}
```

### Option 3: JitPack (Private Repository)

1. Push SDK to private GitHub repo
2. Client adds JitPack:
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.YourOrg:BiometricsSdk:1.0.0'
}
```

---

## Hiding Dependencies

### Current Approach (Recommended)

Dependencies are declared in SDK's `build.gradle` using `implementation` (not `api`), which means:
- Dependencies are **not exposed** transitively
- Client must add required dependencies manually (listed in integration guide)

### Alternative: Fat AAR (Bundle Everything)

Use gradle plugin to bundle all dependencies into single AAR:

```gradle
// In BiometricsSdk/build.gradle
plugins {
    id 'com.github.nicorbet.fat-aar-android' version '1.3.0'
}

dependencies {
    embed 'com.squareup.okhttp3:okhttp:4.10.0'  // Bundled into AAR
    // ... other dependencies
}
```

**Pros:** Single AAR, no external dependencies
**Cons:** Larger file size, potential conflicts with client's dependencies

---

## Files to Send to Client

```
delivery/
├── RMSTBiometricsSdk-1.0.0.aar     # Compiled SDK
├── SDK_INTEGRATION_GUIDE.md         # Integration documentation
└── sample/                          # (Optional) Sample project
    ├── app/
    │   ├── build.gradle
    │   └── src/
    └── build.gradle
```

---

## Versioning

When releasing updates:

1. Update version in `BiometricsSdk/build.gradle`:
```gradle
android {
    defaultConfig {
        versionCode 2
        versionName "1.1.0"
    }
}
```

2. Build new AAR
3. Rename: `RMSTBiometricsSdk-1.1.0.aar`
4. Update documentation with changelog

---

## Security Notes

1. **Server URL**: The API server URL is embedded in the AAR. For production, use HTTPS.

2. **Code Obfuscation**: ProGuard is enabled for release builds, making reverse engineering harder.

3. **License Protection**: Consider adding license key validation in future versions.

4. **Certificate Pinning**: For additional security, implement SSL certificate pinning for API calls.

---

## Quick Commands

```bash
# Build debug AAR
./gradlew :BiometricsSdk:assembleDebug

# Build release AAR (obfuscated)
./gradlew :BiometricsSdk:assembleRelease

# Clean and rebuild
./gradlew clean :BiometricsSdk:assembleRelease

# Check AAR contents
unzip -l BiometricsSdk/build/outputs/aar/BiometricsSdk-release.aar
```
