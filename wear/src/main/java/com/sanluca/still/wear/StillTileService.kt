package com.sanluca.still.wear

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sanluca.still.wear.ui.theme.DarkWarmColorScheme
import com.sanluca.still.wear.ui.theme.OvertimeGold
import com.sanluca.still.wear.ui.theme.SpringColorScheme
import com.sanluca.still.wear.ui.theme.StillColorPalette
import com.sanluca.still.wear.ui.theme.SummerColorScheme
import com.sanluca.still.wear.ui.theme.WinterColorScheme

private const val TILE_RESOURCES_VERSION = "8"
private const val TILE_LOG_TAG = "StillWear"

class StillTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val deviceParams = requestParams.deviceConfiguration
        val isRunning = StillTimerService.isServiceRunning
        val snapshot = StillTimerService.timerState.value
        Log.d(
            TILE_LOG_TAG,
            "Tile onTileRequest: running=$isRunning, prep=${snapshot.isPreparing}, overtime=${snapshot.isOvertime}, screen=${deviceParams.screenWidthDp}x${deviceParams.screenHeightDp}"
        )

        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildTileLayout(this, deviceParams))
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(TILE_RESOURCES_VERSION)
                .setTileTimeline(singleTileTimeline)
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(TILE_RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildTileLayout(
        context: Context,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        val isRunning = StillTimerService.isServiceRunning
        val snapshot = StillTimerService.timerState.value
        val palette = getSavedColorPalette(context)
        val colorScheme = when (palette) {
            StillColorPalette.SPRING -> SpringColorScheme
            StillColorPalette.SUMMER -> SummerColorScheme
            StillColorPalette.AUTUMN -> DarkWarmColorScheme
            StillColorPalette.WINTER -> WinterColorScheme
        }

        val accentColorArgb = if (isRunning && snapshot.isOvertime) {
            OvertimeGold.toArgb()
        } else {
            colorScheme.primary.toArgb()
        }
        val onAccentColorArgb = if (isRunning && snapshot.isOvertime) {
            0xFF000000.toInt()
        } else {
            colorScheme.onPrimary.toArgb()
        }
        val primaryTextColorArgb = colorScheme.onBackground.toArgb()
        val secondaryTextColorArgb = colorScheme.onSurfaceVariant.toArgb()

        val launchIntent = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName("com.sanluca.still.wear.MainActivity")
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(if (isRunning) "open_ongoing_session" else "launch_main_activity")
            .setOnClick(launchIntent)
            .build()

        val titleText = Text.Builder(context, context.getString(R.string.tile_title))
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(secondaryTextColorArgb))
            .setMaxLines(1)
            .build()

        val mainContentString = if (isRunning) {
            when {
                snapshot.isPreparing -> context.getString(R.string.tile_status_preparing)
                snapshot.isOvertime -> context.getString(R.string.tile_status_overtime)
                else -> context.getString(R.string.tile_status_active)
            }
        } else {
            val savedTimeSeconds = getSavedSelectedTime(context)
            if (savedTimeSeconds == -1) {
                context.getString(R.string.tile_duration_unlimited)
            } else {
                val minutes = (savedTimeSeconds / 60).coerceAtLeast(1)
                context.getString(R.string.tile_duration_minutes, minutes)
            }
        }

        val mainContentText = Text.Builder(context, mainContentString)
            .setTypography(if (isRunning) Typography.TYPOGRAPHY_TITLE3 else Typography.TYPOGRAPHY_TITLE2)
            .setColor(argb(if (isRunning && snapshot.isOvertime) OvertimeGold.toArgb() else primaryTextColorArgb))
            .setMaxLines(2)
            .build()

        val chipLabel = context.getString(
            if (isRunning) R.string.tile_action_open else R.string.tile_action_start
        )
        val primaryChip = CompactChip.Builder(context, chipLabel, clickable, deviceParameters)
            .setChipColors(ChipColors(accentColorArgb, onAccentColorArgb))
            .build()

        val primaryLayout = PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(titleText)
            .setContent(mainContentText)
            .setPrimaryChipContent(primaryChip)
            .build()

        // Sfondo nero OLED puro conforme alle Wear OS Tile Guidelines
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF000000.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(primaryLayout)
            .build()
    }
}