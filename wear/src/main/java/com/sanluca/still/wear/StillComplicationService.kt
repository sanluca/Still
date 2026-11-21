package com.sanluca.still.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

class StillComplicationService : ComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return buildComplicationData(type, includeTapAction = false)
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        listener.onComplicationData(
            buildComplicationData(request.complicationType, includeTapAction = true)
        )
    }

    private fun buildComplicationData(
        type: ComplicationType,
        includeTapAction: Boolean
    ): ComplicationData? {
        val label = getString(R.string.complication_short_text)
        val contentDescription = PlainComplicationText.Builder(
            getString(R.string.complication_description)
        ).build()
        val monoImage = MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_still_complication)
        ).build()
        val tapAction = if (includeTapAction) createLaunchPendingIntent() else null

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(label).build(),
                contentDescription = contentDescription
            )
                .setMonochromaticImage(monoImage)
                .setTapAction(tapAction)
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = monoImage,
                contentDescription = contentDescription
            )
                .setTapAction(tapAction)
                .build()

            else -> null
        }
    }

    private fun createLaunchPendingIntent(): PendingIntent {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
