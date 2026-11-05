package com.jaysato.xiaomihealthbridge

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

object WorkoutRouteStore {
    internal const val PREFS = "workout_routes"

    data class Point(val time: Long, val latitude: Double, val longitude: Double, val altitude: Double?)
    data class Workout(val start: Long, val end: Long, val points: List<Point>)

    fun save(context: Context, start: Long, end: Long, encodedPoints: Array<String>): Boolean =
        save(start, end, encodedPoints) { route ->
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("start", start)
                .putLong("end", end)
                .putString("points", route)
                .commit()
        }

    internal fun save(
        start: Long,
        end: Long,
        encodedPoints: Array<String>,
        persist: (String) -> Boolean
    ): Boolean {
        val ordered = encodedPoints.mapNotNull(::decode).filter { it.time in start until end }.sortedBy { it.time }
        val valid = ordered.fold(mutableListOf<Point>()) { accepted, point ->
            val previous = accepted.lastOrNull()
            if (previous == null || plausible(previous, point)) accepted += point
            accepted
        }
        if (valid.size < 2) return true
        val route = valid.joinToString("\n") { "${it.time},${it.latitude},${it.longitude},${it.altitude ?: ""}" }
        return persist(route)
    }

    fun latest(context: Context): Workout? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong("start", 0L)
        val end = prefs.getLong("end", 0L)
        val points = prefs.getString("points", null)?.lineSequence()?.mapNotNull(::decode)?.toList().orEmpty()
        return if (start > 0L && end > start && points.size >= 2) Workout(start, end, points) else null
    }

    private fun decode(value: String): Point? {
        val parts = value.split(',')
        val time = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val latitude = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        val longitude = parts.getOrNull(2)?.toDoubleOrNull() ?: return null
        val altitude = parts.getOrNull(3)?.toDoubleOrNull()
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Point(time, latitude, longitude, altitude)
    }

    private fun plausible(first: Point, second: Point): Boolean {
        val seconds = (second.time - first.time) / 1000.0
        if (seconds <= 0.0) return false
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) * sin(dLon / 2).pow(2)
        val meters = 6_371_000.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
        return meters / seconds <= 45.0
    }
}

internal object WorkoutRouteDisplayPolicy {
    private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    fun recordedStart(timestampMillis: Long, zone: ZoneId): String =
        formatter.format(Instant.ofEpochMilli(timestampMillis).atZone(zone))
}
