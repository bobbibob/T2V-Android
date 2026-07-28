# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$$serializer {
    *;
}
-keep,includedescriptorclasses class com.t2v.**$$serializer { *; }
-keepclassmembers class com.t2v.** {
    *** Companion;
}
-keepclasseswithmembers class com.t2v.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ONNX Runtime
-keep class com.microsoft.onnxruntime.** { *; }

# ffmpeg-kit
-keep class com.arthenica.ffmpegkit.** { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }
