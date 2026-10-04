-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keepclassmembers class com.k4music.android.utils.ActionReceiver$* {
    <fields>;
    <methods>;
}

-if @com.k4music.android.utils.ActionReceiver class **
-keepclassmembers class <1> {
    <fields>;
    <methods>;
}

#noinspection ShrinkerUnresolvedReference
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    #noinspection ShrinkerUnresolvedReference
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    #noinspection ShrinkerUnresolvedReference
    kotlinx.serialization.KSerializer serializer(...);
}

-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keep class ch.qos.logback.classic.android.** { *; }

# Manifest-declared classes: the system instantiates these by exact name.
-keep class com.k4music.android.MainApplication { *; }
-keep class com.k4music.android.MainActivity { *; }
-keep class com.k4music.android.service.PlayerService { *; }
-keep class com.k4music.android.service.PlayerService$NotificationActionReceiver { *; }
-keep class com.k4music.android.service.PlayerService$NotificationDismissReceiver { *; }
-keep class com.k4music.android.service.PlayerMediaBrowserService { *; }
-keep class com.k4music.android.service.PrecacheService { *; }

# Startup-path singletons accessed by name/reference from Application.
-keep class com.k4music.android.utils.CrashGuard { *; }
-keep class com.k4music.android.service.ServiceNotifications { *; }
-keep class com.k4music.android.Database { *; }
-keep class com.k4music.android.DatabaseDependency { *; }

# MonetCompat — reflection-based dynamic color access
-keep class com.kieronquinn.monetcompat.** { *; }
-dontwarn com.kieronquinn.monetcompat.**

# kotlinx.collections.immutable — used across UI state and preferences
-keep class kotlinx.collections.immutable.** { *; }
-dontwarn kotlinx.collections.immutable.**

# Chaquopy Python bridge
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Compose UI runtime (generated + lambdas)
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
