package com.example.joggingapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.joggingapp.ui.theme.JogginTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ROUTE_COLORS = listOf(
    "Light Green" to Color(0xFF66BB6A),
    "Blue"        to Color(0xFF2196F3),
    "Orange"      to Color(0xFFFF9800),
    "Red"         to Color(0xFFF44336),
    "Purple"      to Color(0xFF9C27B0),
    "White"       to Color(0xFFFFFFFF),
)

enum class TrackState { IDLE, TRACKING, PAUSED }

// Shared date-format patterns (used across history card, summary, and share text)
const val DATE_FMT_FULL = "dd MMM yyyy • HH:mm"
const val DATE_FMT_DAY  = "yyyyMMdd"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Capture an invite deep link if the app was launched by one.
        handleBuddyInviteIntent(intent)
        CrashLogger.install(this)
        AppLogger.log(this, LogCategory.UI, "App launched — " +
            "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
            "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        setContent { JogApp(this) }

    }

    // Invites can arrive while the app is already running (singleTop relaunch).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBuddyInviteIntent(intent)
    }

    // Track foreground state so FCM push notifications don't double up with the in-app
    // Buddy Link notifications while the app is visible (see AppForeground).
    override fun onStart() {
        super.onStart()
        AppForeground.onActivityStarted()
    }

    override fun onStop() {
        AppForeground.onActivityStopped()
        super.onStop()
    }

    /** Extracts a Buddy Link invite URI from an intent and publishes it for the UI. */
    private fun handleBuddyInviteIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val scheme = data.scheme?.lowercase()
        val looksLikeInvite = scheme == BuddyConstants.INVITE_SCHEME ||
            ((scheme == "https" || scheme == "http") && data.getQueryParameter("id") != null)
        if (looksLikeInvite) {
            AppLogger.log(this, LogCategory.UI, "Buddy Link invite intent received")
            BuddyInviteBus.pending = data.toString()
        }
    }
}

/**
 * A tiny process-wide holder for an incoming invite deep-link URI. MainActivity writes
 * to it from onCreate/onNewIntent; JogApp polls it once composition is ready. Kept
 * outside the Activity so a recreate doesn't lose a link that arrived mid-flight.
 */
object BuddyInviteBus {
    @Volatile var pending: String? = null
}

fun haversineKm(a: Point, b: Point): Double {
    val R = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
    val sLat = Math.sin(dLat / 2); val sLon = Math.sin(dLon / 2)
    val h = sLat * sLat + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * sLon * sLon
    return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

fun calcDistanceKm(path: List<Point>): Double {
    if (path.size < 2) return 0.0
    var d = 0.0
    for (i in 1 until path.size) {
        // Skip NaN segment-break sentinels
        if (path[i - 1].lat.isNaN() || path[i].lat.isNaN()) continue
        d += haversineKm(path[i - 1], path[i])
    }
    return d
}

fun fmtTime(s: Int) = "%02d:%02d".format(s / 60, s % 60)

/**
 * Compute elapsed duration in seconds for a saved route.
 * Filters out NaN sentinel points (pause markers) so a paused run
 * uses (lastRealPoint.timeMs - firstRealPoint.timeMs) rather than the
 * raw first/last which can span the pause gap.
 */
fun calcDurationSeconds(points: List<Point>): Int {
    val real = points.filter { !it.lat.isNaN() }
    if (real.size < 2) return 0
    return ((real.last().timeMs - real.first().timeMs) / 1000L).toInt()
}

fun fmtDuration(s: Int): String {
    val hrs = s / 3600
    val mins = (s % 3600) / 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins} min"
        else -> "${s}s"
    }
}

fun emojiForMode(mode: Int): String = when (mode) { 1 -> "🏃"; 2 -> "🚴"; else -> "🚶" }

// English default (used by non-localized callers like the outgoing share text)
fun labelForMode(mode: Int): String = when (mode) { 1 -> "Run"; 2 -> "Cycle"; else -> "Walk" }

// Localized variant — pass the active AppStrings
fun labelForMode(mode: Int, s: AppStrings): String = when (mode) { 1 -> s.run; 2 -> s.cycle; else -> s.walk }

// Build a blended emoji string from the set of activity modes used (deduped, preserving order)
fun blendActivityEmoji(modes: List<Int>, current: Int): String {
    val distinct = modes.distinct().ifEmpty { listOf(current) }
    return distinct.joinToString("") { emojiForMode(it) }
}

fun blendActivityLabel(modes: List<Int>): String {
    val distinct = modes.distinct()
    return when {
        distinct.isEmpty() -> "Walk"
        distinct.size == 1 -> labelForMode(distinct.first())
        else -> distinct.joinToString(" + ") { labelForMode(it) }
    }
}

fun blendActivityLabel(modes: List<Int>, s: AppStrings): String {
    val distinct = modes.distinct()
    return when {
        distinct.isEmpty() -> s.walk
        distinct.size == 1 -> labelForMode(distinct.first(), s)
        else -> distinct.joinToString(" + ") { labelForMode(it, s) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JogApp(context: Context) {
    var trackState       by remember { mutableStateOf(TrackState.IDLE) }
    val path                  = remember { mutableStateListOf<Point>() }
    var avgSpeedMs       by remember { mutableStateOf(0f) }
    var minSpeedMs       by remember { mutableStateOf(Float.MAX_VALUE) }
    var maxSpeedMs       by remember { mutableStateOf(0f) }
    var elapsedSeconds   by remember { mutableStateOf(0) }
    var pendingStart     by remember { mutableStateOf(false) }
    var activityMode     by remember { mutableStateOf(0) }
    val usedActivityModes = remember { mutableStateListOf<Int>() }
    var stepCount        by remember { mutableStateOf(0) }
    var stepBaseline     by remember { mutableStateOf(-1f) }
    var showSummary      by remember { mutableStateOf(false) }
    var summaryRoute     by remember { mutableStateOf<SavedRoute?>(null) }
    var summarySteps     by remember { mutableStateOf(0) }
    var showAchievements by remember { mutableStateOf(false) }
    var showExerciseTargets by remember { mutableStateOf(false) }
    var savedRoutes      by remember { mutableStateOf(RouteStorage.loadRoutes(context)) }
    var optionsPaneOpen  by remember { mutableStateOf(false) }
    var colorIdx         by remember { mutableStateOf(0) }
    val prefs = remember { context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var showNameEntry by remember { mutableStateOf(userName.isEmpty()) }
    val routeColor   = ROUTE_COLORS[colorIdx].second
    val distKm       by remember { derivedStateOf { calcDistanceKm(path) } }
    val avgKmh       = avgSpeedMs * 3.6f
    // Language is declared here (early) so activity labels can be localized. The actual
    // CompositionLocalProvider is set up further down when the UI tree is built.
    var appLanguage by remember { mutableStateOf(loadLanguage(context)) }
    val S = stringsFor(appLanguage)
    val activityLabel = labelForMode(activityMode, S)
    // Blended emoji: if more than one activity type was used during the run, show them combined
    val activeModes = if (trackState != TrackState.IDLE && usedActivityModes.isNotEmpty()) usedActivityModes.toList() else listOf(activityMode)
    val activityEmoji = blendActivityEmoji(activeModes, activityMode)
    val activityBlendLabel = blendActivityLabel(activeModes, S)
    val sensorMgr  = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor = remember { sensorMgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    val stepListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values[0]
                if (stepBaseline < 0f) stepBaseline = v
                stepCount = (v - stepBaseline).toInt().coerceAtLeast(0)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
    }
    DisposableEffect(trackState) {
        if (trackState == TrackState.TRACKING && stepSensor != null)
            sensorMgr.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        else sensorMgr.unregisterListener(stepListener)
        onDispose { sensorMgr.unregisterListener(stepListener) }
    }
    fun hasFine() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasNotif() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    fun hasBg() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED else true
    fun hasActivity() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED else true

    // Clears every live-stat field back to zero. Used both when starting a fresh run
    // and when dismissing the summary, so the map screen never shows the last run's
    // stats/route while idle. Keep this the single source of truth for "reset".
    fun resetStats() {
        path.clear(); avgSpeedMs = 0f; minSpeedMs = Float.MAX_VALUE; maxSpeedMs = 0f
        stepCount = 0; stepBaseline = -1f; elapsedSeconds = 0
        usedActivityModes.clear()
    }
    fun doStart() {
        resetStats()
        usedActivityModes.add(activityMode)
        trackState = TrackState.TRACKING
        AppLogger.log(context, LogCategory.TRACKING,
            "Tracking started — mode=${labelForMode(activityMode)} " +
            "fineLocation=${hasFine()} bgLocation=${hasBg()} stepSensor=${stepSensor != null}")
        ContextCompat.startForegroundService(context, Intent(context, ForegroundLocationService::class.java))
    }
    fun pauseTracking() {
        AppLogger.log(context, LogCategory.TRACKING,
            "Paused — elapsed=${fmtTime(elapsedSeconds)} dist=${String.format("%.2f", distKm)}km points=${path.size}")
        trackState = TrackState.PAUSED
        if (path.isNotEmpty()) path.add(Point(Double.NaN, Double.NaN, System.currentTimeMillis(), 0f))
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, ForegroundLocationService::class.java).apply { action = ForegroundLocationService.ACTION_PAUSE })
        } catch (_: Exception) {}
    }
    fun resumeTracking() {
        AppLogger.log(context, LogCategory.TRACKING,
            "Resumed — elapsed so far=${fmtTime(elapsedSeconds)} dist=${String.format("%.2f", distKm)}km")
        trackState = TrackState.TRACKING
        try {
            ContextCompat.startForegroundService(context,
                Intent(context, ForegroundLocationService::class.java).apply { action = ForegroundLocationService.ACTION_RESUME })
        } catch (_: Exception) {}
    }
    fun endRun() {
        AppLogger.log(context, LogCategory.TRACKING,
            "Run ending — state=$trackState elapsed=${fmtTime(elapsedSeconds)} pathSize=${path.size} avgKmh=${String.format("%.1f", avgSpeedMs * 3.6f)}")
        trackState = TrackState.IDLE
        try { context.stopService(Intent(context, ForegroundLocationService::class.java)) } catch (_: Exception) {}

        // Claim and clear the recovery prefs atomically as the very first thing we do.
        // This prevents the LaunchedEffect(Unit) crash-recovery block from racing with this
        // path and saving the same route a second time if the activity is recreated while
        // endRun() is in progress.
        val servicePrefs = context.getSharedPreferences("jog_service", Context.MODE_PRIVATE)
        val pendingExists = servicePrefs.getBoolean("pending_route_exists", false)
        // Read the JSON before clearing, so we still have it for recovery below
        val pendingJson = if (pendingExists) servicePrefs.getString("pending_route_points", null) else null
        if (pendingExists) {
            servicePrefs.edit().remove("pending_route_points").remove("pending_route_time").remove("pending_route_exists").apply()
        }

        // If path is empty (activity was in background and didn't receive live GPS broadcasts),
        // try to recover points the service had buffered before it shut down.
        if (path.size < 2 && pendingJson != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<Point>>() {}.type
                val recovered: List<Point> = com.google.gson.Gson().fromJson(pendingJson, type)
                path.clear()
                path.addAll(recovered)
                val moving = recovered.filter { it.speed > 0f }
                avgSpeedMs = if (moving.isNotEmpty()) moving.map { it.speed }.average().toFloat() else 0f
                minSpeedMs = moving.minOfOrNull { it.speed } ?: 0f
                maxSpeedMs = recovered.maxOfOrNull { it.speed } ?: 0f
                AppLogger.log(context, LogCategory.SAVE,
                    "Path recovered from service buffer — ${recovered.size} points dist=${String.format("%.2f", calcDistanceKm(recovered))}km")
            } catch (e: Exception) {
                AppLogger.log(context, LogCategory.ERROR, "Failed to recover path from service buffer: ${e.message}")
            }
        }

        if (path.size >= 2) {
            val realPoints = path.filter { !it.lat.isNaN() }
            val dh = if (realPoints.size >= 2) (realPoints.last().timeMs - realPoints.first().timeMs).toFloat() / 3_600_000f else elapsedSeconds / 3_600f
            val met = when (activityMode) { 1 -> if (avgSpeedMs * 3.6f < 9f) 8f else 10f; 2 -> 6f; else -> 3.5f }
            val blend = if (usedActivityModes.isNotEmpty()) usedActivityModes.distinct() else listOf(activityMode)
            val now = System.currentTimeMillis()
            val route = SavedRoute(id = now.toString(), points = realPoints,
                avgSpeed = avgSpeedMs, minSpeed = if (minSpeedMs == Float.MAX_VALUE) 0f else minSpeedMs,
                maxSpeed = maxSpeedMs, calories = met * 70f * dh, timestamp = now,
                activityType = activityMode, steps = stepCount, activityTypes = blend)
            RouteStorage.saveRoute(context, route)
            savedRoutes = RouteStorage.loadRoutes(context)
            summaryRoute = route; summarySteps = stepCount; showSummary = true
            AppLogger.log(context, LogCategory.SAVE,
                "Route saved — id=${route.id} mode=${blendActivityLabel(blend)} " +
                "dist=${String.format("%.2f", calcDistanceKm(realPoints))}km " +
                "elapsed=${fmtTime(elapsedSeconds)} steps=$stepCount " +
                "kcal=${String.format("%.0f", route.calories)}")
        } else if (elapsedSeconds >= 10) {
            val dh = elapsedSeconds / 3_600f
            val met = when (activityMode) { 1 -> if (avgSpeedMs * 3.6f < 9f) 8f else 10f; 2 -> 6f; else -> 3.5f }
            val blend = if (usedActivityModes.isNotEmpty()) usedActivityModes.distinct() else listOf(activityMode)
            val now = System.currentTimeMillis()
            val syntheticPoints = listOf(
                Point(0.0, 0.0, now - (elapsedSeconds * 1000L), 0f),
                Point(0.0, 0.0, now, 0f)
            )
            val route = SavedRoute(id = now.toString(), points = syntheticPoints,
                avgSpeed = 0f, minSpeed = 0f, maxSpeed = 0f,
                calories = met * 70f * dh, timestamp = now,
                activityType = activityMode, steps = stepCount, activityTypes = blend)
            RouteStorage.saveRoute(context, route)
            savedRoutes = RouteStorage.loadRoutes(context)
            summaryRoute = route; summarySteps = stepCount; showSummary = true
            AppLogger.log(context, LogCategory.SAVE,
                "GPS-less route saved (timer fallback) — elapsed=${fmtTime(elapsedSeconds)} steps=$stepCount kcal=${String.format("%.0f", route.calories)}")
        } else {
            AppLogger.log(context, LogCategory.TRACKING,
                "Run discarded — too short (elapsed=${fmtTime(elapsedSeconds)} pathSize=${path.size})")
        }
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        if (pendingStart) { pendingStart = false; doStart() }
    }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val ok = results[Manifest.permission.ACCESS_FINE_LOCATION] == true || results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) { pendingStart = false; return@rememberLauncherForActivityResult }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBg()) { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION); return@rememberLauncherForActivityResult }
        if (pendingStart) { pendingStart = false; doStart() }
    }
    fun requestAndStart() {
        val req = mutableListOf<String>()
        if (!hasFine()) { req.add(Manifest.permission.ACCESS_FINE_LOCATION); req.add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (!hasNotif() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) req.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!hasActivity() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) req.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (req.isNotEmpty()) { pendingStart = true; fgLauncher.launch(req.toTypedArray()); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBg()) { pendingStart = true; bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION); return }
        doStart()
    }

    LaunchedEffect(Unit) {
        val req = mutableListOf<String>()
        if (!hasFine()) { req.add(Manifest.permission.ACCESS_FINE_LOCATION); req.add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (!hasNotif() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) req.add(Manifest.permission.POST_NOTIFICATIONS)
        if (!hasActivity() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) req.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (req.isNotEmpty()) fgLauncher.launch(req.toTypedArray())
    }

    // ── Buddy Link controller (Phase 3-4) ────────────────────────────────────
    // Single instance for the whole app; observes the (currently NoOp) repository.
    val buddyController = remember { BuddyLinkController(context.applicationContext) }
    DisposableEffect(Unit) {
        buddyController.start()
        onDispose { buddyController.dispose() }
    }
    // A transient, localized message shown after handling an invite deep link.
    var buddyInviteMessage by remember { mutableStateOf<String?>(null) }
    // Sender appId of an opened invite awaiting the recipient's Accept/Ignore choice.
    // Held across the enable flow so an invite opened before enabling is NOT dropped.
    var pendingInviteFrom by remember { mutableStateOf<String?>(null) }

    // Report the device's GPS/location-enabled state so CONDITION-1
    // (sharing = enabled && gpsOn) stays accurate. Polls while composed.
    LaunchedEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        while (true) {
            val on = try {
                lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                    lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
            } catch (_: Exception) { false }
            buddyController.updateGpsState(on && hasFine())
            delay(5000L)
        }
    }

    // Consume an incoming invite deep link (set by MainActivity). Parses + validates,
    // then either fires a link request or surfaces a localized error (Req 3.5b).
    LaunchedEffect(Unit) {
        while (true) {
            val raw = BuddyInviteBus.pending
            if (raw != null) {
                BuddyInviteBus.pending = null
                when (val res = BuddyInvite.parse(raw)) {
                    is BuddyInvite.ParseResult.Valid -> {
                        // HOLD the sender id regardless of enable state so the invite is
                        // never silently dropped. The Accept/Ignore dialog is shown once
                        // Buddy Link is enabled; if it isn't yet, prompt the user to enable.
                        pendingInviteFrom = res.appId
                        if (!buddyController.enabled) {
                            buddyInviteMessage = S.buddyInviteNeedsEnable
                        }
                    }
                    is BuddyInvite.ParseResult.Expired -> buddyInviteMessage = S.buddyLinkExpired
                    is BuddyInvite.ParseResult.Malformed -> buddyInviteMessage = S.buddyLinkInvalid
                }
            }
            delay(1000L)
        }
    }

    val locReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ForegroundLocationService.ACTION_LOCATION_BROADCAST) return
                val lat = intent.getDoubleExtra(ForegroundLocationService.EXTRA_LAT, 0.0)
                val lon = intent.getDoubleExtra(ForegroundLocationService.EXTRA_LON, 0.0)
                val spd = intent.getFloatExtra(ForegroundLocationService.EXTRA_SPEED, 0f)
                val t   = intent.getLongExtra(ForegroundLocationService.EXTRA_TIME, System.currentTimeMillis())
                path.add(Point(lat, lon, t, spd))
                if (spd > 0f) minSpeedMs = minOf(minSpeedMs, spd)
                maxSpeedMs = maxOf(maxSpeedMs, spd)
                val moving = path.filter { !it.lat.isNaN() && it.speed > 0f }
                avgSpeedMs = if (moving.isNotEmpty()) moving.map { it.speed }.average().toFloat() else 0f
            }
        }
    }
    DisposableEffect(trackState) {
        if (trackState == TrackState.TRACKING)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(locReceiver, IntentFilter(ForegroundLocationService.ACTION_LOCATION_BROADCAST), Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(locReceiver, IntentFilter(ForegroundLocationService.ACTION_LOCATION_BROADCAST))
                }
            } catch (_: Exception) {}
        onDispose { try { context.unregisterReceiver(locReceiver) } catch (_: Exception) {} }
    }

    // Listen for pause/end actions triggered from the notification on lock screen
    val notifActionReceiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.example.joggingapp.ACTION_PAUSE_FROM_NOTIF" -> {
                        AppLogger.log(context, LogCategory.NOTIF, "PAUSE_FROM_NOTIF received — inserting segment break")
                        if (path.isNotEmpty()) path.add(Point(Double.NaN, Double.NaN, System.currentTimeMillis(), 0f))
                        trackState = TrackState.PAUSED
                    }
                    "com.example.joggingapp.ACTION_RESUME_FROM_NOTIF" -> {
                        AppLogger.log(context, LogCategory.NOTIF, "RESUME_FROM_NOTIF received")
                        trackState = TrackState.TRACKING
                    }
                    "com.example.joggingapp.ACTION_END_FROM_NOTIF" -> {
                        AppLogger.log(context, LogCategory.NOTIF, "END_FROM_NOTIF received")
                        endRun()
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction("com.example.joggingapp.ACTION_PAUSE_FROM_NOTIF")
            addAction("com.example.joggingapp.ACTION_RESUME_FROM_NOTIF")
            addAction("com.example.joggingapp.ACTION_END_FROM_NOTIF")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(notifActionReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(notifActionReceiver, filter)
            }
        } catch (_: Exception) {}
        onDispose { try { context.unregisterReceiver(notifActionReceiver) } catch (_: Exception) {} }
    }

    LaunchedEffect(trackState) {
        if (trackState == TrackState.TRACKING) {
            while (trackState == TrackState.TRACKING) {
                delay(1000L)
                if (trackState == TrackState.TRACKING) elapsedSeconds++
            }
        }
    }

    // Recover pending route if the user ended via notification but the broadcast never
    // reached the activity (e.g. app was killed). This only fires on composition (app start),
    // so it must not race with endRun() — endRun() clears the flag synchronously first,
    // meaning if both code paths are live at the same time, endRun() wins.
    LaunchedEffect(Unit) {
        val servicePrefs = context.getSharedPreferences("jog_service", Context.MODE_PRIVATE)
        if (servicePrefs.getBoolean("pending_route_exists", false) && trackState == TrackState.IDLE) {
            AppLogger.log(context, LogCategory.SAVE, "Crash recovery: pending route found on app start")
            val json = servicePrefs.getString("pending_route_points", null)
            servicePrefs.edit().remove("pending_route_points").remove("pending_route_time").remove("pending_route_exists").apply()
            try {
                if (json != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<Point>>() {}.type
                    val recovered: List<Point> = com.google.gson.Gson().fromJson(json, type)
                    if (recovered.size >= 2) {
                        val moving = recovered.filter { it.speed > 0f }
                        val avg = if (moving.isNotEmpty()) moving.map { it.speed }.average().toFloat() else 0f
                        val min = moving.minOfOrNull { it.speed } ?: 0f
                        val max = recovered.maxOfOrNull { it.speed } ?: 0f
                        val dh = (recovered.last().timeMs - recovered.first().timeMs).toFloat() / 3_600_000f
                        val met = when (activityMode) { 1 -> if (avg * 3.6f < 9f) 8f else 10f; 2 -> 6f; else -> 3.5f }
                        val route = SavedRoute(id = recovered.first().timeMs.toString(), points = recovered,
                            avgSpeed = avg, minSpeed = min, maxSpeed = max, calories = met * 70f * dh,
                            timestamp = recovered.first().timeMs, activityType = activityMode, steps = 0)
                        RouteStorage.saveRoute(context, route)
                        savedRoutes = RouteStorage.loadRoutes(context)
                        summaryRoute = route; summarySteps = 0; showSummary = true
                        AppLogger.log(context, LogCategory.SAVE,
                            "Crash recovery complete — ${recovered.size} points saved dist=${String.format("%.2f", calcDistanceKm(recovered))}km")
                    } else {
                        AppLogger.log(context, LogCategory.SAVE, "Crash recovery skipped — only ${recovered.size} point(s)")
                    }
                }
            } catch (e: Exception) {
                AppLogger.log(context, LogCategory.ERROR, "Crash recovery failed: ${e.message}")
            }
        }
    }

    var appTheme by remember { mutableStateOf(com.example.joggingapp.ui.theme.loadTheme(context)) }
    val paneWidth  = 280.dp
    val paneOffset by animateDpAsState(if (optionsPaneOpen) 0.dp else paneWidth, tween(300), label = "pane")

    JogginTheme(appTheme = appTheme) {
      CompositionLocalProvider(LocalStrings provides S, LocalLanguage provides appLanguage) {
        val c = JogginTheme.colors
        Box(modifier = Modifier.fillMaxSize().background(c.background)
        ) {
            if (showNameEntry) {
                // First-launch name entry overlay
                Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
                    Card(modifier = Modifier.padding(32.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), backgroundColor = c.surface) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(S.welcomeTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
                            Spacer(Modifier.height(8.dp))
                            Text(S.whatsYourName, fontSize = 14.sp, color = c.textSecondary)
                            Spacer(Modifier.height(16.dp))
                            var nameInput by remember { mutableStateOf("") }
                            androidx.compose.material.TextField(
                                value = nameInput, onValueChange = { nameInput = it },
                                placeholder = { Text(S.enterYourName) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(20.dp))
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(c.primary).clickable {
                                    if (nameInput.isNotBlank()) {
                                        userName = nameInput.trim()
                                        prefs.edit().putString("user_name", userName).apply()
                                        showNameEntry = false
                                    }
                                }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                                Text(S.letsGo, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
                            }
                        }
                    }
                }
            } else if (showAchievements) {
                // BackHandler allows system back gesture/button to dismiss
                androidx.activity.compose.BackHandler { showAchievements = false }
                AchievementsScreen(routes = savedRoutes, onBack = { showAchievements = false })
            } else if (showExerciseTargets) {
                ExerciseTargetsScreen(routes = savedRoutes, onBack = { showExerciseTargets = false })
            } else if (showSummary && summaryRoute != null) {
                SummaryScreen(route = summaryRoute!!, steps = summarySteps, activityLabel = activityLabel, onDone = {
                    showSummary = false
                    // Reset live stats so the map screen returns to a clean, zeroed state
                    // instead of showing the run we just finished.
                    resetStats()
                    summaryRoute = null; summarySteps = 0
                })
            } else {
                val pagerState = rememberPagerState(initialPage = 0)
                VerticalPager(state = pagerState, pageCount = 2, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    JogMap(
                                        path = path, routeColor = routeColor, hasGpsSignal = path.isNotEmpty(),
                                        buddyView = buddyController.primaryBuddyView,
                                        buddyTrail = buddyController.visibleBuddyTrail,
                                        buddyUpdatedLabel = { atMs ->
                                            val mins = ((System.currentTimeMillis() - atMs) / 60000L).toInt()
                                            if (mins <= 0) S.buddyJustNow else S.buddyMinutesAgo(mins)
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // ── Broadcast-on badge (top-center) while this device shares (Req 6.2) ──
                                    if (buddyController.sharing) {
                                        Row(modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                                            .clip(RoundedCornerShape(50)).background(Color(0xCC1565C0)).padding(horizontal = 12.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("📡", fontSize = 12.sp)
                                            Text(S.buddyBroadcastOn, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    // ── Eye "eyes-on-you" indicator (bottom-center); tap → options (Req 11) ──
                                    if (buddyController.hasWatchers) {
                                        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                                            .clip(RoundedCornerShape(50)).background(Color(0xCCEF6C00))
                                            .clickable { optionsPaneOpen = true }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .semantics { contentDescription = S.buddyEyesOnYou },
                                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("👁", fontSize = 14.sp)
                                            Text(S.buddyEyesOnYou, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    if (savedRoutes.isNotEmpty()) {
                                        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("↑", fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f))
                                            Text(S.pastRunsCount(savedRoutes.size), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                                Card(modifier = Modifier.fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures { _, dragAmount ->
                                            if (dragAmount < -20) optionsPaneOpen = true   // swipe left → options
                                            if (dragAmount > 20) showAchievements = true    // swipe right → achievements
                                        }
                                    }, elevation = 8.dp,
                                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), backgroundColor = c.surfaceVariant) {
                                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(activityLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(S.hintAchievements, fontSize = 10.sp, color = c.textSecondary)
                                                Text("|", fontSize = 10.sp, color = c.divider)
                                                Text(S.hintOptions, fontSize = 10.sp, color = c.textSecondary)
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            StatItem(fmtTime(elapsedSeconds), S.statTime); VDivider()
                                            StatItem(String.format("%.2f", distKm), S.statDistanceKm); VDivider()
                                            StatItem(if (avgKmh > 0f) String.format("%.1f", avgKmh) else "---", S.statKmh)
                                        }
                                        Spacer(Modifier.height(20.dp))

                                        when (trackState) {
                                            TrackState.PAUSED -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                                    BigButton(S.resume, c.success) { resumeTracking() }
                                                    BigButton(S.endRun, c.error) { endRun() }
                                                }
                                            }
                                            else -> {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                                        if (trackState != TrackState.IDLE) {
                                                            // During activity: cycle is 2 → flip to walk (0); walk/run toggle between 0 and 1
                                                            activityMode = when (activityMode) {
                                                                2 -> 0  // cycling → walking (can't run with a bike)
                                                                1 -> 0  // running → walking
                                                                else -> 1  // walking → running
                                                            }
                                                        } else {
                                                            // Pre-start: cycle through all three
                                                            activityMode = (activityMode + 1) % 3
                                                        }
                                                        if (trackState != TrackState.IDLE && !usedActivityModes.contains(activityMode)) {
                                                            usedActivityModes.add(activityMode)
                                                        }
                                                    }) {
                                                        val isBlendActive = trackState != TrackState.IDLE && usedActivityModes.distinct().size > 1
                                                        val btnColor = when {
                                                            isBlendActive -> c.blend
                                                            activityMode == 2 -> c.accent
                                                            activityMode == 1 -> c.primary
                                                            else -> c.walking
                                                        }
                                                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(btnColor), contentAlignment = Alignment.Center) { Text(activityEmoji, fontSize = if (activityEmoji.length > 2) 16.sp else 22.sp) }
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(if (trackState != TrackState.IDLE) activityBlendLabel else activityLabel, fontSize = 11.sp, color = c.textSecondary, textAlign = TextAlign.Center)
                                                    }
                                                    Box(modifier = Modifier.size(68.dp).shadow(6.dp, CircleShape).clip(CircleShape)
                                                        .background(if (trackState == TrackState.TRACKING) c.primaryDark else c.primary)
                                                        .clickable { if (trackState == TrackState.TRACKING) pauseTracking() else requestAndStart() }, contentAlignment = Alignment.Center) {
                                                        if (trackState == TrackState.TRACKING) {
                                                            Canvas(modifier = Modifier.size(24.dp)) { val bw = size.width * 0.22f; val bh = size.height * 0.7f; val gap = size.width * 0.18f; val top = (size.height - bh) / 2f
                                                                drawRect(Color(0xFFFFFFFF), topLeft = Offset((size.width - gap) / 2f - bw, top), size = androidx.compose.ui.geometry.Size(bw, bh))
                                                                drawRect(Color(0xFFFFFFFF), topLeft = Offset((size.width + gap) / 2f, top), size = androidx.compose.ui.geometry.Size(bw, bh)) }
                                                        } else {
                                                            Canvas(modifier = Modifier.size(24.dp)) { val w = size.width; val h = size.height; val triH = h * 0.6f; val triW = w * 0.52f; val cx = w / 2f; val cy = h / 2f
                                                                drawPath(Path().apply { moveTo(cx + triH * 0.67f, cy); lineTo(cx - triH * 0.33f, cy - triW / 2f); lineTo(cx - triH * 0.33f, cy + triW / 2f); close() }, Color(0xFFFFFFFF)) }
                                                        }
                                                    }
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("👟", fontSize = 22.sp); Spacer(Modifier.height(4.dp))
                                                        Text("$stepCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
                                                        Text(S.steps, fontSize = 11.sp, color = c.textSecondary)
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize().background(c.background)) {
                                var sortNewestFirst by remember { mutableStateOf(true) }
                                Row(modifier = Modifier.fillMaxWidth().background(c.primary).padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(S.pastRuns, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.onPrimary)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (sortNewestFirst) S.newest else S.oldest, fontSize = 11.sp, color = c.onPrimary.copy(alpha = 0.8f))
                                        Text(if (sortNewestFirst) "↓" else "↑", fontSize = 16.sp, color = c.onPrimary.copy(alpha = 0.8f),
                                            modifier = Modifier.clickable { sortNewestFirst = !sortNewestFirst })
                                    }
                                }
                                if (savedRoutes.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.noRunsYet, fontSize = 15.sp, color = c.textSecondary, textAlign = TextAlign.Center) }
                                } else {
                                    val sortedRoutes = if (sortNewestFirst) savedRoutes.sortedByDescending { it.timestamp } else savedRoutes.sortedBy { it.timestamp }
                                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        sortedRoutes.forEach { route -> HistoryRunCard(route = route, onDelete = { RouteStorage.deleteRoute(context, route.id); savedRoutes = RouteStorage.loadRoutes(context) }, onTitleChanged = { newTitle -> RouteStorage.updateRouteTitle(context, route.id, newTitle); savedRoutes = RouteStorage.loadRoutes(context) }) }
                                        Spacer(Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!showSummary && !showAchievements && !showExerciseTargets) {
                if (optionsPaneOpen) { Box(modifier = Modifier.fillMaxSize().background(Color(0x66000000)).clickable { optionsPaneOpen = false }) }
                Box(modifier = Modifier.width(paneWidth).fillMaxHeight().align(Alignment.CenterEnd).offset(x = paneOffset).shadow(16.dp).background(c.secondary)) {
                    OptionsPaneContent(colorIdx = colorIdx, savedRoutes = savedRoutes, onColorSelected = { colorIdx = it }, onClose = { optionsPaneOpen = false },
                        onClearRuns = { RouteStorage.clearRoutes(context); savedRoutes = emptyList() },
                        onRoutesChanged = { savedRoutes = RouteStorage.loadRoutes(context) },
                        userName = userName,
                        onUserNameChanged = { newName ->
                            userName = newName
                            prefs.edit().putString("user_name", newName).apply()
                        },
                        currentLanguage = appLanguage,
                        onLanguageChanged = { appLanguage = it; saveLanguage(context, it) },
                        currentTheme = appTheme, onThemeChanged = { appTheme = it; com.example.joggingapp.ui.theme.saveTheme(context, it) },
                        buddyController = buddyController,
                        buddyInviteMessage = buddyInviteMessage,
                        onBuddyInviteMessageShown = { buddyInviteMessage = null },
                        onOpenExerciseTargets = { optionsPaneOpen = false; showExerciseTargets = true })
                }
            }

            // ── Welcome bubble — show once per session, auto-dismiss after 3s ──
            // Use a stable remember(Unit) so this is set once at composition and
            // never re-evaluated on subsequent recompositions.
            var showWelcome by remember { mutableStateOf(userName.isNotEmpty()) }

            LaunchedEffect(Unit) {
                if (showWelcome) { delay(3000L); showWelcome = false }
            }
            if (showWelcome && !showNameEntry && !showAchievements && !showSummary && !showExerciseTargets) {
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.primary)
                    .shadow(8.dp, RoundedCornerShape(50))
                    .padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(S.welcomeUser(userName), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
                }
            }

            // ── Buddy invite Accept/Ignore dialog ────────────────────────────
            // Shown to the RECIPIENT once an invite is opened AND Buddy Link is on.
            // Accept drives recipient-side pairing (both become buddies); Ignore
            // discards cleanly. Held across the enable flow so it is never dropped.
            val inviteFrom = pendingInviteFrom
            if (inviteFrom != null && buddyController.enabled) {
                BuddyInviteDialog(
                    senderLabel = buddyShortId(inviteFrom),
                    onAccept = {
                        buddyController.acceptInvite(inviteFrom)
                        buddyInviteMessage = S.buddyInviteAcceptedToast
                        pendingInviteFrom = null
                    },
                    onIgnore = { pendingInviteFrom = null }
                )
            }
        }
      }
    }
}

@Composable
fun HistoryRunCard(route: SavedRoute, onDelete: () -> Unit, onTitleChanged: (String) -> Unit = {}) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current
    // Title editing state — reset when this card's route changes
    var editingTitle by remember(route.id) { mutableStateOf(false) }
    var titleInput by remember(route.id) { mutableStateOf(route.title ?: "") }
    val distKm = calcDistanceKm(route.points)
    // Use calcDurationSeconds so NaN sentinel pause-break points are excluded
    val dur = calcDurationSeconds(route.points)
    val dateStr = SimpleDateFormat(DATE_FMT_FULL, Locale.getDefault()).format(Date(route.timestamp))
    val isBlended = (route.activityTypes ?: emptyList()).distinct().size > 1
    val actEmoji = when {
        isBlended -> (route.activityTypes ?: emptyList()).distinct().joinToString("") { emojiForMode(it) }
        route.activityType == 2 -> "🚴"
        route.activityType == 1 -> "🏃"
        // Infer for old routes that don't have activityType: avg > 5 km/h = run
        route.activityType == 0 && route.avgSpeed * 3.6f > 5f -> "🏃"
        else -> "🚶"
    }
    val actLabel = when {
        isBlended -> (route.activityTypes ?: emptyList()).distinct().joinToString(" + ") { labelForMode(it, S) }
        route.activityType == 2 -> S.cycle
        route.activityType == 1 -> S.run
        route.activityType == 0 && route.avgSpeed * 3.6f > 5f -> S.run
        else -> S.walk
    }

    val activityColor = when {
        isBlended -> c.blend
        route.activityType == 2 -> c.cycling
        route.activityType == 1 -> c.running
        route.activityType == 0 && route.avgSpeed * 3.6f > 5f -> c.running
        else -> c.walking
    }

    val hasGpsData = route.points.size >= 2 && !(route.points.first().lat == 0.0 && route.points.first().lon == 0.0)

    Card(modifier = Modifier.fillMaxWidth().border(5.dp, activityColor, RoundedCornerShape(16.dp)),
        elevation = 4.dp, shape = RoundedCornerShape(16.dp), backgroundColor = c.surface) {
        Column {
            // ── Editable title bar (above the map) ──────────────────────────────
            if (editingTitle) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material.TextField(
                        value = titleInput,
                        onValueChange = { if (it.length <= RouteStorage.MAX_TITLE_LEN) titleInput = it },
                        singleLine = true,
                        placeholder = { Text(S.titlePlaceholder, fontSize = 14.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                    // Save (✓) and Cancel (✕)
                    Text("✓", fontSize = 20.sp, color = c.success, modifier = Modifier
                        .clickable { onTitleChanged(titleInput.trim()); editingTitle = false }
                        .padding(4.dp))
                    Text("✕", fontSize = 18.sp, color = c.textSecondary, modifier = Modifier
                        .clickable { titleInput = route.title ?: ""; editingTitle = false }
                        .padding(4.dp))
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    // If the user hasn't named the run, leave the bar blank (no placeholder text).
                    Text(
                        text = route.title?.takeIf { it.isNotBlank() } ?: "",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = c.onBackground,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Pencil edit icon, top-right
                    Text("✏️", fontSize = 16.sp, modifier = Modifier
                        .clickable { titleInput = route.title ?: ""; editingTitle = true }
                        .padding(4.dp))
                }
            }
            // Interactive mini map with zoom and pan (only if GPS data available)
            Box(modifier = Modifier.fillMaxWidth().height(180.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
                if (hasGpsData) {
                    RouteMap(points = route.points, interactive = true, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(c.surface),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 32.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(S.noGpsData, fontSize = 13.sp, color = c.textSecondary)
                            Text(S.timerBasedActivity, fontSize = 11.sp, color = c.textSecondary.copy(alpha = 0.7f))
                        }
                    }
                }
                // Semi-opaque daily total overlay (only when map is showing)
                if (hasGpsData) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Column {
                        Text("$actEmoji $actLabel", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${String.format("%.2f", distKm)} km • ${fmtTime(dur)}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(actEmoji, fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(dateStr, fontSize = 12.sp, color = c.textSecondary)
                    }
                    // "Today" badge if the run was today
                    val isToday = SimpleDateFormat(DATE_FMT_DAY, Locale.getDefault()).format(Date(route.timestamp)) ==
                        SimpleDateFormat(DATE_FMT_DAY, Locale.getDefault()).format(Date())
                    if (isToday) {
                        Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(c.primary).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(S.today, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.onPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MiniStat("⏱", fmtDuration(dur), S.duration)
                    MiniStat("📍", if (hasGpsData) String.format("%.2f", distKm) else S.na, S.km)
                    MiniStat("⚡", if (hasGpsData) String.format("%.1f", route.avgSpeed * 3.6f) else S.na, S.statKmh)
                    MiniStat("🔥", String.format("%.0f", route.calories), S.kcal)
                }
                if (route.steps > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(S.stepsCount(route.steps), fontSize = 11.sp, color = c.textSecondary)
                }
                Spacer(Modifier.height(12.dp)); Divider(color = c.divider); Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("📤  ${S.share}", fontSize = 13.sp, color = c.accent,
                        modifier = Modifier.clickable { shareRoute(context, route) }.padding(4.dp))
                    Text("🗑  ${S.remove}", fontSize = 13.sp, color = c.error,
                        modifier = Modifier.clickable { onDelete() }.padding(4.dp))
                }
            }
        }
    }
}

@Composable
fun MiniStat(emoji: String, value: String, label: String) {
    val c = JogginTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 16.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
        Text(label, fontSize = 10.sp, color = c.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun SummaryScreen(route: SavedRoute, steps: Int, activityLabel: String, onDone: () -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current
    val distKm = calcDistanceKm(route.points)
    // Use calcDurationSeconds so NaN sentinel pause-break points are excluded
    val dur = calcDurationSeconds(route.points)
    val dateStr = SimpleDateFormat(DATE_FMT_FULL, Locale.getDefault()).format(Date(route.timestamp))

    // Activity type derived from the saved route (truth), handling blended runs. This is
    // more accurate than the passed-in activityLabel, which reflects the current UI mode.
    val routeModes = (route.activityTypes ?: listOf(route.activityType)).distinct()
    val routeActivityLabel = routeModes.joinToString(" + ") { labelForMode(it, S) }
    val routeActivityEmoji = routeModes.joinToString("") { emojiForMode(it) }
    Box(modifier = Modifier.fillMaxSize().background(c.surface)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(S.complete(activityLabel), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.onBackground)
            Text(dateStr, fontSize = 13.sp, color = c.textSecondary)
            Card(modifier = Modifier.fillMaxWidth().height(220.dp), elevation = 4.dp, shape = RoundedCornerShape(12.dp)) {
                RouteMap(points = route.points, interactive = false, modifier = Modifier.fillMaxSize())
            }
            Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(12.dp), backgroundColor = c.surface) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SumRow("$routeActivityEmoji  ${S.activity}", routeActivityLabel); Divider(color = c.divider)
                    SumRow("⏱  ${S.time}", fmtTime(dur)); Divider(color = c.divider)
                    SumRow("📍  ${S.distance}", String.format("%.2f ${S.km}", distKm)); Divider(color = c.divider)
                    SumRow("⚡  ${S.avgSpeed}", String.format("%.1f ${S.statKmh}", route.avgSpeed * 3.6f)); Divider(color = c.divider)
                    SumRow("🔼  ${S.maxSpeed}", String.format("%.1f ${S.statKmh}", route.maxSpeed * 3.6f)); Divider(color = c.divider)
                    SumRow("🔽  ${S.minSpeed}", String.format("%.1f ${S.statKmh}", route.minSpeed * 3.6f)); Divider(color = c.divider)
                    SumRow("👟  ${S.steps.replaceFirstChar { it.uppercase() }}", "$steps"); Divider(color = c.divider)
                    SumRow("🔥  ${S.calories}", String.format("%.0f ${S.kcal}", route.calories))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Share button
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(c.accent).clickable { shareRoute(context, route) }
                    .padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("📤 ${S.share}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
                }
                // Done button
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(c.primary).clickable { onDone() }
                    .padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text(S.done, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary)
                }
            }
        }
    }
}

@Composable
fun SumRow(label: String, value: String) {
    val c = JogginTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = c.textSecondary)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground)
    }
}

// Shared OpenStreetMap tile source — one definition used by every map in the app.
// Shared tile source — provider chosen in MapTiles.kt (Carto by default, OSM fallback).
private fun jogginTileSource() = MapTiles.tileSource()

// One-time osmdroid configuration (user agent + tile cache location).
private fun configureOsmdroid(context: Context) {
    org.osmdroid.config.Configuration.getInstance().apply {
        userAgentValue = MapTiles.USER_AGENT
        osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid")
    }
}

/**
 * Single map composable used for both the post-run summary (interactive = false)
 * and the history cards (interactive = true). Draws the route polyline plus
 * start/finish markers and frames the whole route.
 */
@Composable
fun RouteMap(points: List<Point>, interactive: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember(points, interactive) {
        configureOsmdroid(context)
        org.osmdroid.views.MapView(context).apply {
            setTileSource(jogginTileSource())
            setMultiTouchControls(interactive)
            isClickable = interactive
            isFocusable = interactive
            if (interactive) {
                // Stop the parent scroll from stealing pan/zoom gestures
                setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
            }
            if (points.size >= 2) {
                val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
                val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }
                val line = org.osmdroid.views.overlay.Polyline(this).apply {
                    outlinePaint.color = android.graphics.Color.parseColor("#66BB6A")
                    outlinePaint.strokeWidth = 10f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    setPoints(points.map { org.osmdroid.util.GeoPoint(it.lat, it.lon) })
                }
                val bearing = bearingDeg(points[0], points[1])
                val startM = org.osmdroid.views.overlay.Marker(this).apply {
                    position = org.osmdroid.util.GeoPoint(points.first().lat, points.first().lon)
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                    icon = makeStartArrowDrawable(context, bearing); title = "Start"
                }
                val endM = org.osmdroid.views.overlay.Marker(this).apply {
                    position = org.osmdroid.util.GeoPoint(points.last().lat, points.last().lon)
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                    icon = makeFinishFlagDrawable(context); title = "Finish"
                }
                overlays.addAll(listOf(line, startM, endM))
                post { zoomToBoundingBox(org.osmdroid.util.BoundingBox(
                    maxLat + 0.001, maxLon + 0.001, minLat - 0.001, minLon - 0.001), false, 48) }
            } else if (points.isNotEmpty()) {
                controller.setZoom(16.0)
                controller.setCenter(org.osmdroid.util.GeoPoint(points[0].lat, points[0].lon))
            }
        }
    }
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }
    androidx.compose.ui.viewinterop.AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * A collapsible options section: a tappable header (title + chevron) that shows/hides
 * its [content]. Collapsed by default. The header can carry an optional [leadingEmoji].
 * Includes the standard section divider above the header so callers don't repeat it.
 */
@Composable
fun CollapsibleSection(
    title: String,
    leadingEmoji: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    val c = JogginTheme.colors
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Spacer(Modifier.height(20.dp)); Divider(color = c.onSecondary.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (leadingEmoji != null) Text(leadingEmoji, fontSize = 16.sp)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondary)
        }
        // Chevron indicates state (▸ collapsed, ▾ expanded)
        Text(if (expanded) "▾" else "▸", fontSize = 14.sp, color = c.onSecondary.copy(alpha = 0.7f))
    }
    if (expanded) {
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * The Profile / Bio content (avatar + rotate + name edit). Extracted so it can be
 * placed at the TOP of the options pane inside a CollapsibleSection. Holds its own
 * photo/name-edit state; only the user name + change callback come from outside.
 */
@Composable
fun ProfileSection(userName: String, onUserNameChanged: (String) -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE) }

    // Profile picture path stored in prefs; loaded as a Bitmap
    var profilePicPath by remember { mutableStateOf(prefs.getString("profile_pic_path", null)) }

    // Image picker launcher — copies selected image into app-private storage
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
            val dest = java.io.File(context.filesDir, "profile_pic.jpg")
            dest.outputStream().use { out -> input.copyTo(out) }
            input.close()
            profilePicPath = dest.absolutePath
            prefs.edit().putString("profile_pic_path", dest.absolutePath).apply()
            AppLogger.log(context, LogCategory.PROFILE, "Profile picture updated")
        } catch (e: Exception) {
            AppLogger.log(context, LogCategory.ERROR, "Failed to save profile picture: ${e.message}")
        }
    }

    var editingName by remember { mutableStateOf(false) }
    var nameInput  by remember { mutableStateOf(userName) }
    // Bumped whenever the profile picture file changes on disk (e.g. after a rotate)
    // so the displayed bitmap is re-decoded even though the file path is unchanged.
    var picVersion by remember { mutableStateOf(0) }

    // Rotates the saved profile picture 90° clockwise, in place, and rewrites the
    // JPEG so the new orientation is used everywhere (avatar + share image).
    fun rotateProfilePicture() {
        val path = profilePicPath ?: return
        try {
            val src = BitmapFactory.decodeFile(path) ?: return
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            val rotated = android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            java.io.File(path).outputStream().use { out ->
                rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            if (rotated != src) src.recycle()
            picVersion++
            AppLogger.log(context, LogCategory.PROFILE, "Profile picture rotated 90°")
        } catch (e: Exception) {
            AppLogger.log(context, LogCategory.ERROR, "Failed to rotate profile picture: ${e.message}")
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Avatar circle ─────────────────────────────────────────────────
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape)
                .background(c.primaryDark.copy(alpha = 0.15f))
                .border(3.dp, c.primaryDark, CircleShape)
                .clickable { pickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            val bmp = remember(profilePicPath, picVersion) {
                profilePicPath?.let { path -> try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null } }
            }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = S.profilePicture,
                    modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                val initials = userName.trim().split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.take(2).joinToString("")
                if (initials.isNotEmpty()) Text(initials, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.primaryDark)
                else Text("📷", fontSize = 28.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(S.tapToChangePhoto, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.5f))
        // ── Rotate button — only shown when a photo exists ─────────────────
        if (profilePicPath != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(c.primary.copy(alpha = 0.15f))
                    .clickable { rotateProfilePicture() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🔄", fontSize = 14.sp)
                Text(S.rotatePhoto, fontSize = 12.sp, color = c.onSecondary, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── Name field ────────────────────────────────────────────────────
        if (editingName) {
            androidx.compose.material.TextField(
                value = nameInput, onValueChange = { nameInput = it },
                singleLine = true, placeholder = { Text(S.yourName) },
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.primary)
                        .clickable {
                            if (nameInput.isNotBlank()) {
                                AppLogger.log(context, LogCategory.PROFILE, "Name changed: '${userName}' → '${nameInput.trim()}'")
                                onUserNameChanged(nameInput.trim()); editingName = false
                            }
                        }.padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text(S.save, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary) }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.divider)
                        .clickable { nameInput = userName; editingName = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text(S.cancel, fontSize = 13.sp, color = c.onBackground) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (userName.isNotBlank()) userName else S.tapToSetName,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (userName.isNotBlank()) c.onSecondary else c.onSecondary.copy(alpha = 0.45f)
                )
                Text("✏️", fontSize = 16.sp, modifier = Modifier.clickable { nameInput = userName; editingName = true })
            }
        }
    }
}

@Composable
fun OptionsPaneContent(colorIdx: Int, savedRoutes: List<SavedRoute>, onColorSelected: (Int) -> Unit, onClose: () -> Unit, onClearRuns: () -> Unit, onRoutesChanged: () -> Unit = {}, userName: String = "", onUserNameChanged: (String) -> Unit = {}, currentTheme: com.example.joggingapp.ui.theme.AppTheme = com.example.joggingapp.ui.theme.AppTheme.SUNRISE, onThemeChanged: (com.example.joggingapp.ui.theme.AppTheme) -> Unit = {}, currentLanguage: Language = Language.ENGLISH, onLanguageChanged: (Language) -> Unit = {}, buddyController: BuddyLinkController? = null, buddyInviteMessage: String? = null, onBuddyInviteMessageShown: () -> Unit = {}, onOpenExerciseTargets: () -> Unit = {}) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE) }
    // Localized route colour names, index-aligned with ROUTE_COLORS
    val colorNames = listOf(S.colorLightGreen, S.colorBlue, S.colorOrange, S.colorRed, S.colorPurple, S.colorWhite)
    var shareWithMap by remember { mutableStateOf(prefs.getBoolean("share_with_map", true)) }
    // Debug console is hidden by default. It's revealed only by triple-tapping the
    // version number at the very bottom of this pane. Reset each time the pane reopens.
    var showDebugConsole by remember { mutableStateOf(false) }
    var versionTapCount  by remember { mutableStateOf(0) }
    var lastVersionTapMs by remember { mutableStateOf(0L) }
    // restoreMessage is keyed on Unit so it survives recompositions but resets when the
    // composable leaves and re-enters composition (i.e. each time the pane is opened).
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var restoreSuccess by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            AppLogger.log(context, LogCategory.BACKUP, "Restore cancelled — no file selected")
            return@rememberLauncherForActivityResult
        }
        try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
            // Handles both v2 envelope (routes + buddyAppId) and legacy v1 bare arrays.
            val backup = BackupData.parse(json) ?: run {
                restoreMessage = S.restoreFailed
                restoreSuccess = false
                AppLogger.log(context, LogCategory.ERROR, "Restore failed: unrecognized backup format")
                return@rememberLauncherForActivityResult
            }
            val routes = backup.routes
            val existing = RouteStorage.loadRoutes(context).map { it.id }.toSet()
            val imported = routes.filter { it.id !in existing }
            imported.forEach { RouteStorage.saveRoute(context, it) }
            if (imported.isNotEmpty()) onRoutesChanged()
            // Restore Buddy Link identity ONLY if this install has none yet, so we never
            // clobber an already-provisioned local identity (Req 2.3, D4).
            backup.buddyAppId?.takeIf { it.isNotBlank() }?.let { restoredId ->
                if (!BuddyIdentity.hasId(context)) {
                    BuddyIdentity.adoptId(context, restoredId)
                }
            }
            restoreMessage = S.restoredCount(imported.size)
            restoreSuccess = true
            AppLogger.log(context, LogCategory.BACKUP,
                "Restore complete (v${backup.version}) — ${imported.size} imported, ${routes.size - imported.size} already existed" +
                    (if (backup.buddyAppId != null) ", buddy identity present" else ""))
        } catch (e: Exception) {
            restoreMessage = S.restoreFailed
            restoreSuccess = false
            AppLogger.log(context, LogCategory.ERROR, "Restore failed: ${e.message}")
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(S.options, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.onSecondary)
            // ✕ button: close pane AND clear any stale restore message
            Text("✕", fontSize = 22.sp, color = c.onSecondary.copy(alpha = 0.7f), modifier = Modifier.clickable { restoreMessage = null; onClose() })
        }

        // ── Profile / Bio (pinned at TOP, NOT collapsible) ────────────────────
        Spacer(Modifier.height(20.dp)); Divider(color = c.onSecondary.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("👤", fontSize = 16.sp)
            Text(S.myProfile, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondary)
        }
        Spacer(Modifier.height(16.dp))
        ProfileSection(userName = userName, onUserNameChanged = onUserNameChanged)

        // ── App Theme ─────────────────────────────────────────────────────────
        CollapsibleSection(title = S.appTheme, leadingEmoji = "🎨") {
        com.example.joggingapp.ui.theme.AppTheme.values().forEach { theme ->
            val (p, s, a) = com.example.joggingapp.ui.theme.getPreviewColors(theme)
            val selected = theme == currentTheme
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (selected) c.primary.copy(alpha = 0.25f) else Color.Transparent)
                .clickable { AppLogger.log(context, LogCategory.UI, "Theme changed to ${theme.displayName}"); onThemeChanged(theme) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Color strip preview — thin white ring around each dot so they stand
                // out against the pane and don't bleed into each other.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(p).border(1.dp, Color.White, CircleShape))
                    Box(Modifier.size(16.dp).clip(CircleShape).background(s).border(1.dp, Color.White, CircleShape))
                    Box(Modifier.size(16.dp).clip(CircleShape).background(a).border(1.dp, Color.White, CircleShape))
                }
                Text("${theme.emoji} ${theme.displayName}", fontSize = 13.sp,
                    color = c.onSecondary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                if (selected) Text("✓", fontSize = 14.sp, color = c.success)
            }
            Spacer(Modifier.height(4.dp))
        }
        } // end App Theme

        // ── Route Colour ──────────────────────────────────────────────────────
        CollapsibleSection(title = S.routeColour, leadingEmoji = "🎨") {
        ROUTE_COLORS.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (name, color) -> val idx = ROUTE_COLORS.indexOfFirst { it.first == name }
                    Row(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (idx == colorIdx) c.primary.copy(alpha = 0.25f) else Color.Transparent).clickable { onColorSelected(idx) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(color)); Text(colorNames.getOrElse(idx) { name }, fontSize = 12.sp, color = c.onSecondary)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }; Spacer(Modifier.height(4.dp))
        }
        } // end Route Colour

        // ── Exercise Targets (opens a full screen) ────────────────────────────
        Spacer(Modifier.height(20.dp)); Divider(color = c.onSecondary.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.primary.copy(alpha = 0.15f))
            .clickable { AppLogger.log(context, LogCategory.UI, "Exercise Targets opened"); onOpenExerciseTargets() }
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎯", fontSize = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(S.exerciseTargets, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
                Text(S.exerciseTargetsSubtitle, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.7f))
            }
            Text("›", fontSize = 20.sp, color = c.onSecondary.copy(alpha = 0.7f))
        }

        // ── Sharing ───────────────────────────────────────────────────────────
        CollapsibleSection(title = S.sharing, leadingEmoji = "📤") {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.primary.copy(alpha = 0.15f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(S.includeMapWhenSharing, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.Medium)
            Box(modifier = Modifier.size(40.dp, 24.dp).clip(RoundedCornerShape(12.dp))
                .background(if (shareWithMap) c.success else c.divider)
                .clickable { shareWithMap = !shareWithMap; prefs.edit().putBoolean("share_with_map", shareWithMap).apply() },
                contentAlignment = if (shareWithMap) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(modifier = Modifier.size(20.dp).padding(2.dp).clip(CircleShape).background(Color.White))
            }
        }
        } // end Sharing

        // ── Recent Runs ───────────────────────────────────────────────────────
        CollapsibleSection(title = S.recentRuns, leadingEmoji = "🏃") {
        if (savedRoutes.isEmpty()) { Text(S.noRunsYet.substringBefore("\n"), fontSize = 13.sp, color = c.onSecondary.copy(alpha = 0.6f)) }
        else {
            savedRoutes.take(5).forEach { route -> val ts = SimpleDateFormat("dd/MM • HH:mm", Locale.getDefault()).format(Date(route.timestamp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ts, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.8f)); Text(String.format("%.2f ${S.km}", calcDistanceKm(route.points)), fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.6f))
                }; Divider(color = c.onSecondary.copy(alpha = 0.15f)) }
            Spacer(Modifier.height(12.dp)); Text(S.clearAllRuns, fontSize = 13.sp, color = c.error, modifier = Modifier.clickable {
                AppLogger.log(context, LogCategory.UI, "All runs cleared — had ${savedRoutes.size} routes")
                onClearRuns()
            })
        }
        } // end Recent Runs

        // ── Share App + Backup/Restore ────────────────────────────────────────
        CollapsibleSection(title = S.shareApp, leadingEmoji = "📲") {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.primary.copy(alpha = 0.15f))
            .clickable { AppLogger.log(context, LogCategory.UI, "Share app tapped — user=$userName"); shareApp(context, userName) }
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📲", fontSize = 20.sp)
            Column {
                Text(S.shareAppWithFriends, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
                Text(S.shareAppSubtitle, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.7f))
            }
        }

        // Backup section
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.success.copy(alpha = 0.15f))
            .clickable { AppLogger.log(context, LogCategory.BACKUP, "Backup tapped — ${savedRoutes.size} routes"); backupRuns(context) }
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("💾", fontSize = 20.sp)
            Column {
                Text(S.backupRuns, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
                Text(S.backupRunsSubtitle, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(c.accent.copy(alpha = 0.15f))
            .clickable { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
            .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📂", fontSize = 20.sp)
            Column {
                Text(S.restoreRuns, fontSize = 13.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
                Text(S.restoreRunsSubtitle, fontSize = 10.sp, color = c.onSecondary.copy(alpha = 0.7f))
            }
        }
        restoreMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, fontSize = 12.sp, color = if (restoreSuccess) c.success else c.error)
        }
        } // end Share App + Backup/Restore

        // ── Language ──────────────────────────────────────────────────────────
        CollapsibleSection(title = S.language, leadingEmoji = "🌐") {
        Language.values().forEach { lang ->
            val selected = lang == currentLanguage
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) c.primary.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable {
                        AppLogger.log(context, LogCategory.UI, "Language changed to ${lang.displayName}")
                        onLanguageChanged(lang)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Flag graphic in a circle
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(c.surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(lang.flag, fontSize = 20.sp)
                }
                Text(
                    lang.nativeName,
                    fontSize = 14.sp,
                    color = c.onSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (selected) Text("✓", fontSize = 16.sp, color = c.success)
            }
            Spacer(Modifier.height(6.dp))
        }
        } // end Language

        // ── Buddy Link (LAST regular section) ─────────────────────────────────
        if (buddyController != null) {
            BuddyLinkSection(controller = buddyController, userName = userName)
            // Surface a transient invite-handling result (expired / invalid / pending).
            buddyInviteMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.8f))
                LaunchedEffect(msg) { delay(6000L); onBuddyInviteMessageShown() }
            }
        }

        // ── Diagnostics / App Log (SECRET: hidden until version triple-tapped) ─
        if (showDebugConsole) {
        Spacer(Modifier.height(14.dp)); Divider(color = c.onSecondary.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

        // Category filter state — hoisted so it survives recompositions
        var logFilter by remember { mutableStateOf<LogCategory?>(null) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(S.diagnostics, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // "All" chip
                val allSelected = logFilter == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (allSelected) c.primary else c.primary.copy(alpha = 0.15f))
                        .clickable { logFilter = null }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(S.all, fontSize = 9.sp,
                        color = if (allSelected) c.onPrimary else c.onSecondary,
                        fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Normal)
                }
                // One chip per category
                LogCategory.values().forEach { cat ->
                    val sel = logFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) c.primary else c.primary.copy(alpha = 0.15f))
                            .clickable { logFilter = cat }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(cat.emoji, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Live log entries — refresh every 2 seconds while pane is open
        var logEntries by remember { mutableStateOf(AppLogger.getEntries(100)) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(2000L)
                logEntries = AppLogger.getEntries(100)
            }
        }

        // Determine which entries to show based on filter
        val visibleEntries = remember(logEntries, logFilter) {
            val base = if (logFilter == null) logEntries else logEntries.filter { it.category == logFilter }
            base.takeLast(100).reversed() // newest first
        }

        // Colour per category
        fun categoryColor(cat: LogCategory): Color = when (cat) {
            LogCategory.GPS      -> Color(0xFF29B6F6)
            LogCategory.TRACKING -> Color(0xFF66BB6A)
            LogCategory.SERVICE  -> Color(0xFFFFB74D)
            LogCategory.SAVE     -> Color(0xFF81C784)
            LogCategory.NOTIF    -> Color(0xFFBA68C8)
            LogCategory.UI       -> Color(0xFF4FC3F7)
            LogCategory.BACKUP   -> Color(0xFFFFD54F)
            LogCategory.PROFILE  -> Color(0xFFF48FB1)
            LogCategory.ERROR    -> Color(0xFFEF5350)
            LogCategory.INFO     -> Color(0xFFB0BEC5)
        }

        if (visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.primary.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Text(S.noLogEntries, fontSize = 12.sp, color = c.onSecondary.copy(alpha = 0.5f))
            }
        } else {
            // Fixed-height scrollable log box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1117)) // dark terminal background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    visibleEntries.forEach { entry ->
                        val catColor = categoryColor(entry.category)
                        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(entry.timestamp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Timestamp
                            Text(ts, fontSize = 9.sp, color = Color(0xFF6E7681),
                                modifier = Modifier.width(56.dp))
                            // Category pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(catColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(entry.category.label, fontSize = 8.sp, color = catColor,
                                    fontWeight = FontWeight.Bold)
                            }
                            // Message
                            Text(
                                entry.message,
                                fontSize = 9.sp,
                                color = if (entry.category == LogCategory.ERROR) Color(0xFFEF5350) else Color(0xFFCDD9E5),
                                modifier = Modifier.weight(1f),
                                lineHeight = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Share log
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.primary.copy(alpha = 0.15f))
                    .clickable {
                        AppLogger.log(context, LogCategory.UI, "Log shared by user")
                        AppLogger.share(context)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(S.shareLog, fontSize = 12.sp, color = c.onSecondary, fontWeight = FontWeight.SemiBold)
            }
            // Clear log
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.error.copy(alpha = 0.15f))
                    .clickable {
                        AppLogger.clear(context)
                        logEntries = emptyList()
                        AppLogger.log(context, LogCategory.UI, "Log cleared by user")
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(S.clearLog, fontSize = 12.sp, color = c.error, fontWeight = FontWeight.SemiBold)
            }
        }
        } // end if (showDebugConsole)

        Spacer(Modifier.height(24.dp))

        // ── Build number — pinned at the very bottom of the options pane ──────
        // SECRET: triple-tapping this within 1.5s toggles the hidden debug console.
        // Read dynamically from BuildConfig so it never drifts from the real version.
        Text(
            "V${BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color = c.onSecondary.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable {
                val now = System.currentTimeMillis()
                versionTapCount = if (now - lastVersionTapMs <= 1500L) versionTapCount + 1 else 1
                lastVersionTapMs = now
                if (versionTapCount >= 3) {
                    versionTapCount = 0
                    showDebugConsole = !showDebugConsole
                    AppLogger.log(context, LogCategory.UI,
                        "Debug console ${if (showDebugConsole) "revealed" else "hidden"} via version triple-tap")
                }
            }
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable fun StatItem(value: String, label: String) { val c = JogginTheme.colors; Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.onBackground); Text(label, fontSize = 11.sp, color = c.textSecondary, textAlign = TextAlign.Center) } }
@Composable fun VDivider() { val c = JogginTheme.colors; Box(modifier = Modifier.width(1.dp).height(40.dp).background(c.divider)) }
@Composable fun BigButton(label: String, bgColor: Color, onClick: () -> Unit) { val c = JogginTheme.colors; Box(modifier = Modifier.height(52.dp).width(140.dp).clip(RoundedCornerShape(26.dp)).background(bgColor).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimary) } }


// ── Share route via WhatsApp / intent ─────────────────────────────────────────

fun shareRoute(context: Context, route: SavedRoute) {
    val distKm = calcDistanceKm(route.points)
    // Use calcDurationSeconds so pause sentinels don't inflate the duration
    val dur = calcDurationSeconds(route.points)
    // Respect blended activity types if the run mixed modes
    val isBlended = (route.activityTypes ?: emptyList()).distinct().size > 1
    val actLabel = when {
        isBlended -> (route.activityTypes ?: emptyList()).distinct().joinToString(" + ") { labelForMode(it) }
        else -> when (route.activityType) { 1 -> "Run"; 2 -> "Cycle"; else -> "Walk" }
    }
    val actEmoji = when {
        isBlended -> (route.activityTypes ?: emptyList()).distinct().joinToString("") { emojiForMode(it) }
        else -> when (route.activityType) { 1 -> "🏃"; 2 -> "🚴"; else -> "🚶" }
    }
    val dateStr = SimpleDateFormat(DATE_FMT_FULL, Locale.getDefault()).format(Date(route.timestamp))

    val text = buildString {
        appendLine("$actEmoji *$actLabel Complete* — Joggin App")
        appendLine("$dateStr")
        appendLine("━━━━━━━━━━━━━━━")
        appendLine("⏱ Time: ${fmtTime(dur)}")
        appendLine("📍 Distance: ${String.format("%.2f", distKm)} km")
        appendLine("⚡ Avg Speed: ${String.format("%.1f", route.avgSpeed * 3.6f)} km/h")
        appendLine("🔼 Max Speed: ${String.format("%.1f", route.maxSpeed * 3.6f)} km/h")
        appendLine("🔽 Min Speed: ${String.format("%.1f", route.minSpeed * 3.6f)} km/h")
        if (route.steps > 0) appendLine("👟 Steps: ${route.steps}")
        appendLine("🔥 Calories: ${String.format("%.0f", route.calories)} kcal")
        appendLine("━━━━━━━━━━━━━━━")
        appendLine("Tracked with Joggin 🏃‍♂️")
    }

    // Check if user wants map included
    val prefs = context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
    val includeMap = prefs.getBoolean("share_with_map", true)

    if (includeMap && route.points.size >= 2) {
        // Run tile download on background thread to avoid NetworkOnMainThreadException
        Thread {
            val bmpUri = generateRouteBitmap(context, route.points)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (bmpUri != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_STREAM, bmpUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Share run via…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                } else {
                    // Fallback: text only if bitmap generation failed
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Share run via…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                }
            }
        }.start()
        return
    }

    // Fallback: text only
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share run via…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

/** Generates a map bitmap for sharing by downloading map tiles (provider set in MapTiles) */
fun generateRouteBitmap(context: Context, points: List<Point>): android.net.Uri? {
    if (points.size < 2) return null
    return try {
        val width = 600; val height = 400

        val minLat = points.minOf { it.lat }; val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }; val maxLon = points.maxOf { it.lon }
        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2

        // Calculate zoom level
        val latSpan = maxLat - minLat; val lonSpan = maxLon - minLon
        val maxSpan = maxOf(latSpan, lonSpan)
        val zoom = when {
            maxSpan > 0.1  -> 12; maxSpan > 0.05 -> 13; maxSpan > 0.02 -> 14
            maxSpan > 0.01 -> 15; maxSpan > 0.005 -> 16; else -> 17
        }

        // Download tiles and stitch them into a bitmap
        val bmp = downloadTileBitmap(centerLat, centerLon, zoom, width, height)
        val canvas = android.graphics.Canvas(bmp)

        // Convert geo coords to pixel positions on this bitmap
        val n = Math.pow(2.0, zoom.toDouble())
        fun lonToPixX(lon: Double): Float {
            val tileX = (lon + 180.0) / 360.0 * n
            val centerTileX = (centerLon + 180.0) / 360.0 * n
            return (width / 2f + ((tileX - centerTileX) * 256).toFloat())
        }
        fun latToPixY(lat: Double): Float {
            val latRad = Math.toRadians(lat)
            val tileY = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
            val centerLatRad = Math.toRadians(centerLat)
            val centerTileY = (1.0 - Math.log(Math.tan(centerLatRad) + 1.0 / Math.cos(centerLatRad)) / Math.PI) / 2.0 * n
            return (height / 2f + ((tileY - centerTileY) * 256).toFloat())
        }

        // Draw route shadow
        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(80, 0, 0, 0)
            strokeWidth = 10f; style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val shadowPath = android.graphics.Path()
        shadowPath.moveTo(lonToPixX(points[0].lon) + 2, latToPixY(points[0].lat) + 2)
        points.drop(1).forEach { shadowPath.lineTo(lonToPixX(it.lon) + 2, latToPixY(it.lat) + 2) }
        canvas.drawPath(shadowPath, shadowPaint)

        // Draw route line
        val routePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#66BB6A")
            strokeWidth = 8f; style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val routePath = android.graphics.Path()
        routePath.moveTo(lonToPixX(points[0].lon), latToPixY(points[0].lat))
        points.drop(1).forEach { routePath.lineTo(lonToPixX(it.lon), latToPixY(it.lat)) }
        canvas.drawPath(routePath, routePaint)

        // Start dot
        val fx = lonToPixX(points.first().lon); val fy = latToPixY(points.first().lat)
        val gp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#43A047") }
        canvas.drawCircle(fx, fy, 12f, gp)
        gp.color = android.graphics.Color.WHITE; canvas.drawCircle(fx, fy, 6f, gp)

        // End dot
        val lx = lonToPixX(points.last().lon); val ly = latToPixY(points.last().lat)
        val rp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#E53935") }
        canvas.drawCircle(lx, ly, 12f, rp)
        rp.color = android.graphics.Color.WHITE; canvas.drawCircle(lx, ly, 6f, rp)

        // Watermark
        val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 50, 50, 50); textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("Joggin 🏃", width - 140f, height - 14f, tp)

        // Save
        val file = java.io.File(context.cacheDir, "share_route.png")
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
        bmp.recycle()
        androidx.core.content.FileProvider.getUriForFile(context, "com.example.joggingapp.fileprovider", file)
    } catch (_: Exception) { null }
}

/** Downloads and stitches map tiles into a single bitmap (provider set in MapTiles) */
private fun downloadTileBitmap(centerLat: Double, centerLon: Double, zoom: Int, width: Int, height: Int): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#F2EFE9"))

    val n = Math.pow(2.0, zoom.toDouble())
    val centerLatRad = Math.toRadians(centerLat)
    val centerTileXd = (centerLon + 180.0) / 360.0 * n
    val centerTileYd = (1.0 - Math.log(Math.tan(centerLatRad) + 1.0 / Math.cos(centerLatRad)) / Math.PI) / 2.0 * n

    val tilesX = (width / 256) + 2
    val tilesY = (height / 256) + 2
    val startTileX = centerTileXd.toInt() - tilesX / 2
    val startTileY = centerTileYd.toInt() - tilesY / 2

    for (ty in 0 until tilesY) {
        for (tx in 0 until tilesX) {
            val tileX = startTileX + tx
            val tileY = startTileY + ty
            if (tileX < 0 || tileY < 0 || tileX >= n.toInt() || tileY >= n.toInt()) continue

            val pixX = ((tileX - centerTileXd) * 256 + width / 2).toInt()
            val pixY = ((tileY - centerTileYd) * 256 + height / 2).toInt()

            try {
                // URL + subdomain round-robin come from the central MapTiles config
                val url = java.net.URL(MapTiles.rawTileUrl(zoom, tileX, tileY))
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", MapTiles.USER_AGENT)
                conn.setRequestProperty("Accept", "image/png,image/*")
                if (conn.responseCode == 200) {
                    val tile = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    if (tile != null) {
                        canvas.drawBitmap(tile, pixX.toFloat(), pixY.toFloat(), null)
                        tile.recycle()
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }
    return bmp
}


// ── Interactive route map for history cards ────────────────────────────────────

// ── Share App ─────────────────────────────────────────────────────────────────

// Public download page (Firebase Hosting). Sharing a LINK — not the raw .apk file —
// avoids messaging apps (WhatsApp, etc.) corrupting/blocking the attachment, which was
// causing "App not installed" on the receiving phone.
const val JOGGIN_DOWNLOAD_URL = "https://joggin-a69a7.web.app/get"

fun shareApp(context: Context, userName: String = "") {
    val greeting = if (userName.isNotBlank())
        "Hey! $userName has invited you to join them on Joggin' 🏃‍♂️"
    else
        "Hey! Join me on Joggin' 🏃‍♂️"

    val subject = if (userName.isNotBlank())
        "$userName is sharing the Joggin' app with you"
    else
        "Someone is sharing the Joggin' app with you"

    // Share a clean download LINK rather than attaching the APK. The link opens the
    // Firebase-hosted install page, which serves the APK over HTTPS (no corruption).
    val message = "$greeting\n\n" +
        "Joggin' is a free GPS fitness tracker for walking, running & cycling. " +
        "Track your routes, earn achievements, and stay active together!\n\n" +
        "📲 Install it here:\n$JOGGIN_DOWNLOAD_URL"

    AppLogger.log(context, LogCategory.UI, "Share app: sharing download link")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, subject)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLogger.log(context, LogCategory.ERROR, "Share app failed: ${e.message}")
    }
}


// ── Backup & Restore ──────────────────────────────────────────────────────────

fun backupRuns(context: Context) {
    try {
        val routes = RouteStorage.loadRoutes(context)
        if (routes.isEmpty()) {
            android.widget.Toast.makeText(context, "No runs to backup", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // Backup format v2: versioned envelope carrying the routes + the Buddy Link
        // App ID (if provisioned) so identity/links survive reinstall or a new device.
        val json = BackupData.toJson(routes, BuddyIdentity.getId(context))

        // Write to app-private external files (no permission needed on any API level)
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = java.io.File(dir, "joggin_backup.json")
        file.writeText(json)

        // Share via intent so the user can save it wherever they like (Drive, Files, etc.)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "com.example.joggingapp.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Joggin backup — ${routes.size} run(s)")
            putExtra(Intent.EXTRA_TEXT, "Joggin run backup — ${routes.size} run(s) saved")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Save backup via…")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Backup failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}


