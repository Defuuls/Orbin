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

# Strip debug and verbose logging from release builds. Media failure paths used to Log.w the
# full URL; those call sites now redact to host-only (see MediaPreloader). Warnings and errors
# stay for crash legibility, without browsing paths in the message.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
