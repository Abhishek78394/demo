# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# ===== Socket.io and WebSocket Support =====
# Keep socket.io classes to prevent connection failures in release builds
-keep class io.socket.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep WebSocket classes
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# ===== MMKV Storage =====
# Keep MMKV native library and classes
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# ===== React Native Core =====
# Keep React Native classes
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-dontwarn com.facebook.react.**

# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# ===== Device Info =====
# Keep device info classes for uniqueId retrieval
-keep class com.learnium.RNDeviceInfo.** { *; }

# ===== Networking =====
# Keep networking classes for API calls
-keep class com.android.volley.** { *; }
-dontwarn com.android.volley.**

# ===== General Android =====
# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Keep source file names and line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
