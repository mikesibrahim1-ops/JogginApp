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

/**
 * A target the user has achieved — recorded for the "Personal Targets" (achievements)
 * list. One entry is created the first time a target is met within a given period
 * (keyed by [targetId] + [periodKey] so completing a daily target again on a later day
 * records a fresh achievement, but logging more reps the same day does not duplicate it).
 */
data class CompletedTarget(
    val id: String = UUID.randomUUID().toString(),
    val targetId: String = "",
    val periodKey: String = "",
    val type: ExerciseType = ExerciseType.SITUPS,
    val period: TargetPeriod = TargetPeriod.DAILY,
    val amount: Float = 0f,
    val achievedAt: Long = System.currentTimeMillis()
)

/** Stable key identifying the period instance a target was completed in. */
fun periodKeyFor(period: TargetPeriod, now: Long = System.currentTimeMillis()): String {
    val start = periodStart(period, now)
    return "${period.name}:$start"
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

// ── Grouping completed targets (Personal Targets list) ───────────────────────────

/** Granularity for grouping achieved targets on the Personal Targets screen. */
enum class CompletionGrouping { DAY, WEEK, MONTH }

/**
 * A bucket of [CompletedTarget]s that were achieved within the same day / week / month,
 * identified by [bucketStart] (start-of-period epoch millis) for ordering & header text.
 */
data class CompletedGroup(
    val bucketStart: Long,
    val items: List<CompletedTarget>
)

/** Map a [CompletionGrouping] to the matching [TargetPeriod] boundary. */
private fun CompletionGrouping.asPeriod(): TargetPeriod = when (this) {
    CompletionGrouping.DAY -> TargetPeriod.DAILY
    CompletionGrouping.WEEK -> TargetPeriod.WEEKLY
    CompletionGrouping.MONTH -> TargetPeriod.MONTHLY
}

/**
 * Group [completed] targets into day/week/month buckets by their [CompletedTarget.achievedAt]
 * timestamp. Buckets are ordered newest-first, and the items within each bucket keep their
 * incoming order (callers pass newest-first).
 */
fun groupCompleted(
    completed: List<CompletedTarget>,
    grouping: CompletionGrouping
): List<CompletedGroup> {
    val period = grouping.asPeriod()
    return completed
        .groupBy { periodStart(period, it.achievedAt) }
        .map { (start, items) -> CompletedGroup(bucketStart = start, items = items) }
        .sortedByDescending { it.bucketStart }
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
    // Only count activity from the current period AND from after the target was created,
    // so a brand-new target starts at 0 progress (pre-existing reps/routes logged earlier
    // in the same period must NOT auto-complete it). The effective window start is the
    // later of the period start and the target's creation time.
    val start = maxOf(periodStart(t.period, now), t.createdAt)
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
    // Deductions (negative rep-log entries) can push the raw sum below zero; clamp to 0
    // so progress never shows a negative value.
    TargetProgress(target = t, current = current.coerceAtLeast(0f), goal = t.amount)
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

    /** Logs [count] reps of [type] now. Positive adds, negative deducts. Returns the updated log. */
    fun logReps(context: Context, type: ExerciseType, count: Int): List<RepLogEntry> {
        if (count == 0) return loadRepLog(context)
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

    // ── Completed / achieved targets (Personal Targets list) ──
    private const val KEY_COMPLETED = "exercise_completed"

    fun loadCompleted(context: Context): List<CompletedTarget> {
        val json = prefs(context).getString(KEY_COMPLETED, null) ?: return emptyList()
        val type = object : TypeToken<List<CompletedTarget>>() {}.type
        return try { Gson().fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun saveCompleted(context: Context, list: List<CompletedTarget>) {
        prefs(context).edit().putString(KEY_COMPLETED, Gson().toJson(list)).apply()
    }

    /**
     * Record [target] as achieved for the current period, if not already recorded for
     * that same period instance. Returns the newly-created [CompletedTarget] (so the UI
     * can congratulate), or null if this completion was already recorded.
     */
    fun recordCompletionIfNew(
        context: Context,
        target: ExerciseTarget,
        now: Long = System.currentTimeMillis()
    ): CompletedTarget? {
        val key = periodKeyFor(target.period, now)
        val list = loadCompleted(context).toMutableList()
        if (list.any { it.targetId == target.id && it.periodKey == key }) return null
        val entry = CompletedTarget(
            targetId = target.id, periodKey = key, type = target.type,
            period = target.period, amount = target.amount, achievedAt = now
        )
        list.add(0, entry)
        saveCompleted(context, list)
        return entry
    }

    fun clearCompleted(context: Context) {
        prefs(context).edit().remove(KEY_COMPLETED).apply()
    }
}
