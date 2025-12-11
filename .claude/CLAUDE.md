# CLAUDE.md - RMSTBiometrics Project Guide

## Project Overview

**Project Name:** RMSTBiometrics (touchless-fingerprint)
**Type:** Android Application + SDK Library
**Purpose:** Contactless fingerprint biometric capture system using camera-based hand detection
**Current Branch:** 27-oct
**Main Branch:** main

### What This Project Does

This is an Android biometric authentication system that captures touchless fingerprints using AI-powered hand detection. Instead of requiring physical contact with a fingerprint scanner, it uses the device camera to:

1. Detect and track hands in real-time using Google MediaPipe
2. Identify 21 landmark points on each hand
3. Extract individual finger regions (ROI - Region of Interest)
4. Enhance fingerprint images using OpenCV processing
5. Assess image quality automatically
6. Upload biometric data to a remote server

This is particularly useful for hygiene-conscious applications where touchless biometric capture is preferred.

---

## 📌 Current Development Status

> **⚠️ IMPORTANT: Always check SESSION_SUMMARY.md first!**
>
> Before starting any work, read [`SESSION_SUMMARY.md`](../SESSION_SUMMARY.md) for:
> - Latest session progress and bug fixes
> - Current problems and root cause analysis
> - Proposed solutions with code snippets
> - Exact next steps to continue work
>
> Also see [`IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) for the full implementation roadmap.
>
> **Last Updated:** 2025-12-11 Session 3
> **Status:** 3 bugs fixed (negative heights, landmark mismatch, OpenCV crash), 1 remaining (ROI validation failures)

---

## Architecture

### Module Structure

```
touchless-fingerprint/
├── app/                    # Demo Android application
│   ├── src/main/
│   │   ├── java/com/rmst/biometrics/
│   │   │   ├── SplashActivity.kt          # Entry point, checks auth token
│   │   │   ├── LoginActivity.kt           # Token-based authentication
│   │   │   ├── MainActivity.kt            # Navigation host
│   │   │   ├── HandLandmarkerHelper.kt    # MediaPipe wrapper
│   │   │   ├── OverlayView.kt             # Hand visualization & stability
│   │   │   └── fragment/
│   │   │       ├── StartBiometricsFragment.kt  # SDK integration example
│   │   │       ├── CameraFragment.kt           # Hand capture implementation
│   │   │       └── PermissionFragment.kt       # Camera permissions
│   │   └── assets/
│   │       └── hand_landmarker.task       # MediaPipe ML model (7.8 MB)
│   └── build.gradle                       # App dependencies
│
├── BiometricsSdk/          # Reusable Android library
│   ├── src/main/java/com/biometrics/
│   │   ├── Biometrics.kt                  # SDK entry point (public API)
│   │   ├── BiometricsLauncher.kt          # Lifecycle-aware launcher
│   │   ├── BiometricsContract.kt          # Activity result contract
│   │   ├── BiometricsActivity.kt          # SDK host activity
│   │   ├── fragment/
│   │   │   ├── CameraFragment.kt          # Camera capture logic (659 lines)
│   │   │   ├── SdkLauncherFragment.kt     # SDK launcher
│   │   │   └── PermissionFragment.kt      # Permission handling
│   │   ├── utils/
│   │   │   ├── HandLandmarkerHelper.kt    # MediaPipe integration (359 lines)
│   │   │   └── FingerPrintExtractor.kt    # OpenCV processing (329 lines)
│   │   ├── viewmodel/
│   │   │   ├── BiometricsSharedViewModel.kt  # Shared state
│   │   │   └── MainViewModel.kt              # Camera state
│   │   └── model/
│   │       └── BiometricsResult.kt        # Result sealed class
│   └── build.gradle                       # SDK dependencies
│
├── build.gradle            # Root project configuration
├── settings.gradle         # Module configuration
└── README.md              # Basic setup instructions
```

### SDK Architecture Flow

```
User Application
    ↓
Biometrics.register() (Static API)
    ↓
BiometricsLauncher (Manages lifecycle)
    ↓
BiometricsContract (Activity result contract)
    ↓
BiometricsActivity (Host activity)
    ↓
SdkLauncherFragment → CameraFragment
    ↓
HandLandmarkerHelper + FingerprintExtractor
    ↓
BiometricsResult (Success/Error/Cancelled)
```

---

## Technology Stack

### Core Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 1.7.10 | Primary programming language |
| **Java** | - | Legacy code support |
| **Google MediaPipe Tasks Vision** | 0.10.26 | Hand landmark detection (AI/ML) |
| **OpenCV** | 4.5.3.0 | Image processing and enhancement |
| **CameraX** | 1.2.0-alpha02 | Camera management |
| **OkHttp** | 4.10.0 | HTTP client for API communication |
| **Kotlin Coroutines** | - | Asynchronous operations |
| **Android View Binding** | - | Type-safe view access |
| **Navigation Component** | 2.5.3 | Fragment navigation |

### Build Configuration

- **Gradle:** 7.3.0
- **Android Gradle Plugin:** 7.3.0
- **Min SDK:** 24 (Android 7.0 Nougat)
- **Target SDK:** 33
- **Compile SDK:** 33

---

## Key Components

### 1. Hand Detection (MediaPipe)

**File:** `/BiometricsSdk/src/main/java/com/biometrics/utils/HandLandmarkerHelper.kt` (359 lines)

**Purpose:** Wrapper for Google MediaPipe Hand Landmarker API

**Key Features:**
- Detects 21 landmarks per hand
- 3 running modes: IMAGE, VIDEO, LIVE_STREAM
- Configurable confidence thresholds (default: 0.5)
- CPU/GPU delegate support
- Real-time hand tracking from camera

**Landmark Points:**
```
Wrist: 0
Thumb: 1, 2, 3, 4
Index Finger: 5, 6, 7, 8
Middle Finger: 9, 10, 11, 12
Ring Finger: 13, 14, 15, 16
Pinky: 17, 18, 19, 20
```

**Usage:**
```kotlin
val handLandmarkerHelper = HandLandmarkerHelper(
    context = context,
    runningMode = RunningMode.LIVE_STREAM,
    minHandDetectionConfidence = 0.5f,
    minHandTrackingConfidence = 0.5f,
    minHandPresenceConfidence = 0.5f,
    maxNumHands = 1,
    currentDelegate = HandLandmarkerHelper.DELEGATE_CPU
)
```

### 2. Fingerprint Extraction (OpenCV)

**File:** `/BiometricsSdk/src/main/java/com/biometrics/utils/FingerPrintExtractor.kt` (329 lines)

**Purpose:** Extract and enhance individual fingerprint images from hand capture

**Processing Pipeline:**
1. **ROI Calculation** - Calculates region of interest for each finger tip
2. **Validation** - Checks bounds, size (min 50x50px), aspect ratio (0.3-3.0)
3. **Cropping** - Extracts finger regions with 30% padding
4. **Enhancement:**
   - Grayscale conversion
   - Histogram equalization (CLAHE)
   - Gaussian blur (5x5 kernel) for denoising
   - Sharpening filter (unsharp mask)
5. **Quality Assessment** - Scores 0-100 based on:
   - Sharpness (Laplacian variance)
   - Contrast (standard deviation)
   - Brightness (60-180 range optimal)
   - **Minimum threshold: 60**

**Output Model:**
```kotlin
data class FingerprintImage(
    val fingerType: FingerType,
    val bitmap: Bitmap,
    val qualityScore: Float,
    val roi: RectF
)

enum class FingerType {
    THUMB, INDEX, MIDDLE, RING, PINKY
}
```

### 3. Camera Capture

**File:** `/BiometricsSdk/src/main/java/com/biometrics/fragment/CameraFragment.kt` (659 lines)

**Purpose:** Manages camera capture workflow

**Auto-capture Criteria:**
- Hand detected by MediaPipe
- Hand is stable (movement < 0.008 threshold)
- Hand position not near right edge (< 80% screen width)
- User hasn't manually cancelled

**Key Methods:**
- `startCamera()` - Initializes CameraX with back camera
- `updateCameraControlsBasedOnLandmarks()` - Auto-focus on hand center
- `shouldAutoCapture()` - Checks all criteria
- `captureImage()` - Takes photo and processes
- `uploadImageToServer()` - Sends to API

### 4. Stability Detection

**File:** `/app/src/main/java/com/rmst/biometrics/OverlayView.kt`

**Purpose:** Visual feedback and hand stability monitoring

**How It Works:**
- Compares current hand landmarks with previous frame
- Calculates average movement distance
- **Stability threshold: < 0.008 units**
- Shows green checkmark when stable
- Shows hand outline when detected but not stable

### 5. SDK Public API

**File:** `/BiometricsSdk/src/main/java/com/biometrics/Biometrics.kt`

**Purpose:** Main entry point for SDK users

**Registration API:**
```kotlin
// Register with ActivityResultRegistry
val launcher = Biometrics.register(
    activityResultRegistry = registry,
    lifecycleOwner = this
) { result: BiometricsResult ->
    when (result) {
        is BiometricsResult.Success -> {
            val transactionId = result.transactionId
            // Handle success
        }
        is BiometricsResult.Error -> {
            val errorMessage = result.message
            // Handle error
        }
        is BiometricsResult.Cancelled -> {
            // Handle cancellation
        }
    }
}

// Launch biometrics capture
launcher.launch(authToken = "your-token-here")
```

**Alternative (for Fragments):**
```kotlin
val launcher = Biometrics.register(fragment = this) { result ->
    // Handle result
}
```

---

## API Integration

### Demo Server

**Base URL:** `https://demo.rmstservices.com/biometrics/`

### Endpoints

#### 1. Verify Token
```
GET /verifyToken?token=<token>

Response 200:
{
  "valid": true
}

Response 401:
{
  "valid": false,
  "message": "Invalid token"
}
```

#### 2. Register Biometric
```
POST /register
Headers:
  Authorization: Token <auth_token>
  Content-Type: application/json

Body:
{
  "imageData": "<base64_encoded_jpeg>"
}

Response 200:
{
  "transactionId": "unique-transaction-id",
  "success": true
}

Response 400:
{
  "success": false,
  "message": "Error message"
}
```

### Authentication Flow

1. User enters token in LoginActivity
2. Token validated via `/verifyToken` endpoint
3. Valid token stored in SharedPreferences (key: "auth_token")
4. Token passed to SDK during launch
5. SDK includes token in Authorization header during upload

**Token Storage:**
```kotlin
val sharedPref = getSharedPreferences("BiometricsPrefs", Context.MODE_PRIVATE)
sharedPref.edit().putString("auth_token", token).apply()
```

---

## Development Setup

### Prerequisites

1. **Android Studio Dolphin** or later
2. **JDK 11** or later
3. **Android SDK** with:
   - SDK 24 (Android 7.0) minimum
   - SDK 33 (Android 13) target
4. **Physical Android device** (emulator won't work well for camera)
5. **Developer mode enabled** on device

### Initial Setup

1. **Clone the repository:**
   ```bash
   cd /home/notebook/Desktop/Muhammad/JAVA/touchless-fingerprint
   ```

2. **Open in Android Studio:**
   - File → Open → Select project directory
   - Click "Trust Project" when prompted

3. **Gradle Sync:**
   - Android Studio will automatically start Gradle sync
   - If not, click "Sync Now" in the notification bar

4. **Download MediaPipe Model:**
   - Happens automatically during first build via `download_tasks.gradle`
   - Model downloaded from Google Cloud Storage
   - Placed in `/app/src/main/assets/hand_landmarker.task`

5. **Connect Physical Device:**
   - Enable USB debugging
   - Connect via USB
   - Allow debugging permission on device

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK to connected device
./gradlew installDebug

# Run all tests
./gradlew test

# Build SDK library
./gradlew :BiometricsSdk:assembleRelease
```

### Run from Android Studio

1. Select device from dropdown
2. Click green "Run" button (Shift+F10)
3. App installs and launches on device

---

## Important Files Reference

### Configuration Files

| File | Purpose | Key Contents |
|------|---------|--------------|
| `/settings.gradle` | Module configuration | Includes app + BiometricsSdk modules |
| `/build.gradle` | Root project config | Plugin versions, repositories |
| `/app/build.gradle` | App dependencies | MediaPipe, OpenCV, CameraX, OkHttp |
| `/BiometricsSdk/build.gradle` | SDK dependencies | Core library dependencies |
| `/gradle.properties` | Gradle settings | JVM options, Android properties |
| `/local.properties` | Local SDK paths | SDK location (not in git) |

### Manifest Files

| File | Permissions | Key Activities |
|------|-------------|----------------|
| `/app/src/main/AndroidManifest.xml` | CAMERA, INTERNET | SplashActivity (launcher), LoginActivity, MainActivity |
| `/BiometricsSdk/src/main/AndroidManifest.xml` | CAMERA | BiometricsActivity (exported) |

### Asset Files

| File | Size | Purpose |
|------|------|---------|
| `/app/src/main/assets/hand_landmarker.task` | 7.8 MB | MediaPipe ML model |

### Layout Files

Key layouts in `/app/src/main/res/layout/`:
- `activity_splash.xml` - Splash screen
- `activity_login.xml` - Login form
- `activity_main.xml` - Navigation host
- `fragment_camera.xml` - Camera preview + overlay
- `fragment_start_biometrics.xml` - Demo launcher

SDK layouts in `/BiometricsSdk/src/main/res/layout/`:
- `activity_biometrics.xml` - SDK host
- `fragment_sdk_camera.xml` - Camera capture
- `fragment_sdk_permission.xml` - Permission request

---

## Code Patterns and Conventions

### Naming Conventions

- **Activities:** `<Name>Activity.kt` (e.g., `LoginActivity.kt`)
- **Fragments:** `<Name>Fragment.kt` (e.g., `CameraFragment.kt`)
- **ViewModels:** `<Name>ViewModel.kt` (e.g., `MainViewModel.kt`)
- **Helpers:** `<Name>Helper.kt` (e.g., `HandLandmarkerHelper.kt`)
- **Layouts:** `activity_<name>.xml`, `fragment_<name>.xml`

### Architecture Patterns

1. **MVVM (Model-View-ViewModel):**
   - ViewModels for state management
   - LiveData/StateFlow for reactive updates
   - Repository pattern for data access

2. **Single Activity Pattern:**
   - MainActivity hosts Navigation Component
   - Fragments for different screens

3. **Activity Result API:**
   - Modern approach for SDK integration
   - No deprecated `startActivityForResult()`

### Kotlin Conventions

- **Coroutines** for async operations
- **Data classes** for models
- **Sealed classes** for result types
- **Extension functions** for utilities
- **View binding** instead of findViewById

### Error Handling

```kotlin
// Wrap API calls in try-catch
try {
    val response = apiCall()
    BiometricsResult.Success(response.transactionId)
} catch (e: Exception) {
    BiometricsResult.Error(e.message ?: "Unknown error")
}
```

---

## Common Development Tasks

### Task 1: Adjust Hand Detection Sensitivity

**File:** `/BiometricsSdk/src/main/java/com/biometrics/utils/HandLandmarkerHelper.kt`

```kotlin
// Change detection confidence threshold
private val minHandDetectionConfidence: Float = 0.5f // Default
// Lower = more sensitive (more false positives)
// Higher = less sensitive (more false negatives)
```

### Task 2: Modify Stability Threshold

**File:** `/app/src/main/java/com/rmst/biometrics/OverlayView.kt`

```kotlin
// Change stability detection threshold
private fun isHandStable(results: HandLandmarkerHelper.ResultBundle): Boolean {
    val threshold = 0.008f // Default
    // Lower = requires more stability
    // Higher = triggers auto-capture sooner
    return avgDistance < threshold
}
```

### Task 3: Change Fingerprint Quality Threshold

**File:** `/BiometricsSdk/src/main/java/com/biometrics/utils/FingerPrintExtractor.kt`

```kotlin
private fun assessQuality(mat: Mat): Float {
    // Minimum quality score
    val minQuality = 60f // Default
    // Increase to require higher quality
    // Decrease to accept lower quality
}
```

### Task 4: Update API Endpoints

**Files:**
- `/app/src/main/java/com/rmst/biometrics/LoginActivity.kt`
- `/BiometricsSdk/src/main/java/com/biometrics/fragment/CameraFragment.kt`

```kotlin
// Change base URL
private const val BASE_URL = "https://demo.rmstservices.com/biometrics/"
```

### Task 5: Add New Finger to Extraction

**File:** `/BiometricsSdk/src/main/java/com/biometrics/utils/FingerPrintExtractor.kt`

```kotlin
// Add to extractFingerprints() method
private fun extractFingerprints(...) {
    val fingers = listOf(
        FingerType.THUMB to 4,
        FingerType.INDEX to 8,
        FingerType.MIDDLE to 12,
        FingerType.RING to 16,
        FingerType.PINKY to 20
        // Add more finger types here
    )
}
```

### Task 6: Integrate SDK in New App

```kotlin
// 1. Add dependency in app/build.gradle
dependencies {
    implementation project(':BiometricsSdk')
}

// 2. In your Activity or Fragment
class YourActivity : AppCompatActivity() {
    private lateinit var biometricsLauncher: BiometricsLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register SDK
        biometricsLauncher = Biometrics.register(this) { result ->
            when (result) {
                is BiometricsResult.Success -> {
                    Log.d(TAG, "Transaction ID: ${result.transactionId}")
                }
                is BiometricsResult.Error -> {
                    Log.e(TAG, "Error: ${result.message}")
                }
                is BiometricsResult.Cancelled -> {
                    Log.d(TAG, "User cancelled")
                }
            }
        }

        // Launch when ready
        button.setOnClickListener {
            biometricsLauncher.launch("your-auth-token")
        }
    }
}

// 3. Add permissions to AndroidManifest.xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
```

---

## Testing

### Manual Testing Checklist

1. **Permission Flow:**
   - [ ] Camera permission requested on first launch
   - [ ] App handles permission denial gracefully
   - [ ] Permission rationale shown when needed

2. **Authentication:**
   - [ ] Valid token allows app access
   - [ ] Invalid token shows error
   - [ ] Token persists across app restarts

3. **Hand Detection:**
   - [ ] Hand detected and outlined
   - [ ] 21 landmarks visible
   - [ ] Works in various lighting conditions
   - [ ] Handles multiple hands correctly

4. **Stability Detection:**
   - [ ] Green checkmark appears when stable
   - [ ] Auto-capture triggers when stable
   - [ ] Timer resets when hand moves

5. **Image Capture:**
   - [ ] Image captured successfully
   - [ ] Confirmation dialog shown
   - [ ] Option to retake works

6. **Upload:**
   - [ ] Image uploaded successfully
   - [ ] Transaction ID received
   - [ ] Error handling for network issues

### Unit Testing

Currently limited unit tests. To add tests:

```bash
# Create test file
/app/src/test/java/com/rmst/biometrics/ExampleTest.kt

# Run tests
./gradlew test
```

### Device Testing Requirements

- **Minimum:** Android 7.0 device with rear camera
- **Recommended:** Android 10+ with good camera
- **Lighting:** Well-lit environment for best results
- **Hand Position:** Hold hand flat, palm facing camera, 20-30cm away

---

## Troubleshooting

### Issue: MediaPipe Model Not Found

**Symptoms:** App crashes with "hand_landmarker.task not found"

**Solution:**
```bash
# Manually trigger model download
./gradlew :app:downloadTask

# Or download manually from:
# https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
# Place in: /app/src/main/assets/hand_landmarker.task
```

### Issue: OpenCV Native Library Error

**Symptoms:** `UnsatisfiedLinkError` or OpenCV initialization fails

**Solution:**
```kotlin
// Check OpenCV initialization in code
if (!OpenCVLoader.initDebug()) {
    Log.e(TAG, "OpenCV initialization failed")
}

// Verify dependency in build.gradle
implementation 'com.github.QuickBirdEng:opencv-android:4.5.3.0'
```

### Issue: Camera Permission Denied

**Symptoms:** Black screen or permission error

**Solution:**
1. Check AndroidManifest.xml has `<uses-permission android:name="android.permission.CAMERA" />`
2. Request permission at runtime (already handled in PermissionFragment)
3. Go to Settings → Apps → RMSTBiometrics → Permissions → Enable Camera

### Issue: Hand Not Detected

**Symptoms:** No hand outline appears

**Possible Causes:**
1. Poor lighting - increase ambient light
2. Hand too close or too far - maintain 20-30cm distance
3. Hand at extreme angle - keep palm facing camera
4. Low confidence threshold - adjust in HandLandmarkerHelper

**Solution:**
```kotlin
// Lower detection threshold for more sensitivity
minHandDetectionConfidence = 0.3f // Default: 0.5f
```

### Issue: Upload Fails (401 Unauthorized)

**Symptoms:** "Authentication failed" error

**Solution:**
1. Verify token is valid via `/verifyToken` endpoint
2. Check token stored in SharedPreferences
3. Ensure Authorization header format: `Token <your-token>`
4. Check server URL is correct

### Issue: Low Quality Score

**Symptoms:** "Image quality too low" error

**Possible Causes:**
1. Blurry image - keep hand stable
2. Poor lighting - increase light
3. Hand moving during capture

**Solution:**
```kotlin
// Temporarily lower quality threshold for testing
private val minQualityScore = 40f // Default: 60f
```

### Issue: Gradle Build Fails

**Symptoms:** Build errors, dependency resolution fails

**Solution:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew build --refresh-dependencies

# Check Android SDK installed
# Check local.properties points to correct SDK path
```

---

## OpenCV Integration Notes

### OpenCV Initialization

OpenCV is initialized automatically when the library is loaded. The project uses OpenCV Android SDK via JitPack.

**Dependency:**
```gradle
implementation 'com.github.QuickBirdEng:opencv-android:4.5.3.0'
```

**Key OpenCV Functions Used:**

1. **cvtColor** - Color space conversion (BGR ↔ Gray)
2. **equalizeHist** - Histogram equalization for contrast
3. **GaussianBlur** - Noise reduction
4. **Laplacian** - Sharpness calculation
5. **filter2D** - Sharpening filter application
6. **Mat** - Matrix operations for image data

**Usage Example:**
```kotlin
// Convert to grayscale
Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_BGR2GRAY)

// Apply histogram equalization
Imgproc.equalizeHist(grayMat, equalizedMat)

// Gaussian blur for denoising
Imgproc.GaussianBlur(equalizedMat, blurredMat, Size(5.0, 5.0), 0.0)
```

---

## MediaPipe Integration Notes

### Model Details

**File:** `hand_landmarker.task` (7.8 MB)
**Type:** TensorFlow Lite model
**Source:** Google MediaPipe Tasks
**Version:** Compatible with v0.10.26

### Landmark Indices

```
0: WRIST
1: THUMB_CMC
2: THUMB_MCP
3: THUMB_IP
4: THUMB_TIP
5: INDEX_FINGER_MCP
6: INDEX_FINGER_PIP
7: INDEX_FINGER_DIP
8: INDEX_FINGER_TIP
9: MIDDLE_FINGER_MCP
10: MIDDLE_FINGER_PIP
11: MIDDLE_FINGER_DIP
12: MIDDLE_FINGER_TIP
13: RING_FINGER_MCP
14: RING_FINGER_PIP
15: RING_FINGER_DIP
16: RING_FINGER_TIP
17: PINKY_MCP
18: PINKY_PIP
19: PINKY_DIP
20: PINKY_TIP
```

### Running Modes

1. **IMAGE:** Single image processing
2. **VIDEO:** Video file processing
3. **LIVE_STREAM:** Real-time camera feed (used in this project)

### Performance Considerations

- **CPU Delegate:** Works on all devices, slower
- **GPU Delegate:** Faster, requires GPU support
- **Max Hands:** Set to 1 for better performance
- **Frame Rate:** ~30 FPS on modern devices

---

## Security Considerations

### Token Storage

Tokens are stored in SharedPreferences with MODE_PRIVATE:
```kotlin
getSharedPreferences("BiometricsPrefs", Context.MODE_PRIVATE)
```

**Recommendations:**
1. Consider using EncryptedSharedPreferences for production
2. Implement token expiration
3. Use HTTPS for all API calls (already done)

### Image Data

Images are:
1. Captured locally
2. Encoded to Base64
3. Transmitted over HTTPS
4. Deleted after upload

**Recommendations:**
1. Consider encrypting images before upload
2. Implement certificate pinning for API calls
3. Add image watermarking/signing

### Permissions

Only requests necessary permissions:
- CAMERA (required for capture)
- INTERNET (required for upload)

---

## Performance Optimization Tips

### 1. Reduce MediaPipe Processing

```kotlin
// Process every Nth frame instead of every frame
private var frameCounter = 0
override fun onImageAvailable(reader: ImageReader) {
    if (frameCounter++ % 2 == 0) { // Process every 2nd frame
        processImage(reader)
    }
}
```

### 2. Optimize Image Size

```kotlin
// Reduce image resolution before processing
val scaledBitmap = Bitmap.createScaledBitmap(
    originalBitmap,
    originalBitmap.width / 2,
    originalBitmap.height / 2,
    true
)
```

### 3. Use GPU Delegate

```kotlin
// Enable GPU acceleration for MediaPipe
currentDelegate = HandLandmarkerHelper.DELEGATE_GPU
```

### 4. Optimize OpenCV Operations

```kotlin
// Reuse Mat objects instead of creating new ones
private val reusableMat = Mat()

fun process(input: Mat) {
    input.copyTo(reusableMat)
    // Process reusableMat
}
```

---

## Future Enhancement Ideas

1. **Multi-hand Support:** Capture both hands simultaneously
2. **Fingerprint Matching:** Local comparison of prints
3. **Liveness Detection:** Prevent spoofing with fake hands
4. **Offline Mode:** Queue uploads when network unavailable
5. **Quality Feedback:** Real-time UI hints for better positioning
6. **Dark Mode:** Theme support
7. **Localization:** Multi-language support
8. **Analytics:** Track capture success rates
9. **Biometric Templates:** Extract minutiae points for matching
10. **Video Recording:** Record full capture session for audit

---

## Dependencies Overview

### Production Dependencies

```gradle
// MediaPipe (Hand Detection)
implementation 'com.google.mediapipe:tasks-vision:0.10.26'

// OpenCV (Image Processing)
implementation 'com.github.QuickBirdEng:opencv-android:4.5.3.0'

// CameraX (Camera Management)
implementation 'androidx.camera:camera-core:1.2.0-alpha02'
implementation 'androidx.camera:camera-camera2:1.2.0-alpha02'
implementation 'androidx.camera:camera-lifecycle:1.2.0-alpha02'
implementation 'androidx.camera:camera-view:1.2.0-alpha02'

// OkHttp (HTTP Client)
implementation 'com.squareup.okhttp3:okhttp:4.10.0'

// Navigation (Fragment Navigation)
implementation 'androidx.navigation:navigation-fragment-ktx:2.5.3'
implementation 'androidx.navigation:navigation-ui-ktx:2.5.3'

// AndroidX Core
implementation 'androidx.core:core-ktx:1.9.0'
implementation 'androidx.appcompat:appcompat:1.5.1'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.fragment:fragment-ktx:1.5.4'

// Material Design
implementation 'com.google.android.material:material:1.7.0'
```

### Build Dependencies

```gradle
// Gradle Plugins
com.android.application:7.3.0
com.android.library:7.3.0
org.jetbrains.kotlin.android:1.7.10
de.undercouch.download:4.1.2
```

---

## Git Workflow

### Current Branch Status

- **Current:** 27-oct
- **Main:** main
- **Recent work:** Threshold corrections, OpenCV compilation, API key implementation

### Common Git Commands

```bash
# Check status
git status

# View recent commits
git log --oneline -5

# Switch to main branch
git checkout main

# Create new feature branch
git checkout -b feature/your-feature-name

# Stage changes
git add .

# Commit
git commit -m "Your commit message"

# Push to remote
git push origin 27-oct
```

---

## Contact and Resources

### External Documentation

- **MediaPipe Hand Landmarker:** https://developers.google.com/mediapipe/solutions/vision/hand_landmarker
- **OpenCV Android:** https://opencv.org/android/
- **CameraX:** https://developer.android.com/training/camerax
- **Android Activity Result API:** https://developer.android.com/training/basics/intents/result

### Demo Server

- **Base URL:** https://demo.rmstservices.com/biometrics/
- **Test endpoint:** /verifyToken
- **Upload endpoint:** /register

---

## Quick Reference

### SDK Integration (1 Minute)

```kotlin
// 1. Add dependency
implementation project(':BiometricsSdk')

// 2. Register launcher
val launcher = Biometrics.register(this) { result ->
    when (result) {
        is BiometricsResult.Success -> Log.d(TAG, result.transactionId)
        is BiometricsResult.Error -> Log.e(TAG, result.message)
        is BiometricsResult.Cancelled -> Log.d(TAG, "Cancelled")
    }
}

// 3. Launch
launcher.launch("your-auth-token")
```

### Key Constants

```kotlin
// Stability threshold
val STABILITY_THRESHOLD = 0.008f

// Quality threshold
val MIN_QUALITY_SCORE = 60f

// Hand detection confidence
val MIN_DETECTION_CONFIDENCE = 0.5f

// API endpoints
val BASE_URL = "https://demo.rmstservices.com/biometrics/"
```

### Build and Run

```bash
./gradlew clean assembleDebug installDebug
```

---

## Document Maintenance

**Last Updated:** 2025-12-10
**Project Version:** Based on commit d07b2cd (27-oct branch)
**Maintained By:** Development Team

**Update Triggers:**
- Major architecture changes
- New dependencies added
- API endpoint changes
- New features added
- Build configuration changes

---

*This document is intended for AI assistants (like Claude Code) and developers working on the RMSTBiometrics project. It provides comprehensive context for understanding and modifying the codebase.*
