package com.example.joggingapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Buddy Link location-reporting service (Phase 5, Req 6). A foreground service kept
 * SEPARATE from [ForegroundLocationService] (the workout tracker) so it can run
 * regardless of whether a workout is active (Req 6.3, design §11).
 *
 * While running it:
 *  - broadcasts my latest fix to the backend every ~5 min ([BuddyConstants.BROADCAST_INTERVAL_MS])
 *    via [BuddyRepository.writeMyLocation] (which also appends a sparse trail point);
 *  - reacts to on-demand "request location now" flags ([BuddyRepository.observeLocationRequests])
 *    by pushing a fresh fix promptly, outside the normal cadence (Req 6.1b, §8);
 *  - shows a "broadcast on" foreground notification (Req 6.2).
 *
 * The service is started/stopped by [BuddyLinkController] as CONDITION-1 changes
 * (toggle ON + GPS on). It also self-guards: if background-location permission is
 * missing it stops immediately (Req 6b/6c). Backend failures are non-fatal — the
 * repository returns a failure result rather than throwing, so no-network is handled
 * gracefully with no crash (Req 6.5). Against [NoOpBuddyRepository] every write is an
 * inert no-op, so this service is safe to run before Firebase is configured.
 */
class BuddyLocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val repo: BuddyRepository by lazy { BuddyRepositoryProvider.get(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob())
    private var broadcastLoop: Job? = null

    companion object {
        const val ACTION_START = "com.example.joggingapp.BUDDY_START"
        const val ACTION_STOP  = "com.example.joggingapp.BUDDY_STOP"
        private const val NOTIF_ID = 2
        private const val CHANNEL_ID = "joggin_buddy"
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        AppLogger.log(this, LogCategory.SERVICE, "BuddyLocationService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.log(this, LogCategory.SERVICE,
            "BuddyLocationService onStartCommand — action=${intent?.action ?: "null"} bgPerm=${hasBackgroundLocation()}")

        // CONTRACT (V1.59 crash fix): startForegroundService() requires the service to
        // call startForeground() within ~5s or the OS kills the app with
        // ForegroundServiceDidNotStartInTimeException. Satisfy it IMMEDIATELY and
        // UNCONDITIONALLY — before any early-return branch — so a start that then stops
        // (STOP action, or missing bg-permission) can never trip the contract.
        //
        // The service declares foregroundServiceType="location" in the manifest, so on
        // API 34+ startForeground must be passed the LOCATION type; and if the location
        // permission is missing Android can throw (ForegroundServiceStartNotAllowed /
        // SecurityException). Wrap it so a throw here cleanly stops the service instead
        // of crashing the app.
        if (!enterForeground()) {
            stopEverything()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            AppLogger.log(this, LogCategory.SERVICE, "BuddyLocationService stop requested")
            stopEverything()
            return START_NOT_STICKY
        }

        // Background-location permission is required to broadcast while the app is not
        // in the foreground. Without it the feature is unusable (Req 6b/6c) — stop.
        if (!hasBackgroundLocation()) {
            AppLogger.log(this, LogCategory.SERVICE,
                "BuddyLocationService cannot start — background location permission missing")
            stopEverything()
            return START_NOT_STICKY
        }

        if (broadcastLoop == null) {
            startBroadcastLoop()
            observeOnDemandRequests()
        }
        return START_STICKY
    }

    /**
     * Enters the foreground, satisfying the startForegroundService() contract. On API
     * 34+ the manifest's foregroundServiceType="location" is passed explicitly via
     * [ServiceCompat.startForeground]. Any failure (e.g. a SecurityException when the
     * location permission is absent) is logged and reported as false so the caller can
     * stop cleanly — the app must NEVER crash on service start. Returns true on success.
     */
    private fun enterForeground(): Boolean {
        return try {
            val notif = buildNotification()
            // foregroundServiceType="location" is declared in the manifest; on API 29+
            // pass the matching type explicitly (required on API 34+/targetSdk 34),
            // otherwise use the plain 2-arg overload for older platforms.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            AppLogger.log(this, LogCategory.SERVICE, "BuddyLocationService entered foreground")
            true
        } catch (e: Exception) {
            AppLogger.log(this, LogCategory.ERROR,
                "BuddyLocationService startForeground failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** Periodic ~5-min broadcast of my latest fix while the service runs (Req 6.1). */
    private fun startBroadcastLoop() {
        broadcastLoop = scope.launch {
            // V1.58 belt-and-braces ordering guard: the service is started via
            // applySharingState() independently of the controller's observe path, so its
            // first writeMyLocation could otherwise run before users/{appId}/private/self
            // exists and be DENIED. Await ensureSession once here (idempotent set-merge,
            // reuses the cached anon session; NoOp returns an inert Failure we ignore)
            // BEFORE the broadcast loop so the first fix can never precede provisioning.
            BuddyIdentity.getId(applicationContext)?.let { id ->
                repo.ensureSession(id)
            }
            while (true) {
                pushFreshFix(reason = "interval")
                delay(BuddyConstants.BROADCAST_INTERVAL_MS)
            }
        }
    }

    /**
     * Reacts to buddies asking for a fresh fix now. Emits the requesters' ids; when
     * non-empty we push a fix immediately (outside the cadence). Req 6.1b, §8.
     */
    private fun observeOnDemandRequests() {
        repo.observeLocationRequests()
            .onEach { requesters ->
                if (requesters.isNotEmpty()) {
                    AppLogger.log(this, LogCategory.SERVICE,
                        "On-demand location request received (${requesters.size}) — pushing fresh fix")
                    pushFreshFix(reason = "on-demand")
                    // G6 decision: notify the located party (transparent, low-key) that
                    // a buddy actively requested their position — not silent.
                    BuddyNotifier.notifyLocationShared(this)
                }
            }
            .launchIn(scope)
    }

    /**
     * Gets a single fresh location fix and writes it to the backend. Missing
     * permission or a null fix is logged and skipped; backend failures are surfaced
     * only in the log (non-blocking, Req 6.5).
     */
    private fun pushFreshFix(reason: String) {
        if (!hasFineLocation()) {
            AppLogger.log(this, LogCategory.GPS, "Buddy fix skipped ($reason) — no fine-location permission")
            return
        }
        val cr = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()
        try {
            fusedClient.getCurrentLocation(cr, null)
                .addOnSuccessListener { loc: Location? ->
                    if (loc == null) {
                        AppLogger.log(this, LogCategory.GPS, "Buddy fix ($reason) — no location available")
                        return@addOnSuccessListener
                    }
                    writeFix(loc, reason)
                }
                .addOnFailureListener { e ->
                    AppLogger.log(this, LogCategory.GPS, "Buddy fix ($reason) failed: ${e.message}")
                }
        } catch (e: SecurityException) {
            AppLogger.log(this, LogCategory.ERROR, "Buddy fix ($reason) SecurityException: ${e.message}")
        }
    }

    private fun writeFix(loc: Location, reason: String) {
        scope.launch {
            // Read the sharer's current mode from prefs at write time so a mid-activity
            // mode flip takes effect on the next broadcast (design §5.3). Absent/unknown
            // -> PING (fail-safe to the privacy-preserving mode).
            val mode = BuddyShareMode.fromWire(
                getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
                    .getString(BuddyConstants.PREFS_SHARE_MODE_KEY, null)
            )
            val now = System.currentTimeMillis()
            val res = repo.writeMyLocation(loc.latitude, loc.longitude, now, mode)
            when (res) {
                is BuddyResult.Success ->
                    AppLogger.log(this@BuddyLocationService, LogCategory.SERVICE,
                        "Buddy fix broadcast ($reason) — lat=${"%.4f".format(loc.latitude)} lon=${"%.4f".format(loc.longitude)}")
                is BuddyResult.Failure ->
                    // Non-fatal: offline / NoOp backend. Skip quietly (Req 6.5, Req 10).
                    AppLogger.log(this@BuddyLocationService, LogCategory.SERVICE,
                        "Buddy fix ($reason) not sent — backend ${res.reason}")
            }

            // S2 live-tracking share link: additively mirror the SAME fix into the active
            // public share doc when a live share exists. Read the token/expiry/label from
            // jog_prefs (set by createShareLink). expiresAt is passed VERBATIM — never
            // recomputed — so the write-once rule's equality holds. Non-fatal like above.
            val prefs = getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString(BuddyConstants.PREFS_ACTIVE_SHARE_TOKEN, null)
            val expiresAt = prefs.getLong(BuddyConstants.PREFS_ACTIVE_SHARE_EXPIRES_AT, 0L)
            val label = prefs.getString(BuddyConstants.PREFS_ACTIVE_SHARE_LABEL, null)
            val validity = ShareLinkValidity.decide(
                hasToken = token != null, expiresAtMs = expiresAt, revoked = false, nowMs = now
            )
            if (token != null && validity.readable) {
                when (val shareRes = repo.writeShareFix(token, loc.latitude, loc.longitude, now, expiresAt, label)) {
                    is BuddyResult.Success ->
                        AppLogger.log(this@BuddyLocationService, LogCategory.SERVICE,
                            "Share-link fix mirrored ($reason)")
                    is BuddyResult.Failure ->
                        AppLogger.log(this@BuddyLocationService, LogCategory.SERVICE,
                            "Share-link fix ($reason) not sent — backend ${shareRes.reason}")
                }
            }
        }
    }

    private fun stopEverything() {
        broadcastLoop?.cancel(); broadcastLoop = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        broadcastLoop?.cancel(); broadcastLoop = null
        scope.cancel()
        AppLogger.log(this, LogCategory.SERVICE, "BuddyLocationService destroyed (stopped/killed)")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        else true

    // ── Notification (broadcast-on disclosure, Req 6.2) ─────────────────────────

    private fun buildNotification(): Notification {
        val S = stringsFor(loadLanguage(this))
        val channelId = createChannel(S.buddyChannelName)
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(S.buddyBroadcastOn)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel(name: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }
}
