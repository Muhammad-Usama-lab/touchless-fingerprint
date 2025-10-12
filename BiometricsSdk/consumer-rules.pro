# Add project specific ProGuard rules for consumers of this library here.
# By default, R8 will obfuscate the entire library, including the public API.
# We must explicitly keep the classes and methods that the client app needs to access.

# Keep all public classes in the com.biometrics package and its subpackages.
-keep public class com.biometrics.** { *; }

# Keep the following classes and their public members.
-keep public class com.biometrics.Biometrics
-keep public class com.biometrics.BiometricsLauncher
-keep public class com.biometrics.BiometricsActivity
-keep public class com.biometrics.OverlayView
-keep public class com.biometrics.HandLandmarkerHelper

# Keep the result sealed class and all of its subclasses.
-keep public class com.biometrics.model.BiometricsResult
-keep public class com.biometrics.model.BiometricsResult$* { *; }
