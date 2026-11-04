# Orbin R8/ProGuard rules.

# kotlinx.serialization: keep serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.orbin.**$$serializer { *; }
-keepclassmembers class com.orbin.** {
    *** Companion;
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepnames class <2>$<3>

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
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
