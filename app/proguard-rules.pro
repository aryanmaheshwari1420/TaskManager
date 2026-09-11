# Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.HandlerContext {
    public <init>(...);
}

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, AnnotationDefault
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Gson
-keepattributes EnclosingMethod
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class com.google.gson.stream.** { *; }

# Gemini API Models (CRITICAL: Prevents R8 from renaming these)
-keep class com.aryanmaheshwari.taskmanager.data.remote.** { *; }
-keepclassmembers class com.aryanmaheshwari.taskmanager.data.remote.** { *; }
