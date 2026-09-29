# ProGuard rules for 2MIMICDJ2

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class com.miguenduval.mimicdj2.hilt.** { *; }

# Keep Kotlin serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Ktor classes
-keep class io.ktor.** { *; }

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep SLF4J/Logback
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }

# Keep our server classes
-keep class com.miguenduval.mimicdj2.server.** { *; }
-keep class com.miguenduval.mimicdj2.network.** { *; }
-keep class com.miguenduval.mimicdj2.ui.** { *; }

# Keep Parcelable/Serializable
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R8/ProGuard from stripping annotation processors
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod