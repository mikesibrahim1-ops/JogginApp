package com.example.joggingapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class ForegroundLocationService : Service() {
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    // Last accepted location — used for distance filtering
    private var lastAcceptedLocation: Location? = null

    // Live stats for notification display
    private var totalDistanceM = 0.0
    private var speedSumMs = 0.0
    private var speedCount = 0
    private var startTimeMs = 0L
    private var pausedAtMs = 0L        // when the current pause started
    private var totalPausedMs = 0L     // cumulative paused time
    private val notifHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var notifUpdater: Runnable? = null

    // Track all accepted points for recovery if activity doesn't receive end broadcast
    private val trackedPoints = mutableListOf<Point>()

    // Throttle GPS-accepted log entries — log every 10th point to avoid flooding
    private var acceptedPointCount = 0

    companion object {
        const val ACTION_LOCATION_BROADCAST = "com.example.joggingapp.LOCATION_UPDATE"
        const val ACTION_PAUSE  = "com.example.joggingapp.PAUSE"
        const val ACTION_RESUME = "com.example.joggingapp.RESUME"
        const val ACTION_END    = "com.example.joggingapp.END"
        const val EXTRA_LAT   = "lat"
        const val EXTRA_LON   = "lon"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_TIME  = "time"

        // Filtering thresholds
        private const val MIN_ACCURACY_M        = 15f
        private const val MIN_DISTANCE_M        = 8f
        private const val MIN_SPEED_MS          = 0.8f
        private const val WARMUP_POINTS         = 8
        private const val MAX_PLAUSIBLE_SPEED_MS = 12f

        // Auto-end the activity when battery falls to/below this (and not charging),
        // so the run is saved before the phone dies rather than being lost.
        private const val LOW_BATTERY_END_PERCENT = 5
    }

    private var pointsReceived = 0
    private var isPaused = false

    // Battery watchdog — auto-ends the activity when the phone is about to die.
    private var batteryReceiver: BroadcastReceiver? = null
    private var lowBatteryEndTriggered = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (isPaused) return
                val loc = result.lastLocation ?: return
                broadcastIfValid(loc)
            }
        }
        AppLogger.log(this, LogCategory.SERVICE, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            // ── END ───────────────────────────────────────────────────────────
            "STOP", ACTION_END -> {
                AppLogger.log(this, LogCategory.NOTIF,
                    "END received from notification — paused=$isPaused points=${trackedPoints.size}")
                endActivityAndStop(reason = "notification")
                return START_NOT_STICKY
            }

            // ── PAUSE ─────────────────────────────────────────────────────────
            ACTION_PAUSE -> {
                Log.d("Joggin", "Pause requested from notification")
                AppLogger.log(this, LogCategory.NOTIF,
                    "PAUSE received — points so far=${trackedPoints.size} dist=${String.format("%.2f", totalDistanceM / 1000.0)}km")

                isPaused = true
                pausedAtMs = System.currentTimeMillis()
                notifUpdater?.let { notifHandler.removeCallbacks(it) }
                try { fusedClient.removeLocationUpdates(callback) } catch (_: Exception) {}
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, buildNotification(createNotificationChannel()))
                sendBroadcast(Intent("com.example.joggingapp.ACTION_PAUSE_FROM_NOTIF"))
                return START_STICKY
            }

            // ── RESUME ────────────────────────────────────────────────────────
            ACTION_RESUME -> {
                Log.d("Joggin", "Resume requested from notification")
                if (pausedAtMs > 0L) {
                    val pausedForMs = System.currentTimeMillis() - pausedAtMs
                    totalPausedMs += pausedForMs
                    AppLogger.log(this, LogCategory.NOTIF,
                        "RESUME received — was paused for ${pausedForMs / 1000}s total paused=${totalPausedMs / 1000}s")
                    pausedAtMs = 0L
                }
                isPaused = false

                val channelId = createNotificationChannel()
                notifUpdater = object : Runnable {
                    override fun run() {
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(1, buildNotification(channelId))
                        notifHandler.postDelayed(this, 1000)
                    }
                }
                notifHandler.postDelayed(notifUpdater!!, 1000)
                // Reset last accepted location so distance filter doesn't compare
                // against the pre-pause point
                lastAcceptedLocation = null
                startGpsUpdates()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, buildNotification(channelId))
                sendBroadcast(Intent("com.example.joggingapp.ACTION_RESUME_FROM_NOTIF"))
                return START_STICKY
            }
        }

        // ── FRESH START ───────────────────────────────────────────────────────
        Log.d("Joggin", "Foreground service starting")
        isPaused = false
        lastAcceptedLocation = null
        pointsReceived = 0
        acceptedPointCount = 0
        totalDistanceM = 0.0
        speedSumMs = 0.0
        speedCount = 0
        trackedPoints.clear()
        startTimeMs = System.currentTimeMillis()
        lowBatteryEndTriggered = false
        registerBatteryWatchdog()

        AppLogger.log(this, LogCategory.SERVICE, "Tracking started — GPS request interval 3s min 1.5s")

        val channelId = createNotificationChannel()
        startForeground(1, buildNotification(channelId))

        notifUpdater = object : Runnable {
            override fun run() {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(1, buildNotification(channelId))
                notifHandler.postDelayed(this, 1000)
            }
        }
        notifHandler.postDelayed(notifUpdater!!, 1000)
        startGpsUpdates()

        return START_STICKY
    }

    // ── Battery watchdog ────────────────────────────────────────────────────────

    /**
     * Watches battery level while tracking. When the battery reaches
     * LOW_BATTERY_END_PERCENT or lower and the device is NOT charging, the activity
     * is auto-ended (saved) so the run isn't lost when the phone dies.
     */
    private fun registerBatteryWatchdog() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                if (lowBatteryEndTriggered) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return
                val pct = (level * 100) / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL
                if (pct <= LOW_BATTERY_END_PERCENT && !charging) {
                    lowBatteryEndTriggered = true
                    AppLogger.log(this@ForegroundLocationService, LogCategory.SERVICE,
                        "Low battery ($pct%) — auto-ending activity to save the run before shutdown")
                    endActivityAndStop(reason = "low_battery")
                }
            }
        }
        try {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            AppLogger.log(this, LogCategory.ERROR, "Failed to register battery watchdog: ${e.message}")
        }
    }

    private fun unregisterBatteryWatchdog() {
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        batteryReceiver = null
    }

    /**
     * Ends the activity and stops the service. Shared by the notification "End"
     * action and the low-battery watchdog so both use the same proven sequence:
     * stop GPS, persist points for recovery, notify the UI to save + show the
     * summary, then tear down the foreground service.
     */
    private fun endActivityAndStop(reason: String) {
        // Stop ticker so it cannot re-post after we cancel
        notifUpdater?.let { notifHandler.removeCallbacks(it) }
        notifUpdater = null

        // Stop GPS in case we are ending while still actively tracking
        try { fusedClient.removeLocationUpdates(callback) } catch (_: Exception) {}

        // Save tracked points for activity recovery (survives process death)
        saveTrackedPoints()

        // Notify the activity to run endRun() (saves route + shows summary)
        sendBroadcast(Intent("com.example.joggingapp.ACTION_END_FROM_NOTIF"))

        // Tear down the foreground notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(1)
        } catch (_: Exception) {}

        AppLogger.log(this, LogCategory.SERVICE,
            "Service stopping ($reason) — total dist=${String.format("%.2f", totalDistanceM / 1000.0)}km accepted=$acceptedPointCount")
        stopSelf()
    }

    // ── GPS updates ───────────────────────────────────────────────────────────

    private fun startGpsUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()
        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && !isPaused) {
                    Log.d("Joggin", "Last location available: ${loc.latitude}, ${loc.longitude}")
                    AppLogger.log(this, LogCategory.GPS,
                        "Last known location: lat=${String.format("%.5f", loc.latitude)} lon=${String.format("%.5f", loc.longitude)} acc=${loc.accuracy}m")
                    broadcastLocation(loc)
                }
            }
            fusedClient.requestLocationUpdates(req, callback, null)
            AppLogger.log(this, LogCategory.GPS, "GPS updates requested — PRIORITY_HIGH_ACCURACY")
        } catch (e: SecurityException) {
            AppLogger.log(this, LogCategory.ERROR, "SecurityException requesting GPS updates: ${e.message}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d("Joggin", "Foreground service stopping")
        unregisterBatteryWatchdog()
        notifUpdater?.let { notifHandler.removeCallbacks(it) }
        notifUpdater = null
        lastAcceptedLocation = null
        pointsReceived = 0
        try { fusedClient.removeLocationUpdates(callback) } catch (_: Exception) {}
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(1)
        } catch (_: Exception) {}
        AppLogger.log(this, LogCategory.SERVICE, "Service destroyed")
        super.onDestroy()
    }

    // ── GPS point filtering ───────────────────────────────────────────────────

    private fun broadcastIfValid(location: Location) {
        pointsReceived++

        // 1. Discard poor accuracy
        if (location.hasAccuracy() && location.accuracy > MIN_ACCURACY_M) {
            Log.d("Joggin", "Dropped: accuracy ${location.accuracy}m")
            AppLogger.log(this, LogCategory.GPS,
                "Point #$pointsReceived DROPPED — poor accuracy ${String.format("%.1f", location.accuracy)}m (max ${MIN_ACCURACY_M}m)")
            return
        }

        // 2. Discard warm-up points while GPS stabilises
        if (pointsReceived <= WARMUP_POINTS) {
            Log.d("Joggin", "Dropped: warm-up $pointsReceived/$WARMUP_POINTS")
            if (pointsReceived == 1)
                AppLogger.log(this, LogCategory.GPS, "Warm-up phase started — discarding first $WARMUP_POINTS points")
            if (pointsReceived == WARMUP_POINTS)
                AppLogger.log(this, LogCategory.GPS, "Warm-up complete — accepting points from now")
            return
        }

        // 3. Discard if too close to last accepted point
        val last = lastAcceptedLocation
        if (last != null) {
            val distM = last.distanceTo(location)
            if (distM < MIN_DISTANCE_M) {
                Log.d("Joggin", "Dropped: ${distM}m < ${MIN_DISTANCE_M}m threshold")
                return  // not logged — too noisy
            }

            // 4. Speed sanity cross-check
            val timeDeltaSec = (location.time - last.time) / 1000f
            val impliedSpeedMs = if (timeDeltaSec > 0) distM / timeDeltaSec else 0f
            val reportedSpeed = location.speed
            val effectiveSpeed = when {
                reportedSpeed > MAX_PLAUSIBLE_SPEED_MS -> impliedSpeedMs
                reportedSpeed > impliedSpeedMs * 2.5f  -> impliedSpeedMs
                else -> reportedSpeed
            }
            val cleanSpeed = if (effectiveSpeed < MIN_SPEED_MS) 0f else effectiveSpeed

            totalDistanceM += distM.toDouble()
            if (cleanSpeed > 0f) { speedSumMs += cleanSpeed; speedCount++ }
            lastAcceptedLocation = location
            acceptedPointCount++

            Log.d("Joggin", "Accepted: dist=${distM}m implied=${impliedSpeedMs}m/s reported=${reportedSpeed}m/s clean=${cleanSpeed}m/s")

            // Log every 10th accepted point to keep log readable
            if (acceptedPointCount % 10 == 0) {
                AppLogger.log(this, LogCategory.GPS,
                    "Point #$acceptedPointCount accepted — " +
                    "dist=${String.format("%.1f", distM)}m " +
                    "speed=${String.format("%.1f", cleanSpeed * 3.6f)}km/h " +
                    "total=${String.format("%.2f", totalDistanceM / 1000.0)}km " +
                    "acc=${String.format("%.1f", location.accuracy)}m")
            }
            sendPoint(location, cleanSpeed)
        } else {
            // First accepted point
            lastAcceptedLocation = location
            acceptedPointCount++
            AppLogger.log(this, LogCategory.GPS,
                "First GPS fix — lat=${String.format("%.5f", location.latitude)} " +
                "lon=${String.format("%.5f", location.longitude)} " +
                "acc=${String.format("%.1f", location.accuracy)}m")
            sendPoint(location, 0f)
        }
    }

    private fun sendPoint(location: Location, cleanSpeed: Float) {
        val point = Point(location.latitude, location.longitude, System.currentTimeMillis(), cleanSpeed)
        trackedPoints.add(point)
        try {
            sendBroadcast(Intent(ACTION_LOCATION_BROADCAST).apply {
                putExtra(EXTRA_LAT, location.latitude)
                putExtra(EXTRA_LON, location.longitude)
                putExtra(EXTRA_SPEED, cleanSpeed)
                putExtra(EXTRA_TIME, System.currentTimeMillis())
            })
        } catch (_: Exception) {}
    }

    private fun broadcastLocation(location: Location) {
        broadcastIfValid(location)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(channelId: String): Notification {
        // Localize the notification to the user's chosen language (the service has no
        // Compose LocalStrings, so resolve it from persisted prefs each build).
        val S = stringsFor(loadLanguage(this))
        val currentPauseMs = if (isPaused && pausedAtMs > 0L) System.currentTimeMillis() - pausedAtMs else 0L
        val elapsed = (System.currentTimeMillis() - startTimeMs - totalPausedMs - currentPauseMs) / 1000L
        val min = elapsed / 60; val sec = elapsed % 60
        val timeStr = "%02d:%02d".format(min, sec)
        val distKm = totalDistanceM / 1000.0
        val avgKmh = if (speedCount > 0) (speedSumMs / speedCount) * 3.6 else 0.0

        val toggleAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val toggleIntent = Intent(this, ForegroundLocationService::class.java).apply { action = toggleAction }
        val togglePending = android.app.PendingIntent.getService(this, 1, toggleIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val endIntent = Intent(this, ForegroundLocationService::class.java).apply { action = ACTION_END }
        val endPending = android.app.PendingIntent.getService(this, 2, endIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        val statusPrefix = if (isPaused) "⏸ ${S.notifPaused}" else "🏃 ${S.notifRunning}"
        val toggleIcon  = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleLabel = if (isPaused) S.resume else S.pause

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = android.app.PendingIntent.getActivity(this, 0, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("$statusPrefix — $timeStr")
            .setContentText("${String.format("%.2f", distKm)} ${S.km} • ${String.format("%.1f", avgKmh)} ${S.statKmh}")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("⏱ ${S.time}: $timeStr\n📍 ${S.distance}: ${String.format("%.2f", distKm)} ${S.km}\n⚡ ${S.avgSpeed}: ${String.format("%.1f", avgKmh)} ${S.statKmh}"))
            .addAction(toggleIcon, toggleLabel, togglePending)
            .addAction(android.R.drawable.ic_delete, S.endRun, endPending)
            .build()
    }

    // ── Crash recovery ────────────────────────────────────────────────────────

    private fun saveTrackedPoints() {
        if (trackedPoints.size < 2) {
            AppLogger.log(this, LogCategory.SAVE,
                "Crash recovery skipped — only ${trackedPoints.size} point(s) tracked")
            return
        }
        try {
            val prefs = getSharedPreferences("jog_service", Context.MODE_PRIVATE)
            val json = com.google.gson.Gson().toJson(trackedPoints)
            prefs.edit()
                .putString("pending_route_points", json)
                .putLong("pending_route_time", startTimeMs)
                .putBoolean("pending_route_exists", true)
                .apply()
            Log.d("Joggin", "Saved ${trackedPoints.size} tracked points for recovery")
            AppLogger.log(this, LogCategory.SAVE,
                "Crash recovery data saved — ${trackedPoints.size} points, dist=${String.format("%.2f", totalDistanceM / 1000.0)}km")
        } catch (e: Exception) {
            Log.e("Joggin", "Failed to save tracked points", e)
            AppLogger.log(this, LogCategory.ERROR, "Failed to save crash recovery data: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel(): String {
        val channelId = "joggin_tracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelName = stringsFor(loadLanguage(this)).notifChannelName
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        return channelId
    }
}
