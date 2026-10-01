package com.example.joggingapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class SavedRoute(
    val id: String,
    val points: List<Point>,
    val avgSpeed: Float,
    val minSpeed: Float,
    val maxSpeed: Float,
    val calories: Float,
    val timestamp: Long,
    val activityType: Int = 0,  // 0=Walk, 1=Run, 2=Cycle (primary/last used)
    val steps: Int = 0,
    val activityTypes: List<Int>? = null,  // null for routes saved before blend feature
    val title: String? = null  // user-editable run title; null = show default label
)

/**
 * Versioned backup envelope (backup format v2). Wraps the saved routes plus the
 * Buddy Link App ID so a restored install keeps its original identity and links
 * (Req 2.3, D4). Backward-compatible: [BackupData.parse] also accepts a legacy v1
 * backup, which was a bare JSON array of [SavedRoute] with no identity.
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val buddyAppId: String? = null,
    val routes: List<SavedRoute> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 2

        /** Serialize routes + optional buddy App ID into the v2 envelope JSON. */
        fun toJson(routes: List<SavedRoute>, buddyAppId: String?): String =
            Gson().toJson(BackupData(CURRENT_VERSION, buddyAppId, routes))

        /**
         * Parse a backup file. Accepts both the v2 envelope (a JSON object with a
         * `routes` array) and a legacy v1 bare array of routes. Returns null only if
         * the input is neither shape / unparseable.
         */
        fun parse(json: String): BackupData? {
            val gson = Gson()
            val trimmed = json.trimStart()
            return try {
                if (trimmed.startsWith("[")) {
                    // Legacy v1: bare array of routes, no identity.
                    val type = object : TypeToken<List<SavedRoute>>() {}.type
                    val routes: List<SavedRoute> = gson.fromJson(json, type) ?: emptyList()
                    BackupData(version = 1, buddyAppId = null, routes = routes)
                } else {
                    // v2 envelope. Gson can leave `routes` null via reflection if the
                    // field is absent, so guard against it through a nullable view.
                    val data = gson.fromJson(json, BackupData::class.java) ?: return null
                    @Suppress("USELESS_ELVIS")
                    val safeRoutes: List<SavedRoute> = (data.routes as List<SavedRoute>?) ?: emptyList()
                    data.copy(routes = safeRoutes)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

object RouteStorage {
    private const val KEY = "saved_routes"

    private fun prefs(context: Context) = context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)

    fun saveRoute(context: Context, route: SavedRoute) {
        val gson = Gson()
        val existing = loadRoutes(context).toMutableList()
        // Guard: never store two routes with the same ID (handles restore + recovery races)
        if (existing.any { it.id == route.id }) return
        existing.add(0, route)
        prefs(context).edit().putString(KEY, gson.toJson(existing)).apply()
    }

    fun loadRoutes(context: Context): List<SavedRoute> {
        val json = prefs(context).getString(KEY, null) ?: return emptyList()
        val gson = Gson()
        val type = object : TypeToken<List<SavedRoute>>() {}.type
        return try { gson.fromJson(json, type) } catch (e: Exception) { emptyList() }
    }

    fun clearRoutes(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    fun deleteRoute(context: Context, id: String) {
        val gson = Gson()
        val existing = loadRoutes(context).toMutableList()
        val filtered = existing.filterNot { it.id == id }
        prefs(context).edit().putString(KEY, gson.toJson(filtered)).apply()
    }

    /** Renames a saved route. Title is trimmed and capped at [MAX_TITLE_LEN] chars. */
    fun updateRouteTitle(context: Context, id: String, title: String) {
        val gson = Gson()
        val trimmed = title.trim().take(MAX_TITLE_LEN)
        val updated = loadRoutes(context).map {
            if (it.id == id) it.copy(title = trimmed.ifBlank { null }) else it
        }
        prefs(context).edit().putString(KEY, gson.toJson(updated)).apply()
    }

    const val MAX_TITLE_LEN = 100
}
