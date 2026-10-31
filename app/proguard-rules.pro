# Regole ProGuard / R8 per il modulo :app (Still)

# 1. Jetpack Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 2. Health Connect (Modelli e Record di Mindfulness)
-keep class androidx.health.connect.client.records.** { *; }
-keep class androidx.health.connect.client.units.** { *; }
-keep class androidx.health.connect.client.records.metadata.** { *; }

# 3. Google Play Services Wearable Data Layer
-keep class com.google.android.gms.wearable.** { *; }
-keepclassmembers class * extends com.google.android.gms.wearable.WearableListenerService {
    public void onMessageReceived(com.google.android.gms.wearable.MessageEvent);
    public void onDataChanged(com.google.android.gms.wearable.DataEventBuffer);
}

# 4. Servizi Android registrati nel Manifest
-keep class com.sanluca.still.StillTimerService { *; }
-keep class com.sanluca.still.WearListenerService { *; }

# 5. Modelli dati interni
-keep class com.sanluca.still.SessionLogEntry { *; }
-keep class com.sanluca.still.PendingSessionEntry { *; }
