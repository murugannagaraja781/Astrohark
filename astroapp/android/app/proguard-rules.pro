# ============================================================
# ProGuard / R8 Rules for Astrohark App
# Play Store Release Build
# ============================================================

# ---- General Android ----
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---- Firebase ----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---- App Services (FCM, Call) ----
-keep class com.astrohark.app.FCMService { *; }
-keep class com.astrohark.app.CallForegroundService { *; }
-keep class com.astrohark.app.AstrologerApp { *; }

# Keep all Activity classes
-keep class com.astrohark.app.** extends android.app.Activity { *; }
-keep class com.astrohark.app.** extends androidx.appcompat.app.AppCompatActivity { *; }
-keep class com.astrohark.app.** extends androidx.fragment.app.Fragment { *; }

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# ---- Retrofit ----
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---- Gson ----
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep data/model classes used with Gson
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ---- Socket.IO ----
-keep class io.socket.** { *; }
-dontwarn io.socket.**
-keep class org.json.** { *; }
-keep class io.socket.client.** { *; }
-keep class io.socket.engineio.** { *; }
-dontwarn io.socket.engineio.**

# ---- WebRTC (Stream) ----
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class io.getstream.** { *; }
-dontwarn io.getstream.**

# ---- PhonePe SDK ----
-keep class com.phonepe.** { *; }
-dontwarn com.phonepe.**
-keep class phonepe.** { *; }
-dontwarn phonepe.**

# ---- Glide ----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}
-dontwarn com.bumptech.glide.**

# ---- Coil (Compose image loading) ----
-dontwarn coil.**
-keep class coil.** { *; }

# ---- Room Database ----
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# ---- Jetpack Compose ----
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ---- Kotlin ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---- AndroidX / Material ----
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class androidx.** { *; }
-dontwarn androidx.**

# ---- Security Crypto ----
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ---- CircleImageView ----
-keep class de.hdodenhof.circleimageview.** { *; }

# ---- Keep all model/data classes in the app ----
-keep class com.astrohark.app.data.** { *; }
-keep class com.astrohark.app.model.** { *; }
-keep class com.astrohark.app.network.** { *; }

# ---- Prevent R8 from removing used classes ----
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- Suppress warnings ----
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
