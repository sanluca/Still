# Regole ProGuard / R8 per il modulo :wear (Still Watch)

# 1. Jetpack Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# 2. Wear OS Tiles & ProtoLayout
-keep class androidx.wear.tiles.TileService { *; }
-keep class androidx.wear.protolayout.** { <methods>; }

# 3. Google Play Services Wearable
-keep class com.google.android.gms.wearable.** { *; }

# 4. Servizi Wear registrati nel Manifest
-keep class com.sanluca.still.wear.StillTimerService { *; }
-keep class com.sanluca.still.wear.StillTileService { *; }
