package com.sanluca.still

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanluca.still.ui.theme.LocalStillColors

@Composable
internal fun StillAtmosphericBackground(
    isTimerRunning: Boolean,
    isPreparing: Boolean,
    isOvertime: Boolean,
    modifier: Modifier = Modifier
) {
    val stillColors = LocalStillColors.current
    val bgColor = MaterialTheme.colorScheme.background
    val overtimeColor = stillColors.overtime
    val breathPhase = if (isTimerRunning) {
        val atmosphereTransition = rememberInfiniteTransition(label = "natural_atmosphere")
        val animatedPhase by atmosphereTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath_phase"
        )
        animatedPhase
    } else {
        0.5f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(stillColors.skyTop, bgColor, stillColors.skyBottom)
            )
        )
        val activeBoost = if (isTimerRunning && !isPreparing) 1.35f else 0.85f
        val primaryGlowColor = if (isOvertime) overtimeColor else stillColors.auroraPrimary
        val auroraCenter = Offset(
            x = size.width * (0.46f + 0.08f * breathPhase),
            y = size.height * (0.43f + 0.04f * (1f - breathPhase))
        )
        val auroraRadius = size.minDimension * 0.88f
        if (auroraRadius > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlowColor.copy(alpha = (0.14f + 0.06f * breathPhase) * activeBoost),
                        stillColors.auroraSecondary.copy(alpha = (0.06f + 0.04f * (1f - breathPhase)) * activeBoost),
                        Color.Transparent
                    ),
                    center = auroraCenter,
                    radius = auroraRadius
                ),
                radius = auroraRadius,
                center = auroraCenter
            )
        }
    }
}

@Composable
internal fun StillTopBar(
    isPreparing: Boolean,
    isOvertime: Boolean,
    selectedTimeInSeconds: Int,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPresets: () -> Unit = {},
    activePresetName: String? = null,
    canSelectPreset: Boolean = true,
    onOpenGuide: () -> Unit = {}
) {
    val stillColors = LocalStillColors.current
    val overtimeColor = stillColors.overtime

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.still_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
            color = stillColors.titleColor
        )
        Text(
            text = when {
                isPreparing -> stringResource(R.string.status_preparing)
                isOvertime -> stringResource(R.string.status_overtime)
                activePresetName != null -> activePresetName
                selectedTimeInSeconds == -1 -> stringResource(R.string.status_no_limit)
                else -> stringResource(R.string.status_timer)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isPreparing -> MaterialTheme.colorScheme.tertiary
                isOvertime -> overtimeColor
                activePresetName != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        val presetsContentDesc = stringResource(R.string.cd_presets_button)
        val settingsContentDesc = stringResource(R.string.cd_settings_button)
        val statsContentDesc = stringResource(R.string.cd_stats_button)
        val guideContentDesc = stringResource(R.string.cd_guide_button)
        val infoContentDesc = stringResource(R.string.cd_info_button)
        val pillShape = RoundedCornerShape(24.dp)
        // Capsula unificata per i 5 pulsanti (Preset, Impostazioni, Statistiche, Guida, Info)
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = pillShape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    shape = pillShape
                )
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val topBarSymbolStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            val topBarTextAutoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 17.sp)
            val topBarIconAutoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 19.sp)
            IconButton(
                onClick = onOpenPresets,
                enabled = canSelectPreset,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (canSelectPreset) 1f else 0.35f)
                    .semantics { contentDescription = presetsContentDesc }
            ) {
                Text(
                    text = "P",
                    style = topBarSymbolStyle,
                    autoSize = topBarTextAutoSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = if (activePresetName != null) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (activePresetName != null) MaterialTheme.colorScheme.primary else stillColors.titleColor
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = settingsContentDesc }
            ) {
                Text(
                    text = "⚙",
                    style = topBarSymbolStyle,
                    autoSize = topBarIconAutoSize,
                    maxLines = 1,
                    softWrap = false,
                    color = stillColors.titleColor
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenStats,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = statsContentDesc }
            ) {
                Text(
                    text = "≡",
                    style = topBarSymbolStyle,
                    autoSize = topBarIconAutoSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                    color = stillColors.titleColor
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenGuide,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = guideContentDesc }
            ) {
                Text(
                    text = "?",
                    style = topBarSymbolStyle,
                    autoSize = topBarTextAutoSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.SemiBold,
                    color = stillColors.titleColor
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            IconButton(
                onClick = onOpenPrivacy,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = infoContentDesc }
            ) {
                Text(
                    text = "i",
                    style = topBarSymbolStyle,
                    autoSize = topBarTextAutoSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.SemiBold,
                    color = stillColors.titleColor
                )
            }
        }
    }
}
