package com.sanluca.still

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun StillStatsDialog(
    sessionHistoryList: List<SessionLogEntry>,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onExportHistory: (() -> Unit)? = null,
    onImportHistory: (() -> Unit)? = null
) {
    val totalMinutes = sessionHistoryList.sumOf { it.durationMinutes }
    val totalSessions = sessionHistoryList.size
    val streak = calculateStreakDays(sessionHistoryList)
    val bestStreak = calculateBestStreakDays(sessionHistoryList)
    val overallAvgBpm = remember(sessionHistoryList) {
        val sessionHrValues = sessionHistoryList.mapNotNull { entry ->
            entry.avgBpm ?: when {
                entry.startBpm != null && entry.endBpm != null -> ((entry.startBpm + entry.endBpm) / 2.0).roundToInt()
                entry.startBpm != null -> entry.startBpm
                entry.endBpm != null -> entry.endBpm
                else -> null
            }
        }
        if (sessionHrValues.isNotEmpty()) sessionHrValues.average().roundToInt() else null
    }
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()

    val sessionsByDate = remember(sessionHistoryList, zone) {
        sessionHistoryList.groupBy {
            Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate()
        }
    }

    var displayedYearMonth by remember { mutableStateOf(YearMonth.now(zone)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = @Composable {
            Text(
                text = stringResource(R.string.dialog_stats_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.stats_minutes), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.stats_min_format, totalMinutes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.stats_sessions), style = MaterialTheme.typography.labelMedium)
                            Text("$totalSessions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(stringResource(R.string.stats_streak), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.stats_streak_format, streak), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = stringResource(R.string.stats_best_streak_format, bestStreak),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (overallAvgBpm != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.stats_overall_hr_avg, overallAvgBpm),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- CALENDARIO MENSILE ---
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        // Barra di navigazione < Mese Anno >
                        val monthTitle = remember(displayedYearMonth, locale) {
                            val mName = displayedYearMonth.month
                                .getDisplayName(JavaTextStyle.FULL_STANDALONE, locale)
                                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                            "$mName ${displayedYearMonth.year}"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { displayedYearMonth = displayedYearMonth.minusMonths(1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Text(
                                    text = "‹",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = { displayedYearMonth = displayedYearMonth.plusMonths(1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Text(
                                    text = "›",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Intestazione Giorni della Settimana (Lun - Dom)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DayOfWeek.entries.forEach { dow ->
                                val label = dow
                                    .getDisplayName(JavaTextStyle.SHORT_STANDALONE, locale)
                                    .take(3)
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Griglia 7 colonne dei giorni del mese
                        val firstOfMonth = displayedYearMonth.atDay(1)
                        val leadingEmptyCells = firstOfMonth.dayOfWeek.value - 1 // Lunedì = 0 .. Domenica = 6
                        val daysInMonth = displayedYearMonth.lengthOfMonth()
                        val totalCells = ((leadingEmptyCells + daysInMonth + 6) / 7) * 7
                        val today = LocalDate.now(zone)

                        for (week in 0 until (totalCells / 7)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (col in 0..6) {
                                    val cellIndex = week * 7 + col
                                    val dayNumber = cellIndex - leadingEmptyCells + 1
                                    if (dayNumber in 1..daysInMonth) {
                                        val cellDate = displayedYearMonth.atDay(dayNumber)
                                        val daySessions = sessionsByDate[cellDate].orEmpty()
                                        val hasMeditation = daySessions.isNotEmpty()
                                        val dayTotalMins = daySessions.sumOf { it.durationMinutes }
                                        val isToday = cellDate == today

                                        val circleBgColor = when {
                                            !hasMeditation -> Color.Transparent
                                            dayTotalMins >= 30 -> MaterialTheme.colorScheme.primary
                                            dayTotalMins >= 15 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                            else -> MaterialTheme.colorScheme.primaryContainer
                                        }
                                        val dayTextColor = when {
                                            !hasMeditation && isToday -> MaterialTheme.colorScheme.primary
                                            !hasMeditation -> MaterialTheme.colorScheme.onSurface
                                            dayTotalMins >= 15 -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(circleBgColor, CircleShape)
                                                    .then(
                                                        if (hasMeditation) {
                                                            Modifier.border(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                                shape = CircleShape
                                                            )
                                                        } else if (isToday) {
                                                            Modifier.border(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                                shape = CircleShape
                                                            )
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable(enabled = hasMeditation) {
                                                        onSelectDate(cellDate)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$dayNumber",
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    autoSize = TextAutoSize.StepBased(
                                                        minFontSize = 8.sp,
                                                        maxFontSize = 13.sp,
                                                        stepSize = 0.5.sp
                                                    ),
                                                    fontWeight = if (hasMeditation || isToday) FontWeight.Bold else FontWeight.Normal,
                                                    color = dayTextColor,
                                                    style = TextStyle(fontFeatureSettings = "tnum")
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (sessionHistoryList.isEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.stats_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (onExportHistory != null || onImportHistory != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onExportHistory != null) {
                            OutlinedButton(
                                onClick = onExportHistory,
                                enabled = sessionHistoryList.isNotEmpty(),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_export_history),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (onImportHistory != null) {
                            OutlinedButton(
                                onClick = onImportHistory,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.button_import_history),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}

@Composable
internal fun StillDayDetailDialog(
    selectedDate: LocalDate,
    sessionHistoryList: List<SessionLogEntry>,
    onRequestDeleteSession: (SessionLogEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val locale = Locale.getDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val dateTitleFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    }
    val daySessions = remember(sessionHistoryList, selectedDate, zone) {
        sessionHistoryList
            .filter { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() == selectedDate }
            .sortedBy { it.startEpochSec }
    }

    if (daySessions.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
    } else {
        val dayTotalMinutes = daySessions.sumOf { it.durationMinutes }
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Column {
                    Text(
                        text = selectedDate.format(dateTitleFormatter)
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (daySessions.size == 1) {
                            stringResource(R.string.stats_day_single_summary, dayTotalMinutes)
                        } else {
                            stringResource(R.string.stats_day_total_summary, daySessions.size, dayTotalMinutes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(daySessions, key = { it.rawString }) { session ->
                        val startStr = Instant.ofEpochSecond(session.startEpochSec).atZone(zone).format(timeFormatter)
                        val endStr = Instant.ofEpochSecond(session.endEpochSec).atZone(zone).format(timeFormatter)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.stats_min_format, session.durationMinutes),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.stats_session_time_range, startStr, endStr),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val hrLabel = when {
                                        session.startBpm != null && session.endBpm != null && session.startBpm != session.endBpm -> {
                                            stringResource(R.string.stats_session_hr_range, session.startBpm, session.endBpm)
                                        }
                                        session.avgBpm != null -> {
                                            stringResource(R.string.stats_session_hr_avg, session.avgBpm)
                                        }
                                        session.startBpm != null -> {
                                            stringResource(R.string.stats_session_hr_avg, session.startBpm)
                                        }
                                        session.endBpm != null -> {
                                            stringResource(R.string.stats_session_hr_avg, session.endBpm)
                                        }
                                        else -> null
                                    }
                                    if (hrLabel != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = hrLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { onRequestDeleteSession(session) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.button_delete),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.button_close))
                }
            }
        )
    }
}

@Composable
internal fun StillDeleteSessionDialog(
    session: SessionLogEntry,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Text(stringResource(R.string.dialog_delete_message, session.durationMinutes, session.dateFormatted))
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.button_delete))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
