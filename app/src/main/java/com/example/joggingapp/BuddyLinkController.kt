package com.example.joggingapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Buddy Link controller / state holder. Plain class (not an AndroidX ViewModel — the
 * app has no lifecycle-viewmodel dependency and keeps state in Compose) that owns the
 * Buddy Link UI state and mediates the [BuddyRepository]. Get the process-wide instance
 * with [get] and call [start] once the UI is up. It lives as long as the process, not the
 * screen: a rebuilt screen (rotation, theme change, closing and reopening) must not drop a
 * run-together invite in flight, because a received signal is deleted from the server.
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
    var deleteDataFailed by mutableStateOf(false); private set
    var backendAvailable by mutableStateOf(false); private set
    var buddies by mutableStateOf<List<BuddyView>>(emptyList()); private set
    /**
     * Compose-observable liveness tick. Holds the current wall-clock time in millis and
     * is bumped every [LIVENESS_TICK_MS] by a coroutine started in [beginObserving] and
     * cancelled in [disable]/[dispose] (tied to the same lifecycle as the Firestore
     * listeners, so it never leaks and stops when the controller stops observing).
     *
     * Because [BuddyView.isLiveNow] is a function of wall-clock time — a value Compose
     * cannot observe directly — any derived "is this buddy live?" read must touch this
     * state to be re-evaluated as time passes AND as the separate user/location
     * emissions land. Reading it inside [primaryBuddyView]/[isBuddyLive] subscribes the
     * caller, so the map and the buddy list refresh within one tick of a buddy going
     * live, with no swipe/restart. The value IS a timestamp, so it's also passed
     * straight to isLiveNow(nowMs) — reactive and correct at once.
     */
    var livenessTick by mutableStateOf(System.currentTimeMillis()); private set
    var pending by mutableStateOf<List<BuddyLink>>(emptyList()); private set
    var blocked by mutableStateOf<List<String>>(emptyList()); private set
    var watchers by mutableStateOf<List<BuddyUser>>(emptyList()); private set
    /** Sparse breadcrumb trail for the currently displayed buddy (7-day window). */
    var buddyTrail by mutableStateOf<List<BuddyTrailPoint>>(emptyList()); private set
    /** Whether the user has opted to show the buddy's history trail on the map (Req 5b). */
    var showHistory by mutableStateOf(false); private set

    /**
     * Persisted sharer location-sharing mode; PING by default (design §3). Single
     * source of truth for write-gating (the service reads the same prefs key). Persists
     * across Buddy Link enable/disable — only [deleteAllData] resets it.
     */
    var shareMode by mutableStateOf(
        BuddyShareMode.fromWire(prefs.getString(BuddyConstants.PREFS_SHARE_MODE_KEY, null))
    ); private set

    /** Raised once each time Buddy Link is enabled while mode == ROUTE, so the UI can re-alert (req 5b). */
    var routeActiveAlertPending by mutableStateOf(false); private set
    fun consumeRouteActiveAlert() { routeActiveAlertPending = false }

    /**
     * The trail to draw on the map while a buddy is live: their current route by default
     * (so a shared activity needs no extra tap), or everything loaded when the user has
     * opted into "History". Empty when no buddy is live, hiding the overlay.
     */
    val visibleBuddyTrail: List<BuddyTrailPoint>
        get() {
            val primary = primaryBuddyView ?: return emptyList()
            val since = runTogetherStartedAt
            return when {
                showHistory -> buddyTrail
                // Running together: just the part walked together, as the run promised.
                primary.user.appId == runPartnerId && since > 0L -> runPartnerPointsSince(since)
                else -> BuddyLiveRoute.currentSegment(buddyTrail, livenessTick)
            }
        }

    /** Toggle the buddy history trail overlay (task 6.4). */
    fun toggleHistory() { showHistory = !showHistory }

    /** Switch to ROUTE (called only AFTER the consent dialog is confirmed, §6). */
    fun enableRouteMode() { applyShareMode(BuddyShareMode.ROUTE) }

    /** Switch back to PING. Stops NEW trail writes; does NOT delete already-written points (§4). */
    fun disableRouteMode() { applyShareMode(BuddyShareMode.PING) }

    // Renamed from setShareMode to avoid a JVM signature clash with the auto-generated
    // setter for `var shareMode` (both would be setShareMode(BuddyShareMode)).
    private fun applyShareMode(mode: BuddyShareMode) {
        if (shareMode == mode) return
        shareMode = mode
        // Any explicit change makes the mode permanent, so it clears the this-run-only marker.
        prefs.edit().putString(BuddyConstants.PREFS_SHARE_MODE_KEY, mode.wire)
            .remove(BuddyConstants.PREFS_RUN_ROUTE_TEMPORARY).apply()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link share mode -> ${mode.wire}")
        scope.launch { repo.updateMyUser(shareMode = mode) }
    }

    /** Derived: am I currently sharing? (mutual-on gate, CONDITION-1 — my side.) */
    val sharing: Boolean get() = enabled && gpsOn

    /** True when at least one buddy can currently see me (drives the eye indicator). */
    val hasWatchers: Boolean get() = sharing && watchers.isNotEmpty()

    /**
     * Accepted buddies to show in the active list — those I have NOT blocked. Derived
     * reactively from the Compose-observable [buddies] + [blocked] via the pure
     * [BuddyListFilter], so blocking/unblocking moves a buddy between lists within one
     * recomposition. The raw [buddies] (and the transition baseline) stay unfiltered so
     * block fires no false "unlinked" notification.
     */
    val activeBuddies: List<BuddyView>
        get() = BuddyListFilter.activeBuddies(buddies, blocked.toSet())

    /** Accepted buddies I HAVE blocked (link preserved), rendered in the Blocked group with their name. */
    val blockedBuddies: List<BuddyView>
        get() = BuddyListFilter.blockedBuddies(buddies, blocked.toSet())

    /** Legacy/torn-down blocks with no surviving accepted link (pre-reversible-block data); rendered as short ids. */
    val blockedOnlyIds: List<String>
        get() = blocked.filter { id -> buddies.none { it.user.appId == id } }

    /**
     * The buddy to display on the home map: the first one that is currently live
     * (mutually sharing + a fix newer than 15 min). Null when no buddy is live, so
     * the map simply draws no buddy overlay. (v1 is 1:1, so at most one.)
     */
    val primaryBuddyView: BuddyView? get() {
        val live = buddies.filter { it.isLiveNow(livenessTick) }
        // The buddy I'm running with wins over whoever happens to be listed first.
        return live.firstOrNull { it.user.appId == runPartnerId } ?: live.firstOrNull()
    }

    /** Buddies I could invite to a run right now: live and not blocked. */
    val liveBuddies: List<BuddyView>
        get() = activeBuddies.filter { it.isLiveNow(livenessTick) }

    /**
     * Tick-driven liveness check for the buddy list rows. Reads [livenessTick] so the
     * calling composable re-evaluates on every tick (and on each buddies emission),
     * then checks liveness at that instant. The tick IS a current-millis snapshot, so
     * passing it as nowMs to [BuddyView.isLiveNow] is both reactive and correct.
     */
    fun isBuddyLive(view: BuddyView): Boolean = view.isLiveNow(livenessTick)

    // Separate from [observing]: [started] guards the one-time launch entry so the
    // DisposableEffect only kicks the session once, while [observing] guards whether
    // listeners are currently attached (so enable/re-enable can re-attach cleanly).
    private var started = false
    private var observing = false

    /** Begin observing the repository on app launch. Safe to call once per composition. */
    fun start() {
        if (started) return
        started = true
        observeRunRouteLifetime()
        if (!enabled) return   // nothing to observe until enabled
        ensureSession()
        refreshSharingOnLaunch()   // FR6: assert sharing on launch (belt-and-suspenders)
    }

    /**
     * Belt-and-suspenders launch-time sharing assertion (FR6). On a fresh process launch
     * with Buddy Link on, assert `sharing` to the backend and (re)apply the broadcast
     * service state immediately, rather than waiting for the foreground GPS poll's first
     * tick. Mirrors the MainActivity poll's gate (`providerOn && hasFine()`) using the
     * controller's own [appContext]. Idempotent: [updateGpsState] early-returns when the
     * value is unchanged, so this does not double-start the service with the poll that
     * follows a few seconds later.
     *
     * The dominant staleness case — a fully killed/backgrounded app with no open at all —
     * is explicitly NOT solved here (needs BOOT_COMPLETED/WorkManager/FCM) and remains
     * out of scope; the tap-to-locate reason strings keep that case honest.
     */
    private fun refreshSharingOnLaunch() {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE)
            as? android.location.LocationManager
        val providerOn = try {
            lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        } catch (_: Exception) {
            false
        }
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        updateGpsState(providerOn && fine)
    }

    /**
     * Single idempotent entry point that establishes the backend session (with bounded
     * retry) and attaches the Firestore listeners. Shared by [start] (launch) and
     * [enableConfirmed] (consent completion) so enabling Buddy Link begins the live
     * session IMMEDIATELY — no app restart required. Safe to call repeatedly:
     * [beginObserving] early-returns while already observing.
     */
    private fun ensureSession(displayName: String? = null) {
        // V1.58: provisioning is now UNCONDITIONAL. Whenever Buddy Link is enabled we
        // ALWAYS run ensureSessionWithRetry(id) BEFORE attaching listeners — even when a
        // cached anon session already exists (needsSession()==false). ensureSession
        // reuses the existing anon session (no forced new sign-in, no loop) and only
        // re-runs the idempotent subdoc set-merge, so a returning / pre-V1.52 device
        // backfills users/{appId}/private/self before any owner-gated buddy read/write —
        // the migration hole that caused PERMISSION_DENIED. Off the main thread on scope.
        // NoOp backend returns an inert Failure and shouldRetry stops the loop.
        val id = appId
        if (id != null) {
            scope.launch {
                // Publish my state BEFORE attaching listeners. The rules deny location reads
                // until my `sharing` is true, and the earlier write in updateGpsState can
                // fail if it beats the session. shareMode gates the buddy's trail read.
                if (ensureSessionWithRetry(id) && enabled) {
                    repo.updateMyUser(
                        displayName = displayName, buddyEnabled = true, gpsOn = gpsOn,
                        sharing = sharing, shareMode = shareMode
                    )
                    pruneOldTrail()
                }
                if (enabled) beginObserving()
            }
        } else {
            beginObserving()
        }
    }

    /** Enforces the retention promise here: the Firestore TTL policy isn't relied on. */
    private fun pruneOldTrail() {
        scope.launch {
            val cutoff = System.currentTimeMillis() - BuddyConstants.RETENTION_MS
            when (val r = repo.pruneMyTrail(cutoff)) {
                is BuddyResult.Success -> if (r.value > 0) {
                    AppLogger.log(appContext, LogCategory.PROFILE, "Buddy trail pruned: ${r.value} old points")
                }
                is BuddyResult.Failure ->
                    AppLogger.log(appContext, LogCategory.ERROR, "Buddy trail prune failed: ${r.reason}")
            }
        }
    }

    /**
     * Calls [BuddyRepository.ensureSession] up to [BuddySessionRetry.MAX_ATTEMPTS] times
     * with a growing delay. Suspends (never blocks the UI). Returns true on success.
     */
    private suspend fun ensureSessionWithRetry(id: String): Boolean {
        var attempt = 1
        while (true) {
            val ok = repo.ensureSession(id) is BuddyResult.Success
            if (ok) {
                AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link session established (attempt $attempt)")
                return true
            }
            AppLogger.log(appContext, LogCategory.ERROR, "Buddy Link session attempt $attempt failed")
            if (!BuddySessionRetry.shouldRetry(attempt, ok, repo.needsSession())) return false
            delay(BuddySessionRetry.delayAfterAttemptMs(attempt))
            attempt++
        }
    }

    // Tracks the appId whose trail we're currently observing, so we only re-subscribe
    // when the observable buddy actually changes.
    private var trailBuddyId: String? = null
    private var trailJob: Job? = null

    // Previous-snapshot baselines used to detect *transitions* between Firestore
    // emissions so we notify once per event (Req 7). Null until the first emission,
    // so the initial load never fires a notification for pre-existing state.
    // Pending is tracked as linkId -> reqCount so we can honour the "≤2 alerts / 24h"
    // cap (Req 3.8): a re-request bumps reqCount, and we alert only while within the cap.
    private var prevPendingCounts: Map<String, Int>? = null
    private var prevBuddyIds: Set<String>? = null
    // Baseline of currently-online (live) buddy appIds, used to fire a one-shot
    // "buddy came online" notification only on a genuine offline->online transition.
    // Null until the first emission so the initial snapshot never alerts.
    private var prevOnlineIds: Set<String>? = null

    // Jobs for the attached listeners, so [disable] can cancel them and a later
    // re-enable re-attaches a fresh set rather than doubling up.
    private val observeJobs = ArrayList<Job>()

    // The liveness-tick coroutine. Started alongside the listeners in [beginObserving]
    // and cancelled in [disable]/[dispose] so it shares the listeners' lifecycle and
    // never outlives the observing session. Guarded (null) so a re-observe doesn't
    // start a second ticker.
    private var livenessTickJob: Job? = null

    private fun beginObserving() {
        if (observing) return   // already attached — avoid duplicate listeners/diffs
        observing = true
        observeJobs += repo.observeAvailability().onEach { backendAvailable = it }.launchIn(scope)
        observeJobs += repo.observeBuddies().onEach {
            detectBuddyTransitions(it)
            buddies = it
            refreshTrailSubscription()
        }.launchIn(scope)
        observeJobs += repo.observePendingRequests().onEach {
            detectPendingTransitions(it)
            pending = it
        }.launchIn(scope)
        observeJobs += repo.observeBlocked().onEach { blocked = it }.launchIn(scope)
        observeJobs += repo.observeWatchers().onEach { watchers = it }.launchIn(scope)
        // Live Cheer (S1): surface an incoming cheer in-app while foregrounded.
        observeJobs += repo.observeCheers().onEach { handleIncomingCheers(it) }.launchIn(scope)
        observeJobs += repo.observeRunSignals().onEach { handleRunSignals(it) }.launchIn(scope)
        // Liveness ticker: bump the Compose-observable tick every few seconds so the
        // time-based isLiveNow() reads in primaryBuddyView/isBuddyLive re-evaluate as
        // time passes (and after later partial emissions), making the map + buddy list
        // refresh reactively. Tied to this observing session's lifecycle (cancelled in
        // disable()/dispose()) so it can't leak. Only re-reads in-memory state — not a
        // network poll; the Firestore listeners above remain push-based.
        if (livenessTickJob == null) {
            livenessTickJob = scope.launch {
                while (true) {
                    delay(LIVENESS_TICK_MS)
                    livenessTick = System.currentTimeMillis()
                }
            }
        }
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
            if (ev is BuddyTransitions.Event.RequestReceived) {
                BuddyNotifier.notifyRequestReceived(
                    appContext, linkId = ev.link.linkId, fromName = ev.link.fromName
                )
            }
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
        // A buddy turning THEIR sharing on -> a local "buddy online" notification, but
        // only on a genuine offline->online transition after the baseline (prevOnlineIds
        // null = first emission = silent baseline). The carried-forward snapshot dedupes
        // so a buddy staying online across emissions alerts exactly once.
        BuddyTransitions.onlineEvents(prevOnlineIds, newBuddies).forEach { ev ->
            if (ev is BuddyTransitions.Event.CameOnline) BuddyNotifier.notifyBuddyOnline(appContext, ev.name, ev.appId)
        }
        prevOnlineIds = BuddyTransitions.onlineSnapshot(newBuddies)
    }

    /**
     * (Re)subscribes to the primary live buddy's trail. The rules only allow that read in
     * ROUTE mode, so a buddy switching to ROUTE subscribes here and switching back clears
     * the trail. Cancels the previous subscription when there is no such buddy.
     */
    private fun refreshTrailSubscription() {
        val id = primaryBuddyView?.user?.takeIf { it.shareMode == BuddyShareMode.ROUTE }?.appId
        if (id == trailBuddyId) return
        trailBuddyId = id
        trailJob?.cancel(); trailJob = null
        buddyTrail = emptyList()
        if (id != null) {
            trailJob = repo.observeBuddyTrail(id).onEach { pts ->
                buddyTrail = pts
                // Kept apart from buddyTrail, which empties when the partner ends first and
                // switches Route back off: their part of the run must survive until I save.
                if (id == runPartnerId) {
                    runPartnerTrail = (runPartnerTrail + pts).distinctBy { it.at }.sortedBy { it.at }
                }
            }.launchIn(scope)
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
            AppLogger.log(appContext, LogCategory.SERVICE, "Buddy sharing ON — starting BuddyLocationService")
            try { ContextCompat.startForegroundService(appContext, intent) } catch (_: Exception) {}
        } else {
            intent.action = BuddyLocationService.ACTION_STOP
            AppLogger.log(appContext, LogCategory.SERVICE, "Buddy sharing OFF — stopping BuddyLocationService")
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
        deleteDataFailed = false
        enabled = true
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link enabled")
        // Re-alert on EVERY enable while ROUTE is the persisted mode (req 5b): raise a
        // one-shot flag the UI consumes. Set here (not a LaunchedEffect) so it fires once
        // per explicit enable, not on every recomposition.
        if (shareMode == BuddyShareMode.ROUTE) routeActiveAlertPending = true
        // Starts the session through the same path as app launch, so listeners and sharing come
        // up without a restart. The profile, including the name, is written once it succeeds:
        // the rules refuse a brand-new identity's profile before then.
        ensureSession(displayName)
        applySharingState()   // start the location foreground service when CONDITION-1 holds
    }

    /** Turns Buddy Link OFF: stops sharing immediately (Req 1.6). */
    fun disable() {
        restoreRunRoute()
        scope.launch { repo.updateMyUser(buddyEnabled = false, sharing = false) }
        stopLocally()
    }

    /** Stops sharing and clears local state, without writing anything to the backend. */
    private fun stopLocally() {
        enabled = false
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link disabled")
        // Cancel the attached listeners and reset the observing flag so a later
        // re-enable re-attaches a fresh set (no duplicate listeners / doubled diffs).
        observeJobs.forEach { it.cancel() }; observeJobs.clear(); observing = false
        livenessTickJob?.cancel(); livenessTickJob = null
        // Clear observed lists so the UI doesn't show stale buddies while off.
        buddies = emptyList(); pending = emptyList(); watchers = emptyList()
        trailJob?.cancel(); trailJob = null; trailBuddyId = null; buddyTrail = emptyList()
        prevPendingCounts = null; prevBuddyIds = null; prevOnlineIds = null
        lastOnDemandRequestAt = emptyMap()
        // Live Cheer (S1) state reset alongside the rest.
        lastCheerSentAt = emptyMap(); cheersSentToday.clear(); cheerDayStart = 0L
        incomingCheerFromName = null; seenCheerIds = null
        resetRunTogether()
        showHistory = false
        applySharingState()   // sharing is now false -> stops the broadcast service
    }

    // ── Actions (fire-and-forget; failures surface via backendAvailable/status) ──
    fun requestLink(
        targetAppId: String,
        fromName: String? = null,
        onResult: (BuddyResult<Unit>) -> Unit = {}
    ) = scope.launch { onResult(repo.requestLink(targetAppId, fromName)) }
    /**
     * Accepts an opened invite from [fromAppId]: accepts the inviter's pending request
     * if one exists (CONNECTED), otherwise sends a request back (REQUEST_SENT).
     */
    fun acceptInvite(
        fromAppId: String,
        fromName: String? = null,
        onResult: (BuddyResult<InviteOutcome>) -> Unit = {}
    ) = scope.launch { onResult(repo.acceptInvite(fromAppId, fromName)) }
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

    // ── Live Cheer (S1) ─────────────────────────────────────────────────────────

    /**
     * The sender name of the most recent incoming cheer to surface in-app (transient),
     * or null when there's nothing to show. Holds a NAME (no localized text) — the UI
     * formats `S.buddyCheerReceived(name)`, keeping string lookup in the UI layer like
     * `locateMsg`. Set only while foregrounded; the FCM push path covers background.
     */
    var incomingCheerFromName by mutableStateOf<String?>(null); private set
    /** Clears the transient incoming-cheer line after the UI has shown it. */
    fun consumeCheer() { incomingCheerFromName = null }

    // Per-buddy timestamp of the last cheer SENT, feeding the pure CheerRateLimit
    // cooldown. Compose state so the UI can reflect a rate-limited hint.
    var lastCheerSentAt by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    // Per-buddy count of cheers sent in the current day window, feeding the daily cap.
    private val cheersSentToday = mutableMapOf<String, Int>()
    // Epoch-ms start of the current cheer day window; a rollover resets the counts.
    private var cheerDayStart = 0L
    // Baseline of already-seen cheer inbox ids (null = first emission = silent baseline,
    // like prevOnlineIds) so only genuinely new cheers raise an in-app alert.
    private var seenCheerIds: Set<String>? = null

    /**
     * Handle an [observeCheers] emission. On the first emission just set the baseline
     * (silent — a batch already present on attach must not alert). On later emissions,
     * for each genuinely new id, surface the sender name in-app ONLY while foregrounded
     * (the FCM push path presents it when backgrounded/killed, matching the
     * [BuddyFirebaseMessagingService] de-dupe contract).
     */
    private fun handleIncomingCheers(list: List<BuddyCheer>) {
        val ids = list.map { it.id }.toSet()
        val baseline = seenCheerIds
        if (baseline == null) { seenCheerIds = ids; return }
        if (AppForeground.isForeground) {
            list.lastOrNull { it.id !in baseline }?.let { cheer ->
                incomingCheerFromName = cheer.fromName.ifBlank { cheer.fromAppId }
            }
        }
        seenCheerIds = baseline + ids
    }

    /** Reset the per-day cheer counts when the rolling day window has advanced. */
    private fun rollCheerDayIfNeeded(now: Long) {
        if (now - cheerDayStart >= BuddyConstants.CHEER_RETENTION_MS) {
            cheerDayStart = now
            cheersSentToday.clear()
        }
    }

    /**
     * Send a Live Cheer to [buddyAppId] (display name [fromName]), rate-limited per
     * buddy. Returns the pure [CheerRateLimit.Decision] so the UI can show a hint; the
     * actual backend write happens fire-and-forget only when the decision is allowed
     * (same low-stakes pattern as [requestBuddyLocation]).
     */
    fun sendCheer(buddyAppId: String, fromName: String): CheerRateLimit.Decision {
        val now = System.currentTimeMillis()
        rollCheerDayIfNeeded(now)
        val decision = CheerRateLimit.decide(lastCheerSentAt, cheersSentToday, buddyAppId, now)
        if (decision.allowed) {
            lastCheerSentAt = lastCheerSentAt + (buddyAppId to now)
            cheersSentToday[buddyAppId] = (cheersSentToday[buddyAppId] ?: 0) + 1
            scope.launch { repo.sendCheer(buddyAppId, fromName) }
        }
        return decision
    }

    // ── Run together ────────────────────────────────────────────────────────────

    /** The invite I sent and am waiting on, or null. */
    var outgoingRun by mutableStateOf<OutgoingRunInvite?>(null); private set
    /** The invite waiting for my answer, or null. */
    var incomingRun by mutableStateOf<RunSignal?>(null); private set
    /** Set when both sides agreed; the UI starts tracking, then calls [consumeRunStart]. */
    var runStart by mutableStateOf<RunStart?>(null); private set
    var runNotice by mutableStateOf<RunNotice?>(null); private set
    fun consumeRunStart() { runStart = null }
    fun consumeRunNotice() { runNotice = null }

    private var outgoingTimeout: Job? = null
    private var incomingTimeout: Job? = null
    private var seenRunSignalIds = emptySet<String>()
    /** The buddy I agreed to run with, from "both agreed" until that run ends. */
    var runPartnerId by mutableStateOf<String?>(null); private set
    var runPartnerName by mutableStateOf<String?>(null); private set
    /** When the joint run started tracking here; 0 before it starts. */
    var runTogetherStartedAt by mutableStateOf(0L); private set
    /** A buddy picked to invite (home card or Buddy Link); the UI shows the send dialog. */
    var runInviteTarget by mutableStateOf<BuddyView?>(null); private set
    fun askToRun(view: BuddyView) { if (outgoingRun == null) runInviteTarget = view }
    fun clearRunInviteTarget() { runInviteTarget = null }

    /** The partner's trail points from [sinceMs] on, oldest first. */
    fun runPartnerPointsSince(sinceMs: Long): List<BuddyTrailPoint> =
        if (runPartnerId != null) runPartnerTrail.filter { it.at >= sinceMs } else emptyList()

    /** Every trail point of the partner seen during this pairing, oldest first. */
    private var runPartnerTrail by mutableStateOf<List<BuddyTrailPoint>>(emptyList())

    /**
     * The partner's route for the activity being saved, with their name, or null when
     * this wasn't a run together or nothing of theirs arrived (V1.71: kept in history).
     */
    fun runPartnerRouteForSave(): Pair<String, List<Point>>? {
        val since = runTogetherStartedAt
        if (runPartnerId == null || since <= 0L) return null
        val pts = runPartnerPointsSince(since - RUN_PARTNER_SAVE_SLACK_MS)
        runLog("saving partner route — ${pts.size} points")
        if (pts.size < 2) return null
        return (runPartnerName ?: "") to pts.map { Point(it.lat, it.lon, it.at, 0f) }
    }

    /** Ends the run-together pairing (run over, or the run never started). */
    fun endRunTogether() {
        if (runPartnerId == null) return
        runLog("run together over")
        runPartnerId = null; runPartnerName = null; runTogetherStartedAt = 0L
        runPartnerTrail = emptyList()
        refreshTrailSubscription()
    }

    // The run hasn't started yet: its first tracking state turns Route on, until this time.
    private var runRouteDeadline = 0L

    /** True while a joint run is starting, so the UI doesn't also ask for Route consent. */
    val runRoutePending: Boolean get() = System.currentTimeMillis() < runRouteDeadline

    fun inviteToRun(buddy: BuddyView, activityType: Int, fromName: String) {
        if (outgoingRun != null) return
        val invite = OutgoingRunInvite(
            java.util.UUID.randomUUID().toString(), buddy.user.appId,
            buddy.user.displayName.ifBlank { buddyShortId(buddy.user.appId) }, activityType
        )
        outgoingRun = invite
        runNotice = null
        runLog("invite created for ${buddyShortId(invite.buddyAppId)} — type=$activityType invite=${invite.inviteId.take(8)}")
        outgoingTimeout = scope.launch {
            delay(BuddyConstants.RUN_INVITE_TTL_MS)
            runLog("invite ${invite.inviteId.take(8)} got no answer — cancelling")
            outgoingRun = null
            runNotice = RunNotice(RunNotice.Kind.NO_ANSWER, invite.buddyName)
            sendRunSignal(invite.buddyAppId, RunSignalKind.CANCEL, invite.inviteId, activityType)
        }
        scope.launch {
            // Offline the write only queues, and arrives too late to be shown: report it now.
            val result = withTimeoutOrNull(BuddyConstants.RUN_SIGNAL_SEND_TIMEOUT_MS) {
                repo.sendRunSignal(invite.buddyAppId, RunSignalKind.INVITE, invite.inviteId, activityType, fromName)
            }
            if (result is BuddyResult.Success) runLog("invite ${invite.inviteId.take(8)} sent")
            if (result !is BuddyResult.Success && outgoingRun?.inviteId == invite.inviteId) {
                val why = (result as? BuddyResult.Failure)?.reason ?: "timed out"
                AppLogger.log(appContext, LogCategory.ERROR, "Run-together invite not sent: $why")
                closeOutgoing()
                runNotice = RunNotice(RunNotice.Kind.SEND_FAILED, invite.buddyName)
            }
        }
    }

    fun cancelRunInvite() {
        val invite = outgoingRun ?: return
        runLog("invite ${invite.inviteId.take(8)} cancelled by me")
        closeOutgoing()
        sendRunSignal(invite.buddyAppId, RunSignalKind.CANCEL, invite.inviteId, invite.activityType)
    }

    fun acceptRunInvite() {
        val invite = incomingRun ?: return
        runLog("invite ${invite.inviteId.take(8)} accepted by me")
        closeIncoming()
        sendRunSignal(invite.fromAppId, RunSignalKind.ACCEPT, invite.inviteId, invite.activityType)
        beginRunTogether(invite.activityType, invite.fromAppId, invite.fromName)
    }

    fun declineRunInvite() {
        val invite = incomingRun ?: return
        runLog("invite ${invite.inviteId.take(8)} declined by me")
        closeIncoming()
        sendRunSignal(invite.fromAppId, RunSignalKind.DECLINE, invite.inviteId, invite.activityType)
    }

    private fun handleRunSignals(signals: List<RunSignal>) {
        val now = System.currentTimeMillis()
        signals.filter { it.id !in seenRunSignalIds }.forEach { signal ->
            seenRunSignalIds = seenRunSignalIds + signal.id
            val outgoing = outgoingRun
            val action = RunTogether.decide(signal, outgoing, incomingRun, now)
            runLog("received ${signal.kind.wire} from ${buddyShortId(signal.fromAppId)} invite=${signal.inviteId.take(8)} " +
                "age=${(now - signal.at) / 1000}s waitingOnMine=${outgoing != null} → $action")
            when (action) {
                RunTogether.Action.SHOW_INVITE -> showIncomingInvite(signal)
                RunTogether.Action.ACCEPT_CROSSED_INVITE -> {
                    closeOutgoing()
                    sendRunSignal(signal.fromAppId, RunSignalKind.ACCEPT, signal.inviteId, signal.activityType)
                    beginRunTogether(outgoing?.activityType ?: signal.activityType, signal.fromAppId, signal.fromName)
                }
                RunTogether.Action.OUTGOING_ACCEPTED -> {
                    closeOutgoing()
                    if (outgoing != null) {
                        if (!AppForeground.isForeground) BuddyNotifier.notifyRunAccepted(appContext, outgoing.buddyName)
                        beginRunTogether(outgoing.activityType, outgoing.buddyAppId, outgoing.buddyName)
                    }
                }
                RunTogether.Action.OUTGOING_DECLINED -> {
                    closeOutgoing()
                    if (outgoing != null) runNotice = RunNotice(RunNotice.Kind.DECLINED, outgoing.buddyName)
                }
                RunTogether.Action.INCOMING_CANCELLED -> closeIncoming()
                RunTogether.Action.IGNORE -> Unit
            }
        }
    }

    private fun showIncomingInvite(signal: RunSignal) {
        closeIncoming()
        incomingRun = signal
        val name = signal.fromName.ifBlank { buddyShortId(signal.fromAppId) }
        val background = !AppForeground.isForeground
        if (background) BuddyNotifier.notifyRunInvite(appContext, name, signal.activityType)
        runLog("invite ${signal.inviteId.take(8)} shown " + if (background) "as a notification" else "in the app")
        incomingTimeout = scope.launch {
            delay(BuddyConstants.RUN_INVITE_TTL_MS)
            if (incomingRun?.id == signal.id) {
                runLog("invite ${signal.inviteId.take(8)} expired unanswered")
                incomingRun = null
            }
        }
    }

    private fun closeOutgoing() {
        outgoingTimeout?.cancel(); outgoingTimeout = null
        outgoingRun = null
    }

    private fun closeIncoming() {
        incomingTimeout?.cancel(); incomingTimeout = null
        incomingRun = null
    }

    private fun sendRunSignal(buddyAppId: String, kind: RunSignalKind, inviteId: String, activityType: Int) {
        scope.launch {
            val result = repo.sendRunSignal(buddyAppId, kind, inviteId, activityType, null)
            if (result is BuddyResult.Success) runLog("${kind.wire} sent for invite ${inviteId.take(8)}")
            if (result is BuddyResult.Failure) {
                AppLogger.log(appContext, LogCategory.ERROR, "Run-together ${kind.wire} not sent: ${result.reason}")
            }
        }
    }

    private fun beginRunTogether(activityType: Int, partnerId: String, partnerName: String) {
        val now = System.currentTimeMillis()
        runPartnerId = partnerId
        runPartnerName = partnerName.ifBlank { buddyShortId(partnerId) }
        runTogetherStartedAt = 0L
        runPartnerTrail = emptyList()
        refreshTrailSubscription()
        if (LiveActivityBus.state.value == LiveActivityState.IDLE) {
            runLog("both agreed — asking the app to start tracking (type=$activityType)")
            runRouteDeadline = now + 2 * BuddyConstants.RUN_INVITE_TTL_MS
            runStart = RunStart(activityType, now)
        } else {
            runLog("both agreed — already tracking, sharing Route for this run")
            runTogetherStartedAt = now
            turnOnRunRoute()   // already tracking: nothing to start, just share the route
        }
    }

    /** Route for this run only: the person's agreement to the invite covers just this run. */
    private fun turnOnRunRoute() {
        runRouteDeadline = 0L
        if (shareMode != BuddyShareMode.PING) {
            runLog("Route already on — nothing to turn on for this run")
            return
        }
        enableRouteMode()
        prefs.edit().putBoolean(BuddyConstants.PREFS_RUN_ROUTE_TEMPORARY, true).apply()
        runLog("Route turned on for this run only")
    }

    private fun restoreRunRoute() {
        if (prefs.getBoolean(BuddyConstants.PREFS_RUN_ROUTE_TEMPORARY, false)) {
            disableRouteMode()
            runLog("run over — Route turned back off")
        }
    }

    private fun runLog(msg: String) = AppLogger.log(appContext, LogCategory.PROFILE, "Run together: $msg")

    /**
     * Follows the tracking service, the source of truth for when a run starts and ends. The
     * first value also undoes a this-run-only Route left behind if the app died mid-run.
     */
    private fun observeRunRouteLifetime() {
        var wasActive = false
        LiveActivityBus.state.onEach { state ->
            if (state == LiveActivityState.IDLE) {
                restoreRunRoute()
                // Only a run that actually ran ends the pairing; IDLE also shows before it starts.
                if (wasActive) endRunTogether()
                wasActive = false
            } else {
                wasActive = true
                if (runRoutePending) turnOnRunRoute()
                if (runPartnerId != null && runTogetherStartedAt == 0L) {
                    runTogetherStartedAt = System.currentTimeMillis()
                    runLog("run started with ${buddyShortId(runPartnerId!!)}")
                }
            }
        }.launchIn(scope)
    }

    private fun resetRunTogether() {
        closeOutgoing(); closeIncoming()
        runStart = null; runNotice = null
        runRouteDeadline = 0L
        runPartnerId = null; runPartnerName = null; runTogetherStartedAt = 0L; runInviteTarget = null
        runPartnerTrail = emptyList()
        seenRunSignalIds = emptySet()
    }

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

    // ── Live-tracking share link for non-app users (S2) ──────────────────────────

    /**
     * The currently live public share link (S2), or null when none is active. Restored
     * on init from the prefs the Firebase impl persists (token + verbatim expiry): null
     * unless a token is present AND it has not already expired (so a stale prefs entry
     * from a previous run never shows a dead share as live).
     */
    var activeShare by mutableStateOf<ShareLink?>(restoreActiveShare()); private set

    private fun restoreActiveShare(): ShareLink? {
        val token = prefs.getString(BuddyConstants.PREFS_ACTIVE_SHARE_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(BuddyConstants.PREFS_ACTIVE_SHARE_EXPIRES_AT, 0L)
        if (System.currentTimeMillis() >= expiresAt) return null
        return ShareLink(token, expiresAt)
    }

    /**
     * Create a public share link (S2). If a still-valid [activeShare] already exists it
     * is returned without minting a second (one-active-share UX invariant). Otherwise a
     * new link is created fire-and-forget; on success [activeShare] is set and the
     * broadcast service is (re)asserted via [applySharingState] so the active share doc
     * starts receiving fixes while CONDITION-1 holds — no new permission flow. [label]
     * is trimmed and capped to 40 chars (the repository caps again defensively).
     */
    fun createShareLink(label: String?, onResult: (BuddyResult<ShareLink>) -> Unit = {}) {
        val existing = activeShare
        if (existing != null && System.currentTimeMillis() < existing.expiresAtMs) {
            onResult(BuddyResult.Success(existing)); return
        }
        scope.launch {
            val r = repo.createShareLink(label?.trim()?.take(40))
            if (r is BuddyResult.Success) {
                activeShare = r.value
                applySharingState()
            }
            onResult(r)
        }
    }

    /**
     * Revoke the active public share link (S2). No-op when nothing is active. Clears
     * [activeShare] ONLY on success, so a failed revoke keeps the card showing the live
     * share rather than falsely claiming it stopped.
     */
    fun revokeShareLink(onResult: (BuddyResult<Unit>) -> Unit = {}) {
        val t = activeShare?.token ?: return
        scope.launch {
            val r = repo.revokeShareLink(t)
            if (r is BuddyResult.Success) activeShare = null
            onResult(r)
        }
    }

    /** The public viewer URL for a share [token] (reuses the invite host; S2). */
    fun shareUrl(token: String): String =
        "https://" + BuddyConstants.INVITE_HTTPS_HOST + "/" + BuddyConstants.SHARE_PATH_PREFIX + "?t=" + token

    /**
     * Delete all my Buddy Link data + reset identity (Req 8.4). The identity is the only way
     * back to the data, so it's kept on failure and the user can turn Buddy Link on and retry.
     */
    fun deleteAllData() = scope.launch {
        deleteDataFailed = false
        val id = appId
        // A this-run-only Route ends here too, also without a write (see below).
        if (prefs.getBoolean(BuddyConstants.PREFS_RUN_ROUTE_TEMPORARY, false)) {
            shareMode = BuddyShareMode.PING
            prefs.edit().putString(BuddyConstants.PREFS_SHARE_MODE_KEY, BuddyShareMode.PING.wire)
                .remove(BuddyConstants.PREFS_RUN_ROUTE_TEMPORARY).apply()
        }
        // Stop first without a profile write, which would recreate the doc being deleted.
        stopLocally()
        val result = withTimeoutOrNull(DELETE_TIMEOUT_MS) {
            // Restores a session if needed, and ownership if an earlier attempt got partway.
            if (id != null) repo.ensureSession(id)
            activeShare?.token?.let { repo.revokeShareLink(it) }
            repo.deleteAllMyData()
        }
        if (result !is BuddyResult.Success) {
            val why = (result as? BuddyResult.Failure)?.reason ?: "timed out"
            AppLogger.log(appContext, LogCategory.ERROR, "Buddy Link data delete failed: $why")
            deleteDataFailed = true
            return@launch
        }
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link data deleted")
        activeShare = null
        BuddyIdentity.reset(appContext)
        // Fresh identity -> fresh account state: reset the share mode to the default
        // (design §3). This is the ONE place the mode resets; disable() must not.
        prefs.edit().remove(BuddyConstants.PREFS_SHARE_MODE_KEY).apply()
        shareMode = BuddyShareMode.DEFAULT
        appId = null
    }

    /** The invite link message to share (Req 2.4, 3.1). */
    fun buildInviteShareText(
        userName: String,
        messageBuilder: ((name: String, appLink: String, httpsLink: String) -> String)? = null
    ): String? {
        val id = appId ?: return null
        return BuddyInvite.buildShareText(id, userName, messageBuilder)
    }

    fun dispose() { livenessTickJob?.cancel(); livenessTickJob = null; scope.cancel() }

    companion object {
        private const val KEY_ENABLED = "buddy_enabled"
        // Interval for the Compose-observable liveness tick. 3s keeps the perceived
        // latency for a buddy appearing low while costing only in-memory re-reads.
        private const val LIVENESS_TICK_MS = 3000L
        private const val DELETE_TIMEOUT_MS = 60_000L
        // A partner fix written just before my tracking started still belongs to the run.
        private const val RUN_PARTNER_SAVE_SLACK_MS = 60_000L

        @Volatile private var instance: BuddyLinkController? = null

        /** The one controller for this process (V1.71: survives screen rebuilds). */
        fun get(context: Context): BuddyLinkController =
            instance ?: synchronized(this) {
                instance ?: BuddyLinkController(context.applicationContext).also { instance = it }
            }
    }
}
