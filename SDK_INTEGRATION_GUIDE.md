# RMST Biometrics SDK - Integration Guide

## Overview

The RMST Biometrics SDK provides touchless fingerprint capture functionality for Android applications. It uses AI-powered hand detection to capture high-quality fingerprint images, processes them on our secure servers, and returns the results in multiple formats (PNG and WSQ).

---

## Requirements

- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 13 (API 33) or higher
- **Permissions**: Camera, Internet
- **Device**: Physical Android device with rear camera (emulator not recommended)

---

## Installation

### Step 1: Add the AAR file

Copy the `BiometricsSdk.aar` file to your project's `libs` folder:

```
your-app/
├── app/
│   ├── libs/
│   │   └── BiometricsSdk.aar
│   └── build.gradle
```

### Step 2: Configure build.gradle

Add the following to your app's `build.gradle`:

```gradle
android {
    // ... your existing config
}

dependencies {
    // RMST Biometrics SDK
    implementation files('libs/BiometricsSdk.aar')

    // Required dependencies (add these if not already present)
    implementation 'androidx.core:core-ktx:1.8.0'
    implementation 'androidx.fragment:fragment-ktx:1.5.4'
    implementation 'androidx.appcompat:appcompat:1.5.1'
    implementation 'com.google.android.material:material:1.7.0'

    // CameraX (required)
    def camerax_version = '1.2.0-alpha02'
    implementation "androidx.camera:camera-core:$camerax_version"
    implementation "androidx.camera:camera-camera2:$camerax_version"
    implementation "androidx.camera:camera-lifecycle:$camerax_version"
    implementation "androidx.camera:camera-view:$camerax_version"

    // MediaPipe (required)
    implementation 'com.google.mediapipe:tasks-vision:0.10.26'

    // OpenCV (required)
    implementation 'com.quickbirdstudios:opencv:4.5.3.0'

    // Networking (required)
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
}
```

### Step 3: Add Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature android:name="android.hardware.camera" android:required="true" />
```

### Step 4: Network Security Config (for development)

If using HTTP (not HTTPS) during development, create `res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">your-server-ip</domain>
    </domain-config>
</network-security-config>
```

Reference it in your `AndroidManifest.xml`:

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

---

## Quick Start

### Basic Integration (Kotlin)

```kotlin
import com.biometrics.Biometrics
import com.biometrics.BiometricsLauncher
import com.biometrics.model.BiometricsResult

class MainActivity : AppCompatActivity() {

    private lateinit var biometricsLauncher: BiometricsLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Step 1: Register the SDK launcher
        biometricsLauncher = Biometrics.register(this) { result ->
            handleBiometricsResult(result)
        }

        // Step 2: Launch when ready (e.g., on button click)
        findViewById<Button>(R.id.captureButton).setOnClickListener {
            biometricsLauncher.launch("your-auth-token")
        }
    }

    private fun handleBiometricsResult(result: BiometricsResult?) {
        when (result) {
            is BiometricsResult.Success -> {
                // Fingerprints captured and processed successfully
                val batchId = result.batchId
                val processedFiles = result.processedFiles

                processedFiles.forEach { file ->
                    Log.d("Biometrics", "Finger: ${file.getDisplayName()}")
                    Log.d("Biometrics", "PNG URL: ${file.processed_png}")
                    Log.d("Biometrics", "WSQ URL: ${file.processed_wsq}")
                }
            }
            is BiometricsResult.Error -> {
                // Handle error
                Log.e("Biometrics", "Error: ${result.message}")
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
            is BiometricsResult.Cancelled -> {
                // User cancelled the capture
                Log.d("Biometrics", "Capture cancelled by user")
            }
            null -> {
                // No result returned
                Log.w("Biometrics", "No result returned")
            }
        }
    }
}
```

### Integration in Fragment

```kotlin
class MyFragment : Fragment() {

    private lateinit var biometricsLauncher: BiometricsLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricsLauncher = Biometrics.register(this) { result ->
            // Handle result
        }
    }

    private fun startCapture() {
        biometricsLauncher.launch("your-auth-token")
    }
}
```

---

## API Reference

### Biometrics

The main entry point for the SDK.

```kotlin
object Biometrics {
    /**
     * Register the SDK with an Activity
     */
    fun register(
        activity: ComponentActivity,
        callback: (BiometricsResult?) -> Unit
    ): BiometricsLauncher

    /**
     * Register the SDK with a Fragment
     */
    fun register(
        fragment: Fragment,
        callback: (BiometricsResult?) -> Unit
    ): BiometricsLauncher
}
```

### BiometricsLauncher

Used to launch the fingerprint capture flow.

```kotlin
class BiometricsLauncher {
    /**
     * Launch the fingerprint capture screen
     * @param token Authentication token (optional, can be empty string)
     */
    fun launch(token: String)
}
```

### BiometricsResult

Sealed class representing the capture result.

```kotlin
sealed class BiometricsResult {
    /**
     * Capture successful
     * @param batchId Unique identifier for this capture session
     * @param processedFiles List of processed fingerprint files
     * @param parameters Processing parameters used
     */
    data class Success(
        val batchId: String,
        val processedFiles: List<ProcessedFile>,
        val parameters: ProcessingParams?
    )

    /**
     * Capture failed
     * @param message Error description
     */
    data class Error(val message: String)

    /**
     * User cancelled the capture
     */
    object Cancelled
}
```

### ProcessedFile

Represents a single processed fingerprint.

```kotlin
data class ProcessedFile(
    val original_name: String,    // e.g., "RIGHT_INDEX.png"
    val processed_png: String,    // URL to processed PNG image
    val processed_wsq: String     // URL to WSQ file (FBI standard format)
) {
    fun getDisplayName(): String  // Returns "Right Index"
    fun getPngFilename(): String  // Returns "RIGHT_INDEX_processed.png"
    fun getWsqFilename(): String  // Returns "RIGHT_INDEX.wsq"
}
```

### ProcessingParams

Processing parameters used by the server.

```kotlin
data class ProcessingParams(
    val thickness: Int,    // Ridge thickness (1=thin, 2=medium, 3=thick)
    val margin: Int,       // Edge margin in pixels
    val crop: Boolean,     // Whether output is cropped
    val dpi: Int,          // Output DPI (default: 500)
    val block_size: Int    // Processing block size
)
```

---

## Result Handling Examples

### Display Results in RecyclerView

```kotlin
is BiometricsResult.Success -> {
    val adapter = FingerprintAdapter(result.processedFiles)
    recyclerView.adapter = adapter
}
```

### Download Fingerprint Files

```kotlin
fun downloadFile(url: String, filename: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(filename)
        .setDescription("Downloading fingerprint")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    downloadManager.enqueue(request)
}

// Usage
result.processedFiles.forEach { file ->
    downloadFile(file.processed_png, file.getPngFilename())
    downloadFile(file.processed_wsq, file.getWsqFilename())
}
```

### Load Images with Glide

```kotlin
// Add Glide dependency: implementation 'com.github.bumptech.glide:glide:4.15.1'

Glide.with(context)
    .load(processedFile.processed_png)
    .into(imageView)
```

---

## Capture Flow

1. **User launches SDK** → Camera screen opens
2. **Hand detection** → User positions hand in front of camera
3. **Quality checks** → SDK validates hand position and stability
4. **Auto-capture** → When conditions are met, fingerprints are captured
5. **Server processing** → Images are sent to server for enhancement
6. **Result returned** → PNG and WSQ URLs returned to your app

### Capture Conditions

The SDK automatically captures when:
- Hand is detected with sufficient confidence
- Hand is stable (not moving)
- Hand is properly positioned in frame
- Camera is focused on fingertips

---

## Troubleshooting

### Issue: Camera permission denied

Ensure you've added the permission to manifest and requested it at runtime:

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
    != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
        arrayOf(Manifest.permission.CAMERA), REQUEST_CODE)
}
```

### Issue: Network error / Connection refused

1. Check that the device has internet access
2. Verify the server is running and accessible
3. For HTTP connections, ensure network security config allows cleartext traffic

### Issue: Hand not detected

1. Ensure good lighting conditions
2. Hold hand 20-30cm from camera
3. Keep palm facing the camera
4. Avoid extreme angles

### Issue: Low quality capture

1. Keep hand stable during capture
2. Ensure good lighting (avoid shadows on fingers)
3. Clean camera lens

---

## ProGuard Rules

If using ProGuard/R8, add these rules to your `proguard-rules.pro`:

```proguard
# RMST Biometrics SDK
-keep class com.biometrics.** { *; }
-keep class com.google.mediapipe.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# OpenCV
-keep class org.opencv.** { *; }
```

---

## Support

For technical support, please contact:
- Email: support@rmstservices.com
- Documentation: https://docs.rmstservices.com/biometrics

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-01 | Initial release with PNG and WSQ output |

---

## License

This SDK is proprietary software. Unauthorized distribution or reverse engineering is prohibited.

© 2026 RMST Services. All rights reserved.
