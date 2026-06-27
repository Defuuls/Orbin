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
