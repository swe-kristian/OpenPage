# Keep Compose runtime
-keep class androidx.compose.** { *; }

# Kotlin metadata
-dontwarn kotlin.**

# Parcelable
-keepclassmembers class com.qawse.openpage.** implements android.os.Parcelable {
    public static final ** CREATOR;
}
