# FocusTag ProGuard Rules

# 1. Strip Debug/Verbose Logs in Release Builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# 2. Kotlin Serialization / Supabase
# Keep serializable classes and their fields
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep the enum names for serialization if any
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Supabase models specific
-keep class com.focustag.app.data.model.** { *; }

# 3. Compose
-keep class androidx.compose.material.icons.** { *; }

# 4. WorkManager
-keep class androidx.work.** { *; }
