# Keep protobuf-javalite message fields accessed reflectively by MessageSchema
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**

# Keep Android 16+ Live Update reflective methods on NotificationCompat.Builder
-keepclassmembers class androidx.core.app.NotificationCompat$Builder {
    public androidx.core.app.NotificationCompat$Builder setRequestPromotedOngoing(boolean);
    public androidx.core.app.NotificationCompat$Builder setShortCriticalText(java.lang.CharSequence);
}

# Google Tink / AndroidX Security compile-only annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

