# ProGuard rules for 象棋Pro

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep our chess and engine classes
-keep class com.chesspro.app.core.chess.** { *; }
-keep class com.chesspro.app.core.engine.** { *; }
-keep class com.chesspro.app.core.overlay.** { *; }
-keep class com.chesspro.app.ui.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
