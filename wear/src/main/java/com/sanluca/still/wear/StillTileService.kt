package com.sanluca.still.wear

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class StillTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val singleTileTimeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(buildTileLayout(this))
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion("6")
                .setTileTimeline(singleTileTimeline)
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("6")
                .addIdToImageMapping(
                    "app_icon",
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.still_icon)
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }

    private fun buildTileLayout(context: Context): LayoutElementBuilders.LayoutElement {
        val launchIntent = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName("com.sanluca.still.wear.MainActivity")
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("launch_main_activity")
            .setOnClick(launchIntent)
            .build()

        val clickableModifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .build()

        val appIconImage = LayoutElementBuilders.Image.Builder()
            .setResourceId("app_icon")
            .setWidth(dp(110f))
            .setHeight(dp(110f))
            .setModifiers(clickableModifiers)
            .build()

        // Sostituiamo PrimaryLayout con un semplice Box per aggirare i problemi di rendering
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF000000.toInt())) // Sfondo completamente nero forzato
                            .build()
                    )
                    .build()
            )
            .addContent(appIconImage)
            .build()
    }
}