# GhostGrid Privacy-Hardened R8 / ProGuard Rules

# Strip ALL Android Log calls in release builds to guarantee zero GPS coordinate leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# SQLCipher native bindings
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }

# MapLibre Native SDK
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# GraphHopper Core & JTS
-keep class com.graphhopper.** { *; }
-keep class org.locationtech.jts.** { *; }
-dontwarn com.graphhopper.**
-dontwarn org.codehaus.janino.**
-dontwarn org.codehaus.commons.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.slf4j.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn com.google.protobuf.**
-dontwarn org.openstreetmap.osmosis.**

# Moshi, Retrofit & OkHttp
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.ghostgrid.hybrid.** { *; }
-keep class com.example.ghostgrid.downloader.** { *; }
-keep class com.example.ghostgrid.data.** { *; }
-keep class com.example.ghostgrid.tools.** { *; }
-keep class com.example.ghostgrid.search.** { *; }
-keep class com.example.ghostgrid.routing.** { *; }
-keep class com.example.ghostgrid.map.** { *; }
-keep class com.example.ghostgrid.tracking.** { *; }
-keep class com.example.ghostgrid.i18n.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn com.squareup.moshi.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
