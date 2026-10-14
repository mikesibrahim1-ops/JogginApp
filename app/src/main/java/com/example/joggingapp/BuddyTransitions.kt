package com.example.joggingapp

/**
 * Pure, side-effect-free decision logic for Buddy Link notification transitions and the
 * on-demand request cooldown. Extracted from [BuddyLinkController] so the tricky rules
 * (new vs. re-request within the alert cap, buddy appeared/vanished, request rate-limit)
 * can be unit-tested on the JVM without Android, Firebase, or a NotificationManager.
 *
 * The controller owns the state (previous snapshots) and the effects (calling
 * [BuddyNotifier]); this object only decides WHAT events should fire given old + new
 * state. See Req 3.8 (≤2 alerts/24h), Req 7 (link-event notifications), Req 6.1b (G6).
 */
object BuddyTransitions {

    /** A social event the controller should surface as a notification. */
    sealed class Event {
        /** An incoming link request (new, or a re-request within the alert cap). Carries
         *  the triggering link so the controller can pass linkId/fromName to the notifier. */
        data class RequestReceived(val link: BuddyLink) : Event()
        /** A buddy became accepted (appeared in the accepted set). name may be a short id. */
        data class Accepted(val name: String) : Event()
        /** A buddy unlinked / vanished from the accepted set. name may be a short id. */
        data class Unlinked(val name: String) : Event()
        /** A buddy transitioned offline->online (started sharing a live fix).
         *  Carries the buddy's appId so a notification tap can focus the map on them. */
        data class CameOnline(val appId: String, val name: String) : Event()
    }

    /**
     * Decide request-received events from a pending-list transition (Req 7.1 + 3.8).
     * Returns one [Event.RequestReceived] per pending link that is newly seen OR whose
     * `reqCount` increased, but only while `reqCount <= maxAlerts`. Returns empty when
     * [prev] is null (the first emission establishes a baseline and never alerts).
     */
    fun pendingEvents(
        prev: Map<String, Int>?,
        newPending: List<BuddyLink>,
        maxAlerts: Int = BuddyConstants.REQUEST_ALERT_MAX_PER_24H
    ): List<Event> {
        if (prev == null) return emptyList()
        val events = ArrayList<Event>()
        for (link in newPending) {
            val prevCount = prev[link.linkId]
            val isNew = prevCount == null
            val bumped = prevCount != null && link.reqCount > prevCount
            if ((isNew || bumped) && link.reqCount <= maxAlerts) {
                events.add(Event.RequestReceived(link))
            }
        }
        return events
    }

    /** The `linkId -> reqCount` snapshot to carry forward after processing. */
    fun pendingSnapshot(newPending: List<BuddyLink>): Map<String, Int> =
        newPending.associate { it.linkId to it.reqCount }

    /**
     * Decide accepted/unlinked events from a buddy-list transition (Req 7.2, 7.4).
     * A buddy appearing → [Event.Accepted]; a buddy vanishing → [Event.Unlinked].
     * Names prefer the display name (from the new list for Accepted, the previous list
     * for Unlinked since the buddy is gone from the new one), falling back to a short
     * appId. Returns empty when [prev] is null (first emission = baseline).
     */
    fun buddyEvents(
        prev: Set<String>?,
        newBuddies: List<BuddyView>,
        prevBuddies: List<BuddyView> = emptyList()
    ): List<Event> {
        if (prev == null) return emptyList()
        val newById = newBuddies.associateBy { it.user.appId }
        val prevById = prevBuddies.associateBy { it.user.appId }
        val newIds = newById.keys
        val events = ArrayList<Event>()
        (newIds - prev).forEach { id ->
            val name = newById[id]?.user?.displayName?.ifBlank { null } ?: shortAppId(id)
            events.add(Event.Accepted(name))
        }
        (prev - newIds).forEach { id ->
            val name = prevById[id]?.user?.displayName?.ifBlank { null } ?: shortAppId(id)
            events.add(Event.Unlinked(name))
        }
        return events
    }

    /** The accepted-buddy id set to carry forward after processing. */
    fun buddySnapshot(newBuddies: List<BuddyView>): Set<String> =
        newBuddies.map { it.user.appId }.toSet()

    /**
     * Decide "buddy came online" events from a buddy-list transition. "Online" means
     * [BuddyView.isLiveNow] (mutual sharing + a fresh fix) — the SAME notion the map
     * uses to decide a buddy is showable, so a buddy with sharing on but a stale/absent
     * fix is NOT announced prematurely. Emits one [Event.CameOnline] per appId that is
     * online now but was not in the previous online set ([prev]). Returns empty when
     * [prev] is null (the first emission establishes the baseline and NEVER alerts —
     * the required initial-snapshot suppression). Because the controller carries the
     * [onlineSnapshot] forward each emission, a buddy already online does not re-alert
     * until it goes offline and back online (dedupe). Names use the display name with a
     * [shortAppId] fallback.
     */
    fun onlineEvents(
        prev: Set<String>?,
        newBuddies: List<BuddyView>,
        nowMs: Long = System.currentTimeMillis()
    ): List<Event> {
        if (prev == null) return emptyList()
        val onlineNow = newBuddies.filter { it.isLiveNow(nowMs) }
        val events = ArrayList<Event>()
        for (b in onlineNow) {
            val id = b.user.appId
            if (id !in prev) {
                val name = b.user.displayName.ifBlank { null } ?: shortAppId(id)
                events.add(Event.CameOnline(id, name))
            }
        }
        return events
    }

    /** The currently-online (live) buddy appId set to carry forward after processing. */
    fun onlineSnapshot(
        newBuddies: List<BuddyView>,
        nowMs: Long = System.currentTimeMillis()
    ): Set<String> =
        newBuddies.filter { it.isLiveNow(nowMs) }.map { it.user.appId }.toSet()

    /**
     * On-demand request cooldown (Req 6.1b / G6): true if enough time has elapsed since
     * the last request to [buddyAppId] to allow another.
     */
    fun canRequestLocation(
        lastRequestAt: Map<String, Long>,
        buddyAppId: String,
        nowMs: Long,
        cooldownMs: Long = BuddyConstants.ON_DEMAND_REQUEST_COOLDOWN_MS
    ): Boolean {
        val last = lastRequestAt[buddyAppId] ?: return true
        return nowMs - last >= cooldownMs
    }

    /** Compact display form of an App ID (UUIDs are long) for names without a display name. */
    fun shortAppId(appId: String): String =
        if (appId.length <= 10) appId else appId.take(6) + "…" + appId.takeLast(4)
}
