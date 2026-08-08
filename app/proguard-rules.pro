# SOHEIL release hardening
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Android components referenced from the manifest.
-keep class com.soheil.lifeos.MainActivity { *; }
-keep class com.soheil.lifeos.ReminderReceiver { *; }
