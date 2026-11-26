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

# Moshi & Retrofit
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.ghostgrid.hybrid.** { *; }
-keep class com.example.ghostgrid.downloader.** { *; }
