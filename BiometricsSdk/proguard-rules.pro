# =============================================================================
# RMST Biometrics SDK - ProGuard Rules
# =============================================================================

# Keep public API classes that clients will use
-keep public class com.biometrics.Biometrics { *; }
-keep public class com.biometrics.BiometricsLauncher { *; }
-keep public class com.biometrics.model.BiometricsResult { *; }
-keep public class com.biometrics.model.BiometricsResult$* { *; }
-keep public class com.biometrics.model.ProcessResponse { *; }
-keep public class com.biometrics.model.ProcessedFile { *; }
-keep public class com.biometrics.model.ProcessingParams { *; }

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep BiometricsActivity (launched via Intent)
-keep public class com.biometrics.BiometricsActivity { *; }

# Keep contract class
-keep public class com.biometrics.contract.BiometricsContract { *; }

# =============================================================================
# MediaPipe
# =============================================================================
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# =============================================================================
# OpenCV
# =============================================================================
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# =============================================================================
# OkHttp
# =============================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# =============================================================================
# Kotlin
# =============================================================================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# =============================================================================
# Obfuscation settings
# =============================================================================
# Rename source file attribute to hide original filenames
-renamesourcefileattribute SourceFile

# Remove debug logs in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
