@file:Suppress("WearMaterialTheme")

package com.sanluca.still.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal const val SETTINGS_PAGE_COUNT = 3

@Composable
internal fun SettingsPillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentTextWhenUnselected: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        accentTextWhenUnselected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        selected -> Color.Transparent
        enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(containerColor, shape)
            .then(
                if (selected) Modifier else Modifier.border(1.dp, borderColor, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = contentColor
        )
    }
}

@Composable
internal fun SettingsStepChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 12f
) {
    val shape = RoundedCornerShape(18.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.28f else 0.10f)
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.32f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.38f else 0.15f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .widthIn(min = 40.dp)
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(containerColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 2f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = contentColor
        )
    }
}

@Composable
internal fun SettingsHeaderButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
