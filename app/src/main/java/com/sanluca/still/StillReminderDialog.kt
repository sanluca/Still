package com.sanluca.still

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private fun formatReminderTime(context: Context, hour: Int, minute: Int): String {
    val safeTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    return if (DateFormat.is24HourFormat(context)) {
        String.format(Locale.getDefault(), "%02d:%02d", safeTime.hour, safeTime.minute)
    } else {
        safeTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))
    }
}

internal fun formatReminderSummary(context: Context, settings: MeditationReminderSettings): String {
    if (!settings.enabled) {
        return context.getString(R.string.reminder_status_off)
    }
    val timeText = formatReminderTime(context, settings.hour, settings.minute)
    val freqText = when (settings.frequency) {
        ReminderFrequency.DAILY -> context.getString(R.string.reminder_freq_daily)
        ReminderFrequency.EVERY_2_DAYS -> context.getString(R.string.reminder_freq_every_2_days)
        ReminderFrequency.EVERY_3_DAYS -> context.getString(R.string.reminder_freq_every_3_days)
        ReminderFrequency.WEEKDAYS -> {
            val locale = Locale.getDefault()
            val sortedDays = settings.selectedDays.filter { it in 1..7 }.sorted()
            if (sortedDays.size == 7) {
                context.getString(R.string.reminder_freq_daily)
            } else {
                sortedDays.joinToString(", ") { dayVal ->
                    DayOfWeek.of(dayVal)
                        .getDisplayName(TextStyle.SHORT, locale)
                        .replaceFirstChar { ch -> ch.titlecase(locale) }
                }
            }
        }
    }
    return "$freqText · $timeText"
}

@Composable
internal fun StillReminderDialog(
    settings: MeditationReminderSettings,
    onSettingsChange: (MeditationReminderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    var permissionDeniedHint by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember {
        mutableStateOf(
            hasReminderNotificationPermission(context) && areReminderNotificationsEnabled(context)
        )
    }
    DisposableEffect(lifecycleOwner, settings.enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = hasReminderNotificationPermission(context) &&
                    areReminderNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionDeniedToastMsg = stringResource(R.string.reminder_permission_denied_toast)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted && areReminderNotificationsEnabled(context)
        if (granted) {
            permissionDeniedHint = false
            onSettingsChange(settings.copy(enabled = true))
        } else {
            permissionDeniedHint = true
            Toast.makeText(
                context,
                permissionDeniedToastMsg,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_reminder_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. ATTIVA / DISATTIVA
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = stringResource(R.string.reminder_enable_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    if (settings.enabled) R.string.reminder_enable_active
                                    else R.string.reminder_enable_inactive
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { wantEnabled ->
                                if (!wantEnabled) {
                                    permissionDeniedHint = false
                                    onSettingsChange(settings.copy(enabled = false))
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !hasReminderNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    permissionDeniedHint = false
                                    onSettingsChange(settings.copy(enabled = true))
                                }
                            }
                        )
                    }
                }

                if (settings.enabled) {
                    // 2. ORARIO E FREQUENZA
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_time_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = {
                                        TimePickerDialog(
                                            context,
                                            { _, pickedHour, pickedMinute ->
                                                onSettingsChange(
                                                    settings.copy(
                                                        hour = pickedHour,
                                                        minute = pickedMinute
                                                    )
                                                )
                                            },
                                            settings.hour,
                                            settings.minute,
                                            DateFormat.is24HourFormat(context)
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = formatReminderTime(context, settings.hour, settings.minute),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.reminder_frequency_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    ReminderFrequency.DAILY to R.string.reminder_freq_daily,
                                    ReminderFrequency.WEEKDAYS to R.string.reminder_freq_weekdays,
                                    ReminderFrequency.EVERY_2_DAYS to R.string.reminder_freq_every_2_days,
                                    ReminderFrequency.EVERY_3_DAYS to R.string.reminder_freq_every_3_days
                                ).forEach { (freq, labelRes) ->
                                    FilterChip(
                                        selected = settings.frequency == freq,
                                        onClick = {
                                            onSettingsChange(settings.copy(frequency = freq))
                                        },
                                        label = { Text(stringResource(labelRes), fontSize = 12.sp) }
                                    )
                                }
                            }

                            if (settings.frequency == ReminderFrequency.WEEKDAYS) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DayOfWeek.entries.forEach { day ->
                                        val isSelected = day.value in settings.selectedDays
                                        val dayLabel = day
                                            .getDisplayName(TextStyle.SHORT, locale)
                                            .replaceFirstChar { ch -> ch.titlecase(locale) }
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                val updated = settings.selectedDays.toMutableSet()
                                                if (isSelected) {
                                                    if (updated.size > 1) {
                                                        updated.remove(day.value)
                                                        onSettingsChange(settings.copy(selectedDays = updated))
                                                    }
                                                } else {
                                                    updated.add(day.value)
                                                    onSettingsChange(settings.copy(selectedDays = updated))
                                                }
                                            },
                                            label = { Text(dayLabel, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. IMPOSTAZIONI NOTIFICA ANDROID (SUONO / SILENZIOSO)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.reminder_soft_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!notificationsAllowed) {
                                Text(
                                    text = stringResource(R.string.reminder_system_disabled_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedButton(
                                onClick = { openReminderNotificationSettings(context) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_system_settings_button),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else if (permissionDeniedHint) {
                    TextButton(
                        onClick = { openReminderNotificationSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.reminder_system_settings_button),
                            fontSize = 12.sp
                        )
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
