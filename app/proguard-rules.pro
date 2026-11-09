# Keep protobuf generated classes and lite runtime
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
-keep class com.reiux.lumen.proto.** { *; }

# Keep data models
-keep class com.reiux.lumen.data.model.** { *; }

# Keep OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Google Tink / AndroidX Security annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
