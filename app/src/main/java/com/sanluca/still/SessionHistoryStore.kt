package com.sanluca.still

import android.content.Context
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

// Lock condiviso da MainActivity e WearListenerService per i read-modify-write su storico e coda Health Connect
private val historyPrefsLock = Any()

data class SessionLogEntry(
    val id: Long,
    val dateFormatted: String,
    val durationMinutes: Int,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val rawString: String,
    val startBpm: Int? = null,
    val endBpm: Int? = null,
    val avgBpm: Int? = null
)

data class PendingSessionEntry(
    val durationSec: Int,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val rawString: String
)

fun savePendingWatchSession(context: Context, durationSec: Int, startInstant: Instant, endInstant: Instant) {
    val appContext = context.applicationContext ?: context
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
        val raw = "$durationSec|${startInstant.epochSecond}|${endInstant.epochSecond}"
        val updatedList = existingSet.toMutableList()
        if (!updatedList.contains(raw)) {
            updatedList.add(raw)
            if (updatedList.size > 100) {
                updatedList.sortBy { entry ->
                    entry.split("|").getOrNull(1)?.toLongOrNull() ?: 0L
                }
                while (updatedList.size > 100) {
                    updatedList.removeAt(0)
                }
            }
            prefs.edit().putStringSet(KEY_PENDING_HEALTH_SESSIONS, updatedList.toSet()).apply()
        }
    }
}

fun getPendingWatchSessions(context: Context): List<PendingSessionEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
    return set.mapNotNull { raw ->
        val parts = raw.split("|")
        if (parts.size >= 3) {
            val dur = parts[0].toIntOrNull() ?: 0
            val start = parts[1].toLongOrNull() ?: 0L
            val end = parts[2].toLongOrNull() ?: 0L
            PendingSessionEntry(dur, start, end, raw)
        } else null
    }
}

fun clearPendingWatchSessions(context: Context, rawList: List<String>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_PENDING_HEALTH_SESSIONS, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        updatedSet.removeAll(rawList.toSet())
        prefs.edit().putStringSet(KEY_PENDING_HEALTH_SESSIONS, updatedSet).apply()
    }
}

fun addSessionToLocalHistory(
    context: Context,
    durationSeconds: Int,
    startInstant: Instant,
    endInstant: Instant,
    startBpm: Int? = null,
    endBpm: Int? = null,
    avgBpm: Int? = null
) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val timestamp = startInstant.toEpochMilli()
    val minutes = (durationSeconds / 60).coerceAtLeast(1)
    val startSec = startInstant.epochSecond
    val endSec = endInstant.epochSecond
    val validStartBpm = startBpm?.takeIf { it in 30..220 }
    val validEndBpm = endBpm?.takeIf { it in 30..220 }
    val validAvgBpm = avgBpm?.takeIf { it in 30..220 }
    val hasHr = validStartBpm != null || validEndBpm != null || validAvgBpm != null
    val newEntry = if (hasHr) {
        "$timestamp|$minutes|$startSec|$endSec|${validStartBpm ?: 0}|${validEndBpm ?: 0}|${validAvgBpm ?: 0}"
    } else {
        "$timestamp|$minutes|$startSec|$endSec"
    }

    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        updatedSet.add(newEntry)
        prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
    }
}

fun deleteSessionFromLocalHistory(context: Context, rawString: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        updatedSet.remove(rawString)
        prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
    }
}

fun getLocalSessionHistory(context: Context): List<SessionLogEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    return set.mapNotNull { raw ->
        val parts = raw.split("|")
        if (parts.size >= 2) {
            val ts = parts[0].toLongOrNull() ?: 0L
            val mins = parts[1].toIntOrNull() ?: 0
            val startSec = if (parts.size >= 4) parts[2].toLongOrNull() ?: (ts / 1000) else (ts / 1000)
            val endSec = if (parts.size >= 4) parts[3].toLongOrNull() ?: ((ts / 1000) + mins * 60) else ((ts / 1000) + mins * 60)
            val startBpm = parts.getOrNull(4)?.toIntOrNull()?.takeIf { it in 30..220 }
            val endBpm = parts.getOrNull(5)?.toIntOrNull()?.takeIf { it in 30..220 }
            val avgBpm = parts.getOrNull(6)?.toIntOrNull()?.takeIf { it in 30..220 }

            SessionLogEntry(
                id = ts,
                dateFormatted = sdf.format(Date(startSec * 1000L)), // Mostra l'Ora di INIZIO della meditazione per allinearsi a Google Salute
                durationMinutes = mins,
                startEpochSec = startSec,
                endEpochSec = endSec,
                rawString = raw,
                startBpm = startBpm,
                endBpm = endBpm,
                avgBpm = avgBpm
            )
        } else null
    }.sortedByDescending { it.startEpochSec }
}

fun calculateStreakDays(history: List<SessionLogEntry>): Int {
    if (history.isEmpty()) return 0

    val zone = ZoneId.systemDefault()
    val sessionDates = history.mapTo(HashSet()) {
        Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate()
    }

    var streak = 0
    var currentDate = LocalDate.now(zone)

    // Se oggi non si è ancora meditato, partiamo a verificare da ieri senza interrompere la striscia
    if (!sessionDates.contains(currentDate)) {
        currentDate = currentDate.minusDays(1)
    }

    while (sessionDates.contains(currentDate)) {
        streak++
        currentDate = currentDate.minusDays(1)
    }
    return streak
}

fun calculateBestStreakDays(history: List<SessionLogEntry>): Int {
    if (history.isEmpty()) return 0

    val zone = ZoneId.systemDefault()
    val sortedDates = history
        .map { Instant.ofEpochSecond(it.startEpochSec).atZone(zone).toLocalDate() }
        .distinct()
        .sorted()

    if (sortedDates.isEmpty()) return 0

    var bestStreak = 1
    var currentStreak = 1

    for (i in 1 until sortedDates.size) {
        if (sortedDates[i] == sortedDates[i - 1].plusDays(1)) {
            currentStreak++
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
            }
        } else {
            currentStreak = 1
        }
    }
    return bestStreak
}

data class HistoryImportResult(
    val importedCount: Int,
    val skippedDuplicates: Int,
    val invalidCount: Int
)

private const val CSV_HEADER =
    "date_time,duration_minutes,start_epoch_sec,end_epoch_sec,start_bpm,end_bpm,avg_bpm"

private val CSV_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

private val CSV_PARSE_DATE_TIME_FORMATTERS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.US),
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.US)
)

private fun escapeCsvField(value: String): String {
    val needsQuotes = value.any { it == ',' || it == ';' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
}

private fun detectCsvDelimiter(line: String): Char {
    var inQuotes = false
    var commaCount = 0
    var semicolonCount = 0
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (ch == '"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                i += 2
                continue
            }
            inQuotes = !inQuotes
        } else if (!inQuotes) {
            if (ch == ',') commaCount++
            else if (ch == ';') semicolonCount++
        }
        i++
    }
    return if (semicolonCount > commaCount) ';' else ','
}

private fun parseCsvLine(line: String, delimiter: Char): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (inQuotes) {
            if (ch == '"') {
                if (i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i += 2
                    continue
                } else {
                    inQuotes = false
                }
            } else {
                current.append(ch)
            }
        } else {
            when (ch) {
                '"' -> inQuotes = true
                delimiter -> {
                    fields.add(current.toString().trim())
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
        }
        i++
    }
    fields.add(current.toString().trim())
    return fields
}

private fun parseDateTimeToEpochSec(raw: String, zone: ZoneId): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    try {
        return Instant.parse(trimmed).epochSecond
    } catch (_: DateTimeParseException) {
    }
    for (formatter in CSV_PARSE_DATE_TIME_FORMATTERS) {
        try {
            return LocalDateTime.parse(trimmed, formatter).atZone(zone).toEpochSecond()
        } catch (_: DateTimeParseException) {
        }
    }
    return null
}

fun exportSessionHistoryToCsv(context: Context): String {
    val history = getLocalSessionHistory(context)
    val zone = ZoneId.systemDefault()
    val builder = StringBuilder()
    builder.append(CSV_HEADER)
    for (entry in history) {
        val dateTime = Instant.ofEpochSecond(entry.startEpochSec)
            .atZone(zone)
            .format(CSV_DATE_TIME_FORMATTER)
        val fields = listOf(
            dateTime,
            entry.durationMinutes.toString(),
            entry.startEpochSec.toString(),
            entry.endEpochSec.toString(),
            entry.startBpm?.toString().orEmpty(),
            entry.endBpm?.toString().orEmpty(),
            entry.avgBpm?.toString().orEmpty()
        )
        builder.append('\n')
        builder.append(fields.joinToString(",") { escapeCsvField(it) })
    }
    return builder.toString()
}

fun importSessionHistoryFromCsv(context: Context, csvContent: String): HistoryImportResult {
    val cleanedContent = csvContent.removePrefix("\uFEFF")
    val nonEmptyLines = cleanedContent
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()
    if (nonEmptyLines.isEmpty()) {
        throw IllegalArgumentException("Empty CSV content")
    }

    val delimiter = detectCsvDelimiter(nonEmptyLines.first())
    val firstRow = parseCsvLine(nonEmptyLines.first(), delimiter)
    val normalizedHeader = firstRow.map { it.trim().lowercase(Locale.US) }
    val knownHeaders = setOf(
        "date_time",
        "duration_minutes",
        "start_epoch_sec",
        "end_epoch_sec",
        "start_bpm",
        "end_bpm",
        "avg_bpm"
    )
    val hasHeader = normalizedHeader.any { it in knownHeaders }

    val dateTimeIdx = if (hasHeader) normalizedHeader.indexOf("date_time") else 0
    val durationIdx = if (hasHeader) normalizedHeader.indexOf("duration_minutes") else 1
    val startEpochIdx = if (hasHeader) normalizedHeader.indexOf("start_epoch_sec") else 2
    val endEpochIdx = if (hasHeader) normalizedHeader.indexOf("end_epoch_sec") else 3
    val startBpmIdx = if (hasHeader) normalizedHeader.indexOf("start_bpm") else 4
    val endBpmIdx = if (hasHeader) normalizedHeader.indexOf("end_bpm") else 5
    val avgBpmIdx = if (hasHeader) normalizedHeader.indexOf("avg_bpm") else 6

    val dataLines = if (hasHeader) nonEmptyLines.drop(1) else nonEmptyLines
    val zone = ZoneId.systemDefault()

    val appContext = context.applicationContext ?: context
    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var importedCount = 0
    var skippedDuplicates = 0
    var invalidCount = 0

    synchronized(historyPrefsLock) {
        val existingSet = prefs.getStringSet(KEY_SESSION_HISTORY, emptySet()) ?: emptySet()
        val updatedSet = existingSet.toMutableSet()
        val existingKeys = HashSet<Pair<Long, Long>>(existingSet.size)

        for (raw in existingSet) {
            val parts = raw.split("|")
            if (parts.size >= 2) {
                val ts = parts[0].toLongOrNull() ?: 0L
                val mins = parts[1].toIntOrNull() ?: 0
                val startSec = if (parts.size >= 4) parts[2].toLongOrNull() ?: (ts / 1000L) else (ts / 1000L)
                val endSec = if (parts.size >= 4) parts[3].toLongOrNull() ?: ((ts / 1000L) + mins * 60L) else ((ts / 1000L) + mins * 60L)
                if (startSec > 0L && endSec >= startSec) {
                    existingKeys.add(startSec to endSec)
                }
            }
        }

        for (line in dataLines) {
            val cells = parseCsvLine(line, delimiter)
            fun cell(index: Int): String = if (index in cells.indices) cells[index].trim() else ""

            val rawDateTime = cell(dateTimeIdx)
            val rawDuration = cell(durationIdx).toIntOrNull()
            var startEpochSec = cell(startEpochIdx).toLongOrNull() ?: -1L
            var endEpochSec = cell(endEpochIdx).toLongOrNull() ?: -1L

            if ((startEpochSec <= 0L || endEpochSec < startEpochSec) &&
                rawDateTime.isNotEmpty() &&
                rawDuration != null &&
                rawDuration > 0
            ) {
                val parsedStart = parseDateTimeToEpochSec(rawDateTime, zone)
                if (parsedStart != null && parsedStart > 0L) {
                    startEpochSec = parsedStart
                    endEpochSec = parsedStart + rawDuration * 60L
                }
            }

            if (startEpochSec <= 0L || endEpochSec < startEpochSec) {
                invalidCount++
                continue
            }

            val durationMinutes = if (rawDuration != null && rawDuration > 0) {
                rawDuration
            } else {
                ((endEpochSec - startEpochSec) / 60L).toInt().coerceAtLeast(1)
            }
            val timestamp = startEpochSec * 1000L
            val startBpm = cell(startBpmIdx).toIntOrNull()?.takeIf { it in 30..220 }
            val endBpm = cell(endBpmIdx).toIntOrNull()?.takeIf { it in 30..220 }
            val avgBpm = cell(avgBpmIdx).toIntOrNull()?.takeIf { it in 30..220 }

            val dedupKey = startEpochSec to endEpochSec
            if (!existingKeys.add(dedupKey)) {
                skippedDuplicates++
                continue
            }

            val hasHr = startBpm != null || endBpm != null || avgBpm != null
            val rawEntry = if (hasHr) {
                "$timestamp|$durationMinutes|$startEpochSec|$endEpochSec|${startBpm ?: 0}|${endBpm ?: 0}|${avgBpm ?: 0}"
            } else {
                "$timestamp|$durationMinutes|$startEpochSec|$endEpochSec"
            }
            updatedSet.add(rawEntry)
            importedCount++
        }

        if (importedCount > 0) {
            prefs.edit().putStringSet(KEY_SESSION_HISTORY, updatedSet).apply()
        }
    }

    if (!hasHeader && importedCount == 0 && skippedDuplicates == 0 && invalidCount > 0) {
        throw IllegalArgumentException("No valid CSV session rows found")
    }

    return HistoryImportResult(
        importedCount = importedCount,
        skippedDuplicates = skippedDuplicates,
        invalidCount = invalidCount
    )
}
