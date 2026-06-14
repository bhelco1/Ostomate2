# Keep source file names and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx serialization — required for type-safe nav routes (@Serializable objects)
-keepattributes *Annotation*, InnerClasses
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Room — keep generated database implementations (libraries include consumer rules,
# but this guards against any gap in the consumer proguard shipped by Room KMP)
-keep class * extends androidx.room.RoomDatabase { *; }

# Glance widget
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Suppress warnings from transitive dependencies
-dontwarn org.koin.**
-dontwarn okio.**
# Sentry ships its own consumer rules; this suppresses any gap warnings
-dontwarn io.sentry.**
