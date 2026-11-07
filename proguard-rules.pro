# R8 for CarTracks: shrink only (the build is what the owner installs).
# Names stay as written so enum/class names stored in the database, preferences and
# backups keep matching, and crash stack traces stay readable.
-dontobfuscate

# Parcelable ids passed between screens/services (Track.Id, Marker.Id, TrackPoint.Id).
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Enums are persisted by name()/valueOf(): keep their constants and the generated helpers.
-keepclassmembers enum de.dennisguse.opentracks.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Readable crash traces (ShowErrorActivity / logcat).
-keepattributes SourceFile,LineNumberTable

# MapLibre's C++ core calls back into Java through JNI; its AAR only ships a handful of
# keep rules, so keep the whole Java side (suggested by the Gemini 3.8 Flash audit).
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
