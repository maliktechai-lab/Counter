# ReelTracker ProGuard rules

# Room — keep all entities and DAOs
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep class com.reeltracker.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract ** *Dao();
}

# Domain models stored by name in DB
-keep enum com.reeltracker.domain.model.TransitionType { *; }
-keep enum com.reeltracker.domain.model.Platform { *; }

# Detector types stored in diagnostics
-keep enum com.reeltracker.accessibility.detectors.DetectionSignal { *; }
-keep class com.reeltracker.domain.model.DetectionStatus { *; }
-keep class com.reeltracker.accessibility.statemachine.PendingOutcome { *; }
-keep class com.reeltracker.accessibility.EventSnapshot { *; }
-keep class com.reeltracker.accessibility.NodeSnapshot { *; }
-keep class com.reeltracker.accessibility.EventSignature { *; }

# Kotlin
-keepattributes *Annotation*
-keepclassmembers class kotlin.Metadata { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
