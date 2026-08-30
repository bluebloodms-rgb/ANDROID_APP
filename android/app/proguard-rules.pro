# Hilt
-keep class dagger.** { *; }
-dontwarn dagger.internal.codegen.**
-keep class javax.inject.** { *; }
-keep @javax.inject.Scope * { *; }
-keep @interface dagger.* { *; }
-keepclassmembers,allowobfuscation class * extends dagger.hilt.android.internal.managers.ComponentSupplier {
    *;
}

# MAVLink
-keep class org.mavlink.** { *; }
-dontwarn org.mavlink.**
-keep class com.dronegcs.app.data.mavlink.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Timber
-dontwarn timber.log.Timber

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# With R8 full mode generic signatures are stripped for classes that are not kept.
-dontwarn okio.**
-dontwarn kotlin.**
