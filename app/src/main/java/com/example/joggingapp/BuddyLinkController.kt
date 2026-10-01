package com.example.joggingapp

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Buddy Link controller / state holder. Plain class (not an AndroidX ViewModel — the
 * app has no lifecycle-viewmodel dependency and keeps state in Compose) that owns the
 * Buddy Link UI state and mediates the [BuddyRepository]. Create it in a `remember { }`
 * and call [start] from a DisposableEffect; call [dispose] on leaving composition.
 * (Named Controller to avoid clashing with the BuddyLinkState enum in BuddyModels.kt.)
 *
 * The master toggle is persisted locally in prefs so the UI reflects it instantly and
 * offline; the repository is updated when reachable. Enforces CONDITION-1 client-side
 * by deriving `sharing = buddyEnabled && gpsOn`.
 */
class BuddyLinkController(private val appContext: Context) {

    private val prefs = appContext.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
    private val repo: BuddyRepository = BuddyRepositoryProvider.get(appContext)
    private val scope = CoroutineScope(SupervisorJob())

    // ── Observable UI state (Compose mutableState) ──────────────────────────────
    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, false)); private set
    var appId by mutableStateOf<String?>(BuddyIdentity.getId(appContext)); private set
    var gpsOn by mutableStateOf(false); private set
    var backendAvailable by mutableStateOf(false); private set
    var buddies by mutableStateOf<List<BuddyView>>(emptyList()); private set
    var pending by mutableStateOf<List<BuddyLink>>(emptyList()); private set
    var blocked by mutableStateOf<List<String>>(emptyList()); private set
    var watchers by mutableStateOf<List<BuddyUser>>(emptyList()); private set
    /** Sparse breadcrumb trail for the currently displayed buddy (7-day window). */
    var buddyTrail by mutableStateOf<List<BuddyTrailPoint>>(emptyList()); private set
    /** Whether the user has opted to show the buddy's history trail on the map (Req 5b). */
    var showHistory by mutableStateOf(false); private set

    /**
     * The trail to actually draw on the map: the observed [buddyTrail] only while the
     * user has history enabled AND a buddy is live; otherwise empty so the overlay is
     * hidden. Makes the trail a user-invoked "History" view rather than always-on.
     */
    val visibleBuddyTrail: List<BuddyTrailPoint>
        get() = if (showHistory && primaryBuddyView != null) buddyTrail else emptyList()

    /** Toggle the buddy history trail overlay (task 6.4). */
    fun toggleHistory() { showHistory = !showHistory }

    /** Derived: am I currently sharing? (mutual-on gate, CONDITION-1 — my side.) */
    val sharing: Boolean get() = enabled && gpsOn

    /** True when at least one buddy can currently see me (drives the eye indicator). */
    val hasWatchers: Boolean get() = enabled && watchers.isNotEmpty()

    /**
     * The buddy to display on the home map: the first one that is currently live
     * (mutually sharing + a fix newer than 15 min). Null when no buddy is live, so
     * the map simply draws no buddy overlay. (v1 is 1:1, so at most one.)
     */
    val primaryBuddyView: BuddyView? get() = buddies.firstOrNull { it.isLiveNow() }

    private var started = false

    /** Begin observing the repository. Safe to call once per composition. */
    fun start() {
        if (started) return
        started = true
        if (!enabled) return   // nothing to observe until enabled
        beginObserving()
    }

    // Tracks the appId whose trail we're currently observing, so we only re-subscribe
    // when the displayed buddy actually changes.
    private var trailBuddyId: String? = null
    private var trailJob: Job? = null

    // Previous-snapshot baselines used to detect *transitions* between Firestore
    // emissions so we notify once per event (Req 7). Null until the first emission,
    // so the initial load never fires a notification for pre-existing state.
    // Pending is tracked as linkId -> reqCount so we can honour the "≤2 alerts / 24h"
    // cap (Req 3.8): a re-request bumps reqCount, and we alert only while within the cap.
    private var prevPendingCounts: Map<String, Int>? = null
    private var prevBuddyIds: Set<String>? = null

    private fun beginObserving() {
        repo.observeAvailability().onEach { backendAvailable = it }.launchIn(scope)
        repo.observeBuddies().onEach {
            detectBuddyTransitions(it)
            buddies = it
            refreshTrailSubscription()
        }.launchIn(scope)
        repo.observePendingRequests().onEach {
            detectPendingTransitions(it)
            pending = it
        }.launchIn(scope)
        repo.observeBlocked().onEach { blocked = it }.launchIn(scope)
        repo.observeWatchers().onEach { watchers = it }.launchIn(scope)
        // Store the FCM token for future server-side push (best-effort, non-blocking).
        scope.launch { repo.refreshFcmToken() }
    }

    /**
     * Fire a notification for an incoming link request (Req 7.1), honouring the
     * frequency rule (Req 3.8): a given pending request may raise at most
     * [BuddyConstants.REQUEST_ALERT_MAX_PER_24H] alerts within its 24h window. A new
     * request alerts; a re-request (same linkId, bumped reqCount) alerts again only
     * while still within the cap. Beyond the cap the request stays visible in the
     * pending list but no longer alerts.
     */
    private fun detectPendingTransitions(newPending: List<BuddyLink>) {
        BuddyTransitions.pendingEvents(prevPendingCounts, newPending).forEach { ev ->
            if (ev is BuddyTransitions.Event.RequestReceived) BuddyNotifier.notifyRequestReceived(appContext)
        }
        prevPendingCounts = BuddyTransitions.pendingSnapshot(newPending)
    }

    /**
     * Fire notifications when the accepted-buddy set changes (Req 7.2, 7.4):
     *  - a buddy that appeared → a request was accepted;
     *  - a buddy that vanished → they unlinked / stopped sharing.
     */
    private fun detectBuddyTransitions(newBuddies: List<BuddyView>) {
        BuddyTransitions.buddyEvents(prevBuddyIds, newBuddies, prevBuddies = buddies).forEach { ev ->
            when (ev) {
                is BuddyTransitions.Event.Accepted -> BuddyNotifier.notifyRequestAccepted(appContext, ev.name)
                is BuddyTransitions.Event.Unlinked -> BuddyNotifier.notifyUnlinked(appContext, ev.name)
                else -> {}
            }
        }
        prevBuddyIds = BuddyTransitions.buddySnapshot(newBuddies)
    }

    /**
     * (Re)subscribes to the displayed buddy's trail when the primary live buddy
     * changes. Cancels the previous subscription and clears the trail when there is
     * no live buddy.
     */
    private fun refreshTrailSubscription() {
        val id = primaryBuddyView?.user?.appId
        if (id == trailBuddyId) return
        trailBuddyId = id
        trailJob?.cancel(); trailJob = null
        buddyTrail = emptyList()
        if (id != null) {
            trailJob = repo.observeBuddyTrail(id).onEach { buddyTrail = it }.launchIn(scope)
        }
    }

    /** Report the current device GPS/location-enabled state (drives CONDITION-1). */
    fun updateGpsState(on: Boolean) {
        if (gpsOn == on) return
        gpsOn = on
        scope.launch { repo.updateMyUser(gpsOn = on, sharing = sharing) }
        applySharingState()
    }

    /**
     * Starts or stops [BuddyLocationService] so it runs exactly while CONDITION-1
     * holds (toggle ON && GPS on). Idempotent — the service reuses its instance and
     * ignores redundant starts, and a stop on an already-stopped service is harmless.
     */
    private fun applySharingState() {
        val intent = Intent(appContext, BuddyLocationService::class.java)
        if (sharing) {
            intent.action = BuddyLocationService.ACTION_START
            try { ContextCompat.startForegroundService(appContext, intent) } catch (_: Exception) {}
        } else {
            intent.action = BuddyLocationService.ACTION_STOP
            try { appContext.startService(intent) } catch (_: Exception) {}
        }
    }

    /**
     * Called AFTER the consent + permission flow has succeeded (see the UI flow, G3).
     * Provisions the App ID, opens the backend session, and flips the toggle ON.
     */
    fun enableConfirmed(displayName: String) {
        val id = BuddyIdentity.ensureId(appContext)
        appId = id
        enabled = true
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link enabled")
        scope.launch {
            repo.ensureSession(id)
            repo.updateMyUser(displayName = displayName, buddyEnabled = true, gpsOn = gpsOn, sharing = sharing)
        }
        beginObserving()
        applySharingState()
    }

    /** Turns Buddy Link OFF: stops sharing immediately (Req 1.6). */
    fun disable() {
        enabled = false
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link disabled")
        scope.launch { repo.updateMyUser(buddyEnabled = false, sharing = false) }
        // Clear observed lists so the UI doesn't show stale buddies while off.
        buddies = emptyList(); pending = emptyList(); watchers = emptyList()
        trailJob?.cancel(); trailJob = null; trailBuddyId = null; buddyTrail = emptyList()
        prevPendingCounts = null; prevBuddyIds = null
        lastOnDemandRequestAt = emptyMap()
        showHistory = false
        applySharingState()   // sharing is now false -> stops the broadcast service
    }

    // ── Actions (fire-and-forget; failures surface via backendAvailable/status) ──
    fun requestLink(targetAppId: String, onResult: (BuddyResult<Unit>) -> Unit = {}) =
        scope.launch { onResult(repo.requestLink(targetAppId)) }
    fun accept(linkId: String) = scope.launch { repo.acceptLink(linkId) }
    fun decline(linkId: String) = scope.launch { repo.declineLink(linkId) }
    fun remove(linkId: String) = scope.launch { repo.removeLink(linkId) }
    fun block(appId: String) = scope.launch { repo.blockUser(appId) }
    fun unblock(appId: String) = scope.launch { repo.unblockUser(appId) }

    // Per-buddy timestamp of the last on-demand "request location now" call, so the
    // viewer can't spam a broadcaster (client rate-limit, Req 6.1b / G6). Compose state
    // so the button can reflect the cooldown; paired with a UI tick to re-evaluate.
    var lastOnDemandRequestAt by mutableStateOf<Map<String, Long>>(emptyMap()); private set

    /** True if enough time has passed to allow another on-demand request for [buddyAppId]. */
    fun canRequestLocation(buddyAppId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        BuddyTransitions.canRequestLocation(lastOnDemandRequestAt, buddyAppId, nowMs)

    /**
     * Asks a buddy for a fresh fix now, rate-limited per buddy (Req 6.1b / G6). Returns
     * false (and does nothing) if still within the cooldown window, so callers can
     * surface a "please wait" hint instead of silently spamming the backend.
     */
    fun requestBuddyLocation(buddyAppId: String): Boolean {
        if (!canRequestLocation(buddyAppId)) return false
        lastOnDemandRequestAt = lastOnDemandRequestAt + (buddyAppId to System.currentTimeMillis())
        scope.launch { repo.requestBuddyLocation(buddyAppId) }
        return true
    }

    /** Delete all my Buddy Link data + reset identity (Req 8.4). */
    fun deleteAllData() = scope.launch {
        repo.deleteAllMyData()
        BuddyIdentity.reset(appContext)
        disable()
        appId = null
    }

    /** The invite link message to share (Req 2.4, 3.1). */
    fun buildInviteShareText(userName: String): String? {
        val id = appId ?: return null
        return BuddyInvite.buildShareText(id, userName)
    }

    fun dispose() { scope.cancel() }

    companion object {
        private const val KEY_ENABLED = "buddy_enabled"
    }
}
