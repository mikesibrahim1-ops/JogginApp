package com.example.joggingapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Custom Exercise Targets feature.
 *
 * Lets the user set personal goals for at-home / everyday exercises over a chosen
 * period (daily / weekly / monthly) and track progress toward them:
 *   - Manual-count exercises (sit-ups, push-ups, squats): the user logs reps with a
 *     "+" button; progress is the sum of logged reps in the current period.
 *   - Distance exercises (walking, running): progress is auto-computed from the GPS
 *     [SavedRoute] history (no manual logging needed) — the distance covered in the
 *     current period for that activity type.
 *
 * Everything is persisted in SharedPreferences ("jog_prefs") via Gson, mirroring the
 * [RouteStorage] convention. Progress itself is computed on demand (never stored).
 */

/** What is being targeted. Manual types are counted; distance types read from routes. */
enum class ExerciseType(val isDistance: Boolean, val defaultEmoji: String) {
    SITUPS(false, "🧎"),
    PUSHUPS(false, "💪"),
    SQUATS(false, "🏋️"),
    WALKING(true, "🚶"),
    RUNNING(true, "🏃");

    /** Activity-type Int (0=Walk,1=Run,2=Cycle) a distance target maps to, else null. */
    val activityMode: Int?
        get() = when (this) {
            WALKING -> 0
            RUNNING -> 1
            else -> null
        }
}

/** The period a target resets over. */
enum class TargetPeriod { DAILY, WEEKLY, MONTHLY }

/**
 * A user-created target. [amount] is reps for manual types, or kilometres for distance
 * types. Stored; new fields must keep defaults for backward-compatible deserialization.
 */
data class ExerciseTarget(
    val id: String = UUID.randomUUID().toString(),
    val type: ExerciseType = ExerciseType.SITUPS,
    val period: TargetPeriod = TargetPeriod.DAILY,
    val amount: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

/** A single logged bout of a manual exercise (e.g. "20 push-ups at time T"). */
data class RepLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: ExerciseType = ExerciseType.SITUPS,
    val count: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/** Computed progress for a target: current value, target value, 0..1 fraction. */
data class TargetProgress(
    val target: ExerciseTarget,
    val current: Float,
    val goal: Float
) {
    val fraction: Float get() = if (goal <= 0f) 0f else (current / goal).coerceIn(0f, 1f)
    val isMet: Boolean get() = goal > 0f && current >= goal
}

// ── Period boundary helpers ─────────────────────────────────────────────────────

/** Start-of-period epoch millis for the period containing [now]. */
fun periodStart(period: TargetPeriod, now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    when (period) {
        TargetPeriod.DAILY -> { /* already at midnight today */ }
        TargetPeriod.WEEKLY -> {
            // Roll back to the first day of the week (locale-aware firstDayOfWeek).
            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            // If that pushed into the future (edge of week numbering), step back a week.
            if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        TargetPeriod.MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

// ── Progress computation ─────────────────────────────────────────────────────────

/**
 * Compute progress for every [target], given the full GPS route history and the manual
 * rep log. Distance targets sum [calcDistanceKm] over routes (of the matching activity
 * mode) whose timestamp falls in the current period; manual targets sum rep-log counts
 * in the current period.
 */
fun evaluateTargets(
    targets: List<ExerciseTarget>,
    routes: List<SavedRoute>,
    repLog: List<RepLogEntry>,
    now: Long = System.currentTimeMillis()
): List<TargetProgress> = targets.map { t ->
    val start = periodStart(t.period, now)
    val current: Float = if (t.type.isDistance) {
        val mode = t.type.activityMode
        routes.asSequence()
            .filter { it.timestamp in start..now }
            .filter { r ->
                val types = r.activityTypes ?: listOf(r.activityType)
                mode != null && mode in types
            }
            .sumOf { calcDistanceKm(it.points) }
            .toFloat()
    } else {
        repLog.asSequence()
            .filter { it.type == t.type && it.timestamp in start..now }
            .sumOf { it.count }
            .toFloat()
    }
    TargetProgress(target = t, current = current, goal = t.amount)
}

// ── Storage (SharedPreferences + Gson), mirroring RouteStorage ───────────────────

object ExerciseTargetStorage {
    private const val KEY_TARGETS = "exercise_targets"
    private const val KEY_REPLOG = "exercise_rep_log"
    const val MAX_TARGETS = 20

    private fun prefs(context: Context) =
        context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)

    // ── Targets ──
    fun loadTargets(context: Context): List<ExerciseTarget> {
        val json = prefs(context).getString(KEY_TARGETS, null) ?: return emptyList()
        val type = object : TypeToken<List<ExerciseTarget>>() {}.type
        return try { Gson().fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun saveTargets(context: Context, list: List<ExerciseTarget>) {
        prefs(context).edit().putString(KEY_TARGETS, Gson().toJson(list)).apply()
    }

    /** Adds a target (capped at [MAX_TARGETS]). Returns the updated list. */
    fun addTarget(context: Context, target: ExerciseTarget): List<ExerciseTarget> {
        val list = loadTargets(context).toMutableList()
        if (list.size >= MAX_TARGETS) return list
        list.add(0, target)
        saveTargets(context, list)
        return list
    }

    fun deleteTarget(context: Context, id: String): List<ExerciseTarget> {
        val list = loadTargets(context).filterNot { it.id == id }
        saveTargets(context, list)
        return list
    }

    fun clearTargets(context: Context) {
        prefs(context).edit().remove(KEY_TARGETS).apply()
    }

    // ── Manual rep log ──
    fun loadRepLog(context: Context): List<RepLogEntry> {
        val json = prefs(context).getString(KEY_REPLOG, null) ?: return emptyList()
        val type = object : TypeToken<List<RepLogEntry>>() {}.type
        return try { Gson().fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun saveRepLog(context: Context, list: List<RepLogEntry>) {
        prefs(context).edit().putString(KEY_REPLOG, Gson().toJson(list)).apply()
    }

    /** Logs [count] reps of [type] now. Returns the updated log. */
    fun logReps(context: Context, type: ExerciseType, count: Int): List<RepLogEntry> {
        if (count <= 0) return loadRepLog(context)
        val list = loadRepLog(context).toMutableList()
        list.add(0, RepLogEntry(type = type, count = count))
        // Prune entries older than ~400 days so the log can't grow without bound; period
        // windows never look back that far, so this is lossless for progress purposes.
        val cutoff = System.currentTimeMillis() - 400L * 86_400_000L
        val pruned = list.filter { it.timestamp >= cutoff }
        saveRepLog(context, pruned)
        return pruned
    }

    fun clearRepLog(context: Context) {
        prefs(context).edit().remove(KEY_REPLOG).apply()
    }
}
