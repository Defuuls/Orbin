# Orbin R8/ProGuard rules.

# kotlinx.serialization: keep serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.orbin.**$$serializer { *; }
-keepclassmembers class com.orbin.** {
    *** Companion;
}

# Keep navigation route classes (referenced reflectively by type-safe nav).
-keep @kotlinx.serialization.Serializable class com.orbin.app.navigation.** { *; }

# Coil 3 and OkHttp ship their own consumer rules; nothing extra required here.

# Strip debug and verbose logging from release builds. Orbin logs a media URL at debug level
# while preloading; on a client that encrypts everything it stores, writing what a reader is
# looking at into the system log is at odds with the rest of the app. Warnings and errors are
# kept: they carry no browsing content beyond a failing board, and they are what makes a crash
# report legible.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
