package com.example.joggingapp

// ── Data model ────────────────────────────────────────────────────────────────

enum class AchievementTier { BRONZE, SILVER, GOLD, SPECIAL }
enum class AchievementCategory { WALKING, RUNNING, CYCLING, STREAKS, SPECIAL }

data class Achievement(
    val id: String,
    val tier: AchievementTier,
    val category: AchievementCategory,
    val title: String,
    val description: String,
    val icon: String,               // emoji icon
    val requirement: Float,         // target value (km, days, etc.)
    val requirementUnit: String,    // "km_single", "km_total", "km_day", "days_streak", "special"
)

// ── Achievement catalogue ─────────────────────────────────────────────────────

val ALL_ACHIEVEMENTS = listOf(
    // Walking
    Achievement("walk_first",    AchievementTier.BRONZE,  AchievementCategory.WALKING, "First Steps",      "Walk 1 km in a single event",          "👟", 1f,    "km_single"),
    Achievement("walk_5total",   AchievementTier.BRONZE,  AchievementCategory.WALKING, "Stroller",         "Walk 5 km total (cumulative)",          "🚶", 5f,    "km_total"),
    Achievement("walk_5day",     AchievementTier.SILVER,  AchievementCategory.WALKING, "Daily Wanderer",   "Walk 5 km in one day",                  "🌤️", 5f,    "km_day"),
    Achievement("walk_5single",  AchievementTier.SILVER,  AchievementCategory.WALKING, "Solid Stroll",     "Walk 5 km in one event",                "🎯", 5f,    "km_single"),
    Achievement("walk_10day",    AchievementTier.GOLD,    AchievementCategory.WALKING, "Ten K Day",        "Walk 10 km in one day",                 "🏅", 10f,   "km_day"),
    Achievement("walk_10single", AchievementTier.GOLD,    AchievementCategory.WALKING, "Marathon Walker",  "Walk 10 km in one event",               "🏆", 10f,   "km_single"),
    Achievement("walk_50total",  AchievementTier.GOLD,    AchievementCategory.WALKING, "Trail Blazer",     "Walk 50 km total (cumulative)",         "🥾", 50f,   "km_total"),
    Achievement("walk_early",    AchievementTier.SPECIAL, AchievementCategory.WALKING, "Early Bird",       "Complete a walk before 7:00 AM",        "🌅", 1f,    "special"),
    Achievement("walk_night",    AchievementTier.SPECIAL, AchievementCategory.WALKING, "Night Owl",        "Complete a walk after 9:00 PM",         "🌙", 1f,    "special"),
    // Running
    Achievement("run_first",     AchievementTier.BRONZE,  AchievementCategory.RUNNING, "Off the Couch",    "Run 1 km in a single event",            "🏃", 1f,    "km_single"),
    Achievement("run_5total",    AchievementTier.BRONZE,  AchievementCategory.RUNNING, "Warming Up",       "Run 5 km total (cumulative)",           "🔥", 5f,    "km_total"),
    Achievement("run_5single",   AchievementTier.SILVER,  AchievementCategory.RUNNING, "5K Finisher",      "Run 5 km in one event",                 "🎽", 5f,    "km_single"),
    Achievement("run_5day",      AchievementTier.SILVER,  AchievementCategory.RUNNING, "Daily Miler",      "Run 5 km in one day",                   "📏", 5f,    "km_day"),
    Achievement("run_10single",  AchievementTier.GOLD,    AchievementCategory.RUNNING, "10K Crusher",      "Run 10 km in one event",                "💪", 10f,   "km_single"),
    Achievement("run_10day",     AchievementTier.GOLD,    AchievementCategory.RUNNING, "Double Digits",    "Run 10 km in one day",                  "🏅", 10f,   "km_day"),
    Achievement("run_21single",  AchievementTier.GOLD,    AchievementCategory.RUNNING, "Half Marathon Hero","Run 21.1 km in one event",             "🏆", 21.1f, "km_single"),
    Achievement("run_100total",  AchievementTier.GOLD,    AchievementCategory.RUNNING, "Century Club",     "Run 100 km total (cumulative)",         "💯", 100f,  "km_total"),
    Achievement("run_speed",     AchievementTier.SPECIAL, AchievementCategory.RUNNING, "Speed Demon",      "Run 1 km in under 5 minutes",           "⚡", 1f,    "special"),
    Achievement("run_streak3",   AchievementTier.SPECIAL, AchievementCategory.RUNNING, "Consistent Runner","Run 3 days in a row",                   "📅", 3f,    "days_streak"),
    // Cycling
    Achievement("cycle_first",   AchievementTier.BRONZE,  AchievementCategory.CYCLING, "First Ride",       "Cycle 2 km in a single event",          "🚲", 2f,    "km_single"),
    Achievement("cycle_10total", AchievementTier.BRONZE,  AchievementCategory.CYCLING, "Pedal Pusher",     "Cycle 10 km total (cumulative)",        "⚙️", 10f,   "km_total"),
    Achievement("cycle_20single",AchievementTier.SILVER,  AchievementCategory.CYCLING, "Twenty K Ride",    "Cycle 20 km in one event",              "🛣️", 20f,   "km_single"),
    Achievement("cycle_20day",   AchievementTier.SILVER,  AchievementCategory.CYCLING, "Daily Rider",      "Cycle 20 km in one day",                "📊", 20f,   "km_day"),
    Achievement("cycle_50single",AchievementTier.GOLD,    AchievementCategory.CYCLING, "Fifty K Warrior",  "Cycle 50 km in one event",              "🏔️", 50f,   "km_single"),
    Achievement("cycle_50day",   AchievementTier.GOLD,    AchievementCategory.CYCLING, "Iron Legs",        "Cycle 50 km in one day",                "🦵", 50f,   "km_day"),
    Achievement("cycle_100total",AchievementTier.GOLD,    AchievementCategory.CYCLING, "Century Cyclist",  "Cycle 100 km total (cumulative)",       "💯", 100f,  "km_total"),
    Achievement("cycle_hill",    AchievementTier.SPECIAL, AchievementCategory.CYCLING, "Hill Climber",     "Cycle with 500m+ elevation in one event","⛰️", 500f, "special"),
    Achievement("cycle_speed",   AchievementTier.SPECIAL, AchievementCategory.CYCLING, "Speed Racer",      "Cycle 1 km in under 2 minutes",         "🏎️", 1f,   "special"),
    // Streaks
    Achievement("streak_2",      AchievementTier.BRONZE,  AchievementCategory.STREAKS, "Getting Started",  "Be active 2 days in a row",             "📆", 2f,    "days_streak"),
    Achievement("streak_7",      AchievementTier.SILVER,  AchievementCategory.STREAKS, "Week Warrior",     "Be active 7 days in a row",             "🗓️", 7f,    "days_streak"),
    Achievement("streak_14",     AchievementTier.GOLD,    AchievementCategory.STREAKS, "Fortnight Fighter","Be active 14 days in a row",            "📋", 14f,   "days_streak"),
    Achievement("streak_30",     AchievementTier.GOLD,    AchievementCategory.STREAKS, "Monthly Machine",  "Be active 30 days in a row",            "🏆", 30f,   "days_streak"),
    Achievement("streak_comeback",AchievementTier.SPECIAL,AchievementCategory.STREAKS, "Comeback Kid",     "Return after 7+ days of inactivity",    "🔄", 1f,    "special"),
    // Special
    Achievement("special_social", AchievementTier.SPECIAL,AchievementCategory.SPECIAL, "Social Butterfly", "Share an activity to social media",     "🦋", 1f,    "special"),
    Achievement("special_explore",AchievementTier.SPECIAL,AchievementCategory.SPECIAL, "Explorer",         "Complete activities in 3 different locations","🗺️",3f,"special"),
    Achievement("special_allround",AchievementTier.SPECIAL,AchievementCategory.SPECIAL,"All-Rounder",      "Walk, run, AND cycle in one week",      "🎯", 3f,    "special"),
    Achievement("special_rain",   AchievementTier.SPECIAL,AchievementCategory.SPECIAL, "Rain or Shine",    "Complete an outdoor activity in the rain","🌧️",1f,   "special"),
    Achievement("special_sunrise",AchievementTier.SPECIAL,AchievementCategory.SPECIAL, "Sunrise to Sunset","Activity before 8 AM and after 6 PM same day","🌅",2f,"special"),
)

// ── Progress evaluation ───────────────────────────────────────────────────────

data class AchievementProgress(
    val achievement: Achievement,
    val progress: Float,        // 0.0 – 1.0
    val earnedDate: String?,    // non-null = unlocked
)

fun evaluateAchievements(routes: List<SavedRoute>): List<AchievementProgress> {
    if (routes.isEmpty()) return ALL_ACHIEVEMENTS.map { AchievementProgress(it, 0f, null) }

    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    val daySdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())

    // ── Helper: activity type for a route (considers blended types) ──────────
    // A route "counts as" a type if that type appears in activityTypes (or matches activityType)
    fun routeHasType(r: SavedRoute, type: Int): Boolean {
        val types = r.activityTypes ?: listOf(r.activityType)
        return type in types
    }

    // ── Per-category route sets ───────────────────────────────────────────────
    val walkRoutes  = routes.filter { routeHasType(it, 0) }
    val runRoutes   = routes.filter { routeHasType(it, 1) }
    val cycleRoutes = routes.filter { routeHasType(it, 2) }

    // ── Distance helpers ──────────────────────────────────────────────────────
    fun distKm(r: SavedRoute) = calcDistanceKm(r.points).toFloat()

    // Total cumulative km
    val totalWalkKm  = walkRoutes.sumOf  { calcDistanceKm(it.points) }.toFloat()
    val totalRunKm   = runRoutes.sumOf   { calcDistanceKm(it.points) }.toFloat()
    val totalCycleKm = cycleRoutes.sumOf { calcDistanceKm(it.points) }.toFloat()

    // Best single-event km
    val bestWalkSingle  = walkRoutes.maxOfOrNull  { distKm(it) } ?: 0f
    val bestRunSingle   = runRoutes.maxOfOrNull   { distKm(it) } ?: 0f
    val bestCycleSingle = cycleRoutes.maxOfOrNull { distKm(it) } ?: 0f

    // Best single-day km (sum all same-day routes of that type)
    fun bestDayKm(typeRoutes: List<SavedRoute>): Float {
        return typeRoutes
            .groupBy { daySdf.format(java.util.Date(it.timestamp)) }
            .maxOfOrNull { (_, rs) -> rs.sumOf { calcDistanceKm(it.points) }.toFloat() } ?: 0f
    }
    val bestWalkDay  = bestDayKm(walkRoutes)
    val bestRunDay   = bestDayKm(runRoutes)
    val bestCycleDay = bestDayKm(cycleRoutes)

    // ── Streak helpers ────────────────────────────────────────────────────────
    // Returns the longest consecutive-day streak across all routes (any type)
    fun longestStreakDays(allRoutes: List<SavedRoute>): Int {
        val days = allRoutes
            .map { daySdf.format(java.util.Date(it.timestamp)).toLong() }
            .toSortedSet()
            .toList()
        if (days.isEmpty()) return 0
        var best = 1; var cur = 1
        for (i in 1 until days.size) {
            // Compare yyyyMMdd ints: consecutive days aren't simply +1 numerically,
            // so parse to Calendar to check actual day difference
            val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val d1 = fmt.parse(days[i - 1].toString()) ?: continue
            val d2 = fmt.parse(days[i].toString()) ?: continue
            val diffDays = ((d2.time - d1.time) / 86_400_000L).toInt()
            cur = if (diffDays == 1) cur + 1 else 1
            if (cur > best) best = cur
        }
        return best
    }

    // Returns streak for a specific type
    fun longestStreakForType(typeRoutes: List<SavedRoute>): Int = longestStreakDays(typeRoutes)

    val globalStreak = longestStreakDays(routes)
    val runStreak    = longestStreakForType(runRoutes)

    // Comeback: any gap of 7+ days between consecutive activity days
    val allActivityDays = routes
        .map { daySdf.format(java.util.Date(it.timestamp)).toLong() }
        .toSortedSet().toList()
    val hasComeback = run {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
        var found = false
        for (i in 1 until allActivityDays.size) {
            val d1 = fmt.parse(allActivityDays[i - 1].toString())
            val d2 = fmt.parse(allActivityDays[i].toString())
            if (d1 != null && d2 != null && (d2.time - d1.time) / 86_400_000L >= 7) {
                found = true; break
            }
        }
        found
    }

    // ── Speed helpers ─────────────────────────────────────────────────────────
    // Speed Demon: any run where avg moving speed >= 1km / 5min = 3.33 m/s
    val speedDemonAchieved = runRoutes.any { r ->
        val moving = r.points.filter { !it.lat.isNaN() && it.speed > 0f }
        val avg = if (moving.isNotEmpty()) moving.map { it.speed }.average() else 0.0
        avg >= (1000.0 / 300.0)   // 1 km in under 5 min = 200m/min = 3.33 m/s
    }

    // Speed Racer (cycling): avg moving speed >= 1km / 2min = 8.33 m/s
    val speedRacerAchieved = cycleRoutes.any { r ->
        val moving = r.points.filter { !it.lat.isNaN() && it.speed > 0f }
        val avg = if (moving.isNotEmpty()) moving.map { it.speed }.average() else 0.0
        avg >= (1000.0 / 120.0)   // 1 km in under 2 min
    }

    // ── Time-of-day helpers ───────────────────────────────────────────────────
    fun hourOfDay(ts: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ts
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }
    val hasEarlyBird = walkRoutes.any { hourOfDay(it.timestamp) < 7 }
    val hasNightOwl  = walkRoutes.any { hourOfDay(it.timestamp) >= 21 }

    // Sunrise to Sunset: both a before-8am AND after-6pm activity on the same calendar day
    val hasSunriseToSunset = run {
        val earlyDays  = routes.filter { hourOfDay(it.timestamp) < 8  }.map { daySdf.format(java.util.Date(it.timestamp)) }.toSet()
        val lateDays   = routes.filter { hourOfDay(it.timestamp) >= 18 }.map { daySdf.format(java.util.Date(it.timestamp)) }.toSet()
        earlyDays.intersect(lateDays).isNotEmpty()
    }

    // All-Rounder: walk + run + cycle all within the same 7-day rolling window
    val hasAllRounder = run {
        val sorted = routes.sortedBy { it.timestamp }
        var found = false
        for (i in sorted.indices) {
            val windowEnd = sorted[i].timestamp
            val windowStart = windowEnd - 7 * 86_400_000L
            val window = sorted.filter { it.timestamp in windowStart..windowEnd }
            val types = window.flatMap { r -> r.activityTypes ?: listOf(r.activityType) }.toSet()
            if (0 in types && 1 in types && 2 in types) { found = true; break }
        }
        found
    }

    // Explorer: activities in 3+ distinct rough locations (1-decimal-place lat/lon grid)
    val explorerCount = routes
        .filter { it.points.isNotEmpty() && !it.points.first().lat.isNaN() }
        .map { r ->
            val p = r.points.first { !it.lat.isNaN() }
            "${(p.lat * 10).toLong()}_${(p.lon * 10).toLong()}"
        }
        .toSet().size

    // ── Date helper: find the route that pushed an achievement over the line ──
    fun earnedDateForRoute(r: SavedRoute) = sdf.format(java.util.Date(r.timestamp))

    // For cumulative achievements, find the route where the running total crossed threshold
    fun earnedDateCumulative(typeRoutes: List<SavedRoute>, threshold: Float): String? {
        val sorted = typeRoutes.sortedBy { it.timestamp }
        var cum = 0f
        for (r in sorted) {
            cum += distKm(r)
            if (cum >= threshold) return earnedDateForRoute(r)
        }
        return null
    }

    // For single-event achievements
    fun earnedDateSingleEvent(typeRoutes: List<SavedRoute>, threshold: Float): String? =
        typeRoutes.filter { distKm(it) >= threshold }
            .minByOrNull { it.timestamp }
            ?.let { earnedDateForRoute(it) }

    // For single-day achievements
    fun earnedDateDay(typeRoutes: List<SavedRoute>, threshold: Float): String? {
        return typeRoutes
            .groupBy { daySdf.format(java.util.Date(it.timestamp)) }
            .filter { (_, rs) -> rs.sumOf { calcDistanceKm(it.points) }.toFloat() >= threshold }
            .keys.minOrNull()
            ?.let { dayStr ->
                val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                val parsed = fmt.parse(dayStr) ?: return@let null
                sdf.format(parsed)
            }
    }

    return ALL_ACHIEVEMENTS.map { a ->
        val (prog, earnedDate) = when (a.id) {

            // ── Walking ───────────────────────────────────────────────────────
            "walk_first"    -> Pair((bestWalkSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(walkRoutes, a.requirement))
            "walk_5total"   -> Pair((totalWalkKm / a.requirement).coerceAtMost(1f),
                                    earnedDateCumulative(walkRoutes, a.requirement))
            "walk_50total"  -> Pair((totalWalkKm / a.requirement).coerceAtMost(1f),
                                    earnedDateCumulative(walkRoutes, a.requirement))
            "walk_5single"  -> Pair((bestWalkSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(walkRoutes, a.requirement))
            "walk_10single" -> Pair((bestWalkSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(walkRoutes, a.requirement))
            "walk_5day"     -> Pair((bestWalkDay / a.requirement).coerceAtMost(1f),
                                    earnedDateDay(walkRoutes, a.requirement))
            "walk_10day"    -> Pair((bestWalkDay / a.requirement).coerceAtMost(1f),
                                    earnedDateDay(walkRoutes, a.requirement))
            "walk_early"    -> Pair(if (hasEarlyBird) 1f else 0f,
                                    if (hasEarlyBird) walkRoutes.filter { hourOfDay(it.timestamp) < 7 }.minByOrNull { it.timestamp }?.let { earnedDateForRoute(it) } else null)
            "walk_night"    -> Pair(if (hasNightOwl) 1f else 0f,
                                    if (hasNightOwl) walkRoutes.filter { hourOfDay(it.timestamp) >= 21 }.minByOrNull { it.timestamp }?.let { earnedDateForRoute(it) } else null)

            // ── Running ───────────────────────────────────────────────────────
            "run_first"     -> Pair((bestRunSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(runRoutes, a.requirement))
            "run_5total"    -> Pair((totalRunKm / a.requirement).coerceAtMost(1f),
                                    earnedDateCumulative(runRoutes, a.requirement))
            "run_5single"   -> Pair((bestRunSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(runRoutes, a.requirement))
            "run_5day"      -> Pair((bestRunDay / a.requirement).coerceAtMost(1f),
                                    earnedDateDay(runRoutes, a.requirement))
            "run_10single"  -> Pair((bestRunSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(runRoutes, a.requirement))
            "run_10day"     -> Pair((bestRunDay / a.requirement).coerceAtMost(1f),
                                    earnedDateDay(runRoutes, a.requirement))
            "run_21single"  -> Pair((bestRunSingle / a.requirement).coerceAtMost(1f),
                                    earnedDateSingleEvent(runRoutes, a.requirement))
            "run_100total"  -> Pair((totalRunKm / a.requirement).coerceAtMost(1f),
                                    earnedDateCumulative(runRoutes, a.requirement))
            "run_speed"     -> Pair(if (speedDemonAchieved) 1f else 0f,
                                    if (speedDemonAchieved) runRoutes.filter { r ->
                                        val mv = r.points.filter { !it.lat.isNaN() && it.speed > 0f }
                                        mv.isNotEmpty() && mv.map { it.speed }.average() >= 1000.0 / 300.0
                                    }.minByOrNull { it.timestamp }?.let { earnedDateForRoute(it) } else null)
            "run_streak3"   -> Pair((runStreak / a.requirement).coerceAtMost(1f),
                                    if (runStreak >= a.requirement.toInt()) earnedDateForRoute(runRoutes.maxBy { it.timestamp }) else null)

            // ── Cycling ───────────────────────────────────────────────────────
            "cycle_first"    -> Pair((bestCycleSingle / a.requirement).coerceAtMost(1f),
                                     earnedDateSingleEvent(cycleRoutes, a.requirement))
            "cycle_10total"  -> Pair((totalCycleKm / a.requirement).coerceAtMost(1f),
                                     earnedDateCumulative(cycleRoutes, a.requirement))
            "cycle_20single" -> Pair((bestCycleSingle / a.requirement).coerceAtMost(1f),
                                     earnedDateSingleEvent(cycleRoutes, a.requirement))
            "cycle_20day"    -> Pair((bestCycleDay / a.requirement).coerceAtMost(1f),
                                     earnedDateDay(cycleRoutes, a.requirement))
            "cycle_50single" -> Pair((bestCycleSingle / a.requirement).coerceAtMost(1f),
                                     earnedDateSingleEvent(cycleRoutes, a.requirement))
            "cycle_50day"    -> Pair((bestCycleDay / a.requirement).coerceAtMost(1f),
                                     earnedDateDay(cycleRoutes, a.requirement))
            "cycle_100total" -> Pair((totalCycleKm / a.requirement).coerceAtMost(1f),
                                     earnedDateCumulative(cycleRoutes, a.requirement))
            // Hill Climber & Speed Racer: no elevation data available, speed racer uses avg speed
            "cycle_hill"     -> Pair(0f, null)   // elevation not tracked — always locked
            "cycle_speed"    -> Pair(if (speedRacerAchieved) 1f else 0f,
                                     if (speedRacerAchieved) cycleRoutes.filter { r ->
                                         val mv = r.points.filter { !it.lat.isNaN() && it.speed > 0f }
                                         mv.isNotEmpty() && mv.map { it.speed }.average() >= 1000.0 / 120.0
                                     }.minByOrNull { it.timestamp }?.let { earnedDateForRoute(it) } else null)

            // ── Streaks ───────────────────────────────────────────────────────
            "streak_2"       -> Pair((globalStreak / a.requirement).coerceAtMost(1f),
                                     if (globalStreak >= a.requirement.toInt()) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)
            "streak_7"       -> Pair((globalStreak / a.requirement).coerceAtMost(1f),
                                     if (globalStreak >= a.requirement.toInt()) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)
            "streak_14"      -> Pair((globalStreak / a.requirement).coerceAtMost(1f),
                                     if (globalStreak >= a.requirement.toInt()) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)
            "streak_30"      -> Pair((globalStreak / a.requirement).coerceAtMost(1f),
                                     if (globalStreak >= a.requirement.toInt()) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)
            "streak_comeback"-> Pair(if (hasComeback) 1f else 0f,
                                     if (hasComeback) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)

            // ── Special ───────────────────────────────────────────────────────
            // Social Butterfly: can't be auto-detected (requires share action) — always locked
            "special_social"   -> Pair(0f, null)
            "special_explore"  -> Pair((explorerCount / a.requirement).coerceAtMost(1f),
                                       if (explorerCount >= a.requirement.toInt()) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)
            "special_allround" -> Pair(if (hasAllRounder) 1f else 0f,
                                       if (hasAllRounder) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)
            // Rain or Shine: can't be detected without weather data — always locked
            "special_rain"     -> Pair(0f, null)
            "special_sunrise"  -> Pair(if (hasSunriseToSunset) 1f else 0f,
                                       if (hasSunriseToSunset) earnedDateForRoute(routes.maxBy { it.timestamp }) else null)

            else -> Pair(0f, null)
        }
        AchievementProgress(a, prog, earnedDate)
    }
}
