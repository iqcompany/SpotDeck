# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep launcher components
-keep class com.spotdeck.launcher.** { *; }

# Keep data classes
-keepclassmembers class com.spotdeck.launcher.AppInfo {
    <fields>;
}

# Keep view binding classes
-keep class com.spotdeck.launcher.databinding.** { *; }

# Standard Android rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }