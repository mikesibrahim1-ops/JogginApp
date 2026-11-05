package com.example.joggingapp

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firebase implementation of [BuddyRepository] (design §4, §9).
 *
 * IMPORTANT — this file lives in the gated `src/firebase/java` source root and is only
 * compiled when `app/google-services.json` is present (see app/build.gradle). Until
 * then the app uses [NoOpBuddyRepository] and never references any Firebase class.
 *
 * Identity model (design §5, B9): documents are keyed by our **own App ID** (a stable
 * UUID that survives Backup/Restore), NOT the Firebase anonymous uid. The anon uid is
 * only an auth session; we store it on the user doc as `authUid` so security rules can
 * map an authenticated caller to their App-ID-keyed documents. This keeps identity
 * portable across reinstalls/devices.
 *
 * Firestore layout:
 *   users/{appId}                     displayName, photoRef, buddyEnabled, gpsOn,
 *                                     sharing, createdAt, updatedAt   (public profile)
 *   users/{appId}/private/self        authUid, fcmToken, updatedAt    (owner-only, §3)
 *   users/{appId}/blocked/{otherId}   at
 *   links/{linkId}                    a, b, state, reqCount, reqWindowStart,
 *                                     createdAt, updatedAt   (linkId = sorted "a_b")
 *   locations/{appId}                 lat, lon, at, expireAt
 *   locations/{appId}/trail/{autoId}  lat, lon, at, expireAt
 */
class FirebaseBuddyRepository(private val appContext: Context) : BuddyRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val sessionMutex = Mutex()
    private val FRESH_WRITE_ATTEMPTS = 3
    private val FRESH_WRITE_TIMEOUT_MS = 5_000L

    /** This device's App ID (own UUID). Present after ensureSession/first enable. */
    private val myAppId: String? get() = BuddyIdentity.getId(appContext)

    // ── Availability ──────────────────────────────────────────────────────────

    override fun observeAvailability(): Flow<Boolean> = callbackFlow {
        // Firestore has offline persistence; treat "signed in" as available. We also
        // watch the metadata (fromCache) of a cheap doc to reflect connectivity.
        trySend(auth.currentUser != null)
        val id = myAppId
        if (id == null) { trySend(false); awaitClose { }; return@callbackFlow }
        // V1.49: re-emit on auth changes so a LATE sign-in (session auto-retry) flips
        // availability to true. The users/{id} listener below may have been denied
        // (and terminated by the SDK) while signed out, so it can't be relied on for that.
        val authListener = FirebaseAuth.AuthStateListener { a -> trySend(a.currentUser != null) }
        auth.addAuthStateListener(authListener)
        val reg: ListenerRegistration = db.collection("users").document(id)
            .addSnapshotListener { snap, _ ->
                // If we get a server (non-cache) snapshot, we're online.
                val online = snap != null && !snap.metadata.isFromCache
                trySend(online || auth.currentUser != null)
            }
        awaitClose { reg.remove(); auth.removeAuthStateListener(authListener) }
    }

    override fun needsSession(): Boolean = auth.currentUser == null

    // ── Identity / session ──────────────────────────────────────────────────────

    // The controller and BuddyLocationService both get here on first enable, so run one at a
    // time. Two parallel sign-ins make two users, and writes queued under the first then never
    // complete. Two parallel profile creates clash on createdAt, which the rules make immutable.
    override suspend fun ensureSession(appId: String): BuddyResult<Unit> = sessionMutex.withLock { runCatching {
        val freshSignIn = auth.currentUser == null
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        val uid = user?.uid ?: return BuddyResult.Failure(BuddyResult.Reason.PERMISSION)
        // Write the resolver doc FIRST so security rules can map this caller (uid) to
        // their App ID before the user-doc create is evaluated (rules require it).
        val authMapRef = db.collection("authMap").document(uid)
        val authMapData = mapOf("appId" to appId)
        if (freshSignIn) writeAfterSignIn(authMapRef, authMapData) else authMapRef.set(authMapData).await()
        // Write the owner-only private subdoc carrying authUid FIRST (design §3.5). This
        // must precede the public-doc merge in ALL cases: for a returning user the public
        // merge is an UPDATE gated by ownsUser(), which reads authUid from this subdoc.
        // Authorized on create via mappedToMe(appId) (authMap written above).
        db.collection("users").document(appId)
            .collection("private").document("self")
            .set(mapOf("authUid" to uid, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        // Upsert the user doc keyed by our App ID. authUid NO LONGER lives here (§3).
        // Write createdAt ("member since") create-if-absent only: read the doc first and
        // include a server timestamp when it does not yet exist OR has no createdAt, so an
        // existing account's creation time is NEVER clobbered. The §4.3 rule also makes
        // createdAt immutable on update as the authoritative guarantee if this self-read
        // is denied on an un-migrated account.
        val userRef = db.collection("users").document(appId)
        val existing = runCatching { userRef.get().await() }.getOrNull()
        val needsCreatedAt = existing == null || !existing.exists() || existing.get("createdAt") == null
        val sessionData = buildMap<String, Any> {
            put("updatedAt", FieldValue.serverTimestamp())
            if (needsCreatedAt) put("createdAt", FieldValue.serverTimestamp())
            // V1.58 post-merge migration: authUid & fcmToken moved to the private subdoc
            // (§3). A pre-V1.52 public user doc may still carry stale copies of these
            // fields, and the V1.52 ownsUser()/hasOnly() update rule rejects a write that
            // leaves them present. Delete them here so the public doc converges to the
            // whitelisted field set. FieldValue.delete() on an absent field is a no-op,
            // so this is idempotent for already-migrated accounts.
            put("authUid", FieldValue.delete())
            put("fcmToken", FieldValue.delete())
        }
        userRef.set(sessionData, SetOptions.merge()).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) } }

    /**
     * Firestore learns about a new sign-in asynchronously. If it's already running, a write
     * made straight afterwards can be queued under the signed-out user and never complete, so
     * re-issue it once that has had time to settle. Only for idempotent writes.
     */
    private suspend fun writeAfterSignIn(ref: DocumentReference, data: Map<String, Any>) {
        repeat(FRESH_WRITE_ATTEMPTS - 1) {
            if (withTimeoutOrNull(FRESH_WRITE_TIMEOUT_MS) { ref.set(data).await(); true } != null) return
            AppLogger.log(appContext, LogCategory.SERVICE, "Buddy first write after sign-in stalled, retrying")
        }
        ref.set(data).await()
    }

    override suspend fun updateMyUser(
        displayName: String?, photoRef: String?, buddyEnabled: Boolean?,
        gpsOn: Boolean?, sharing: Boolean?, fcmToken: String?, shareMode: BuddyShareMode?
    ): BuddyResult<Unit> = runCatching {
        val id = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val data = buildMap<String, Any> {
            displayName?.let { put("displayName", it) }
            photoRef?.let { put("photoRef", it) }
            buddyEnabled?.let { put("buddyEnabled", it) }
            gpsOn?.let { put("gpsOn", it) }
            sharing?.let { put("sharing", it) }
            // Public-profile share mode ('ping'/'route'); read by the buddy's trail-read
            // rule (design §7.2). Only written when non-null.
            shareMode?.let { put("shareMode", it.wire) }
            put("updatedAt", FieldValue.serverTimestamp())
            // V1.58 belt-and-braces migration (same as ensureSession): strip any stale
            // authUid/fcmToken off the public doc so the hasOnly() update rule accepts
            // this merge on a pre-V1.52 account. Idempotent no-op when already absent.
            put("authUid", FieldValue.delete())
            put("fcmToken", FieldValue.delete())
        }
        db.collection("users").document(id).set(data, SetOptions.merge()).await()
        // fcmToken moved to the owner-only private subdoc (design §3). Defensive-only:
        // no live caller passes fcmToken here. Best-effort with the same fail-closed /
        // self-heal semantics as refreshFcmToken — a denial (subdoc not yet created)
        // persists nothing and self-heals on the next refreshFcmToken after ensureSession.
        if (fcmToken != null) {
            runCatching {
                db.collection("users").document(id)
                    .collection("private").document("self")
                    .set(mapOf("fcmToken" to fcmToken, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .await()
            }
        }
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun refreshFcmToken(): BuddyResult<Unit> = runCatching {
        val id = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        // Fetch the current registration token and persist it for future server push.
        val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
        // Token stored in the owner-only private subdoc (design §3). Fails closed if the
        // subdoc does not yet exist (no authUid in a token-only payload); self-heals once
        // ensureSession has created the subdoc.
        db.collection("users").document(id)
            .collection("private").document("self")
            .set(mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link FCM token stored")
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    // ── Linking ───────────────────────────────────────────────────────────────

    override suspend fun requestLink(targetAppId: String, fromName: String?): BuddyResult<Unit> {
        // Set inside the transaction when the (last) attempt was creating the link doc,
        // so a rules denial of that create can be reported as an unknown code.
        var creating = false
        return runCatching {
            val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
            if (targetAppId == me) return BuddyResult.Failure(BuddyResult.Reason.UNKNOWN)
            // V1.49: NO users/{target} precheck. That read is denied by design before any
            // link exists (users read needs a link), which made every Connect fail with
            // PERMISSION_DENIED. Target existence is now enforced by the links create rule
            // (exists(users/{b})); a denied create is mapped to NOT_FOUND below (Req 3.5).

            val safeFromName = fromName ?: ""
            val linkId = linkIdFor(me, targetAppId)
            val ref = db.collection("links").document(linkId)
            val now = System.currentTimeMillis()

            // Decide what to do with any existing link doc. A declined/removed link is a
            // dead-end: the deployed rules forbid an in-place declined/removed → pending
            // transition, so a plain update would bump the counter but keep the dead state
            // and silently report a false "Request sent". Instead we delete the dead-end
            // and recreate a genuinely fresh pending request (both writes are permitted by
            // the already-deployed rules — either party may delete, side `a` may create).
            val preSnap = ref.get().await()
            val action = BuddyLinkDecisions.requestLinkAction(
                if (preSnap.exists()) BuddyLinkDecisions.parseState(preSnap.getString("state")) else null
            )
            if (action == BuddyLinkDecisions.RequestLinkAction.RECREATE_PENDING) {
                // Drop the dead-end so the create path below runs against a missing doc.
                ref.delete().await()
            }

            db.runTransaction { tx ->
                // Reading a missing link doc is allowed by the rules (resource == null).
                val snap = tx.get(ref)
                creating = !snap.exists()
                if (!snap.exists()) {
                    tx.set(ref, mapOf(
                        "a" to me, "b" to targetAppId, "state" to "pending",
                        "reqCount" to 1L, "reqWindowStart" to now,
                        "fromName" to safeFromName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                } else {
                    // Existing live link (pending/accepted): bump the 24h alert counter
                    // (Req 3.8), keep state.
                    val windowStart = snap.getLong("reqWindowStart") ?: 0L
                    val within = now - windowStart <= BuddyConstants.REQUEST_ALERT_WINDOW_MS
                    val newCount = if (within) (snap.getLong("reqCount") ?: 0L) + 1 else 1L
                    val newWindow = if (within) windowStart else now
                    val updates = buildMap<String, Any> {
                        put("reqCount", newCount)
                        put("reqWindowStart", newWindow)
                        put("updatedAt", FieldValue.serverTimestamp())
                        // Refresh the denormalized name on a re-request when provided.
                        if (safeFromName.isNotBlank()) put("fromName", safeFromName)
                    }
                    tx.update(ref, updates)
                }
                null
            }.await()
            BuddyResult.Success(Unit)
        }.getOrElse { t ->
            val failure = mapError(t)
            // The create rule rejects a code with no users/{b} doc → "unknown code".
            if (creating && failure.reason == BuddyResult.Reason.PERMISSION)
                BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND, failure.message)
            else failure
        }
    }

    override suspend fun acceptInvite(fromAppId: String, fromName: String?): BuddyResult<InviteOutcome> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        if (fromAppId == me) return BuddyResult.Failure(BuddyResult.Reason.UNKNOWN)
        // V1.49: no users/{fromAppId} precheck (denied before a link exists), and a link
        // is never created already-accepted (rules require create state == 'pending').
        val ref = db.collection("links").document(linkIdFor(me, fromAppId))
        val action = db.runTransaction { tx ->
            // Missing doc is readable (rules: resource == null).
            val snap = tx.get(ref)
            val state = if (snap.exists()) BuddyLinkDecisions.parseState(snap.getString("state")) else null
            val act = BuddyLinkDecisions.inviteAction(me, state, snap.getString("b"))
            if (act == BuddyLinkDecisions.InviteAction.ACCEPT_EXISTING) {
                // The inviter already requested me (I'm side b) → accept it.
                tx.update(ref, mapOf(
                    "state" to "accepted",
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            act
        }.await()
        // `action` comes back as a platform type from runTransaction{}.await(), so Kotlin
        // treats it as nullable (InviteAction!). The transaction always returns a non-null
        // `act`, but handle the (impossible) null explicitly so the when is exhaustive and
        // we never crash on a surprise null rather than relying on !!.
        when (action) {
            BuddyLinkDecisions.InviteAction.ACCEPT_EXISTING,
            BuddyLinkDecisions.InviteAction.ALREADY_CONNECTED -> BuddyResult.Success(InviteOutcome.CONNECTED)
            // Send a pending request back to the inviter (a = me), who then accepts.
            BuddyLinkDecisions.InviteAction.SEND_REQUEST -> when (val r = requestLink(fromAppId, fromName)) {
                is BuddyResult.Success -> BuddyResult.Success(InviteOutcome.REQUEST_SENT)
                is BuddyResult.Failure -> r
            }
            null -> BuddyResult.Failure(BuddyResult.Reason.UNKNOWN)
        }
    }.getOrElse { mapError(it) }

    override suspend fun acceptLink(linkId: String): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val ref = db.collection("links").document(linkId)
        // Client-side guard mirroring the rules: only the recipient (side b) may accept,
        // and only a pending link.
        val accepted = db.runTransaction { tx ->
            val snap = tx.get(ref)
            val state = if (snap.exists()) BuddyLinkDecisions.parseState(snap.getString("state")) else null
            val ok = BuddyLinkDecisions.canAccept(me, state, snap.getString("b"))
            if (ok) {
                tx.update(ref, mapOf("state" to "accepted", "updatedAt" to FieldValue.serverTimestamp()))
            }
            ok
        }.await()
        if (accepted) BuddyResult.Success(Unit)
        else BuddyResult.Failure(BuddyResult.Reason.PERMISSION, "only the invited user can accept a pending link")
    }.getOrElse { mapError(it) }

    override suspend fun declineLink(linkId: String): BuddyResult<Unit> =
        setLinkState(linkId, "declined")

    override suspend fun removeLink(linkId: String): BuddyResult<Unit> =
        setLinkState(linkId, "removed")

    private suspend fun setLinkState(linkId: String, state: String): BuddyResult<Unit> = runCatching {
        db.collection("links").document(linkId).update(
            mapOf("state" to state, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override fun observeBuddies(): Flow<List<BuddyView>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        // Accepted links where I'm either side.
        val regA = db.collection("links")
            .whereEqualTo("state", "accepted")
            .whereEqualTo("a", me)
        val regB = db.collection("links")
            .whereEqualTo("state", "accepted")
            .whereEqualTo("b", me)

        val links = HashMap<String, BuddyLink>()
        val userCache = HashMap<String, BuddyUser>()
        val locCache = HashMap<String, BuddyLocation>()
        val userRegs = HashMap<String, ListenerRegistration>()
        val locRegs = HashMap<String, ListenerRegistration>()

        fun emit() {
            val views = links.values.map { link ->
                val other = if (link.a == me) link.b else link.a
                BuddyView(
                    user = userCache[other] ?: BuddyUser(appId = other),
                    location = locCache[other],
                    link = link
                )
            }
            trySend(views)
        }

        // The rules deny a location read while either user's `sharing` is false, and a denied
        // Firestore listener never fires again. Without re-attaching, the last fix would go
        // stale ("location unavailable") until the app restarts. Runs on the main thread,
        // like the snapshot callbacks that own these maps.
        val locOutages = mutableSetOf<String>()
        fun watchLocation(otherId: String) {
            if (locRegs.containsKey(otherId)) return
            locRegs[otherId] = db.collection("locations").document(otherId)
                .addSnapshotListener { s, e ->
                    if (e != null) {
                        // Once per outage: retries repeat every 30s and the log only keeps 2000 lines.
                        if (locOutages.add(otherId)) {
                            AppLogger.log(appContext, LogCategory.ERROR, "Buddy location listener stopped: ${e.code} — will retry")
                        }
                        locRegs.remove(otherId)?.remove()
                        launch(Dispatchers.Main) {
                            delay(BuddyConstants.LISTENER_RETRY_MS)
                            if (links.values.any { it.a == otherId || it.b == otherId }) watchLocation(otherId)
                        }
                        return@addSnapshotListener
                    }
                    // A cached snapshot can fire just before the server denies the read again.
                    if (s?.metadata?.isFromCache == false && locOutages.remove(otherId)) {
                        AppLogger.log(appContext, LogCategory.SERVICE, "Buddy location listener recovered")
                    }
                    val loc = s?.toBuddyLocation()
                    if (loc != null) { locCache[otherId] = loc; emit() }
                }
        }

        fun watchOther(otherId: String) {
            if (!userRegs.containsKey(otherId)) {
                userRegs[otherId] = db.collection("users").document(otherId)
                    .addSnapshotListener { s, _ ->
                        if (s != null && s.exists()) {
                            val user = s.toBuddyUser(otherId)
                            userCache[otherId] = user
                            // They just started sharing: don't wait for the retry delay.
                            if (user.sharing) watchLocation(otherId)
                            emit()
                        }
                    }
            }
            watchLocation(otherId)
        }

        val onLinks = { qs: com.google.firebase.firestore.QuerySnapshot? ->
            qs?.documentChanges?.forEach { ch ->
                val link = ch.document.toBuddyLink()
                when (ch.type) {
                    DocumentChange.Type.REMOVED -> links.remove(link.linkId)
                    else -> { links[link.linkId] = link; watchOther(if (link.a == me) link.b else link.a) }
                }
            }
            emit()
        }
        val la = regA.addSnapshotListener { qs, _ -> onLinks(qs) }
        val lb = regB.addSnapshotListener { qs, _ -> onLinks(qs) }

        awaitClose {
            la.remove(); lb.remove()
            userRegs.values.forEach { it.remove() }
            locRegs.values.forEach { it.remove() }
        }
    }

    override fun observePendingRequests(): Flow<List<BuddyLink>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        // Incoming: links where I'm the recipient (b) and state is pending.
        val reg = db.collection("links")
            .whereEqualTo("b", me)
            .whereEqualTo("state", "pending")
            .addSnapshotListener { qs, _ ->
                trySend(qs?.documents?.map { it.toBuddyLink() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    override fun observeBuddyTrail(buddyAppId: String): Flow<List<BuddyTrailPoint>> = callbackFlow {
        var reg: ListenerRegistration? = null
        var trailOutage = false
        // Old points are pruned by their owner's app, which may not have run lately.
        val cutoff = com.google.firebase.Timestamp(
            java.util.Date(System.currentTimeMillis() - BuddyConstants.RETENTION_MS)
        )
        fun attach() {
            reg = db.collection("locations").document(buddyAppId)
                .collection("trail")
                .whereGreaterThan("at", cutoff)
                .orderBy("at", Query.Direction.ASCENDING)
                .limitToLast(BuddyConstants.TRAIL_QUERY_LIMIT)
                .addSnapshotListener { qs, e ->
                    if (e != null) {
                        // A denied listener is dead for good, so re-attach (see observeBuddies).
                        if (!trailOutage) {
                            trailOutage = true
                            AppLogger.log(appContext, LogCategory.ERROR, "Buddy trail listener stopped: ${e.code} — will retry")
                        }
                        reg?.remove(); reg = null
                        launch(Dispatchers.Main) { delay(BuddyConstants.LISTENER_RETRY_MS); attach() }
                        return@addSnapshotListener
                    }
                    if (qs?.metadata?.isFromCache == false) trailOutage = false
                    if (qs != null) trySend(qs.documents.mapNotNull { d ->
                        val lat = d.getDouble("lat"); val lon = d.getDouble("lon")
                        val at = d.getTimestamp("at")?.toDate()?.time ?: d.getLong("at")
                        if (lat != null && lon != null && at != null) BuddyTrailPoint(lat, lon, at) else null
                    })
                }
        }
        attach()
        awaitClose { reg?.remove() }
    }

    override fun observeWatchers(): Flow<List<BuddyUser>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        // Someone can see me = accepted buddy whose sharing==true AND I'm sharing.
        // The viewer's own sharing gate is enforced client-side (we only render the
        // eye when enabled); here we surface accepted buddies who are sharing.
        val regA = db.collection("links").whereEqualTo("state", "accepted").whereEqualTo("a", me)
        val regB = db.collection("links").whereEqualTo("state", "accepted").whereEqualTo("b", me)
        val others = HashSet<String>()
        val sharingUsers = HashMap<String, BuddyUser>()
        val userRegs = HashMap<String, ListenerRegistration>()

        fun emit() = trySend(sharingUsers.values.filter { it.sharing }.toList())

        fun watch(otherId: String) {
            if (userRegs.containsKey(otherId)) return
            userRegs[otherId] = db.collection("users").document(otherId)
                .addSnapshotListener { s, _ ->
                    if (s != null && s.exists()) { sharingUsers[otherId] = s.toBuddyUser(otherId); emit() }
                }
        }
        val onLinks = { qs: com.google.firebase.firestore.QuerySnapshot? ->
            qs?.documents?.forEach { d ->
                val link = d.toBuddyLink()
                val other = if (link.a == me) link.b else link.a
                if (others.add(other)) watch(other)
            }
            emit()
        }
        val la = regA.addSnapshotListener { qs, _ -> onLinks(qs) }
        val lb = regB.addSnapshotListener { qs, _ -> onLinks(qs) }
        awaitClose { la.remove(); lb.remove(); userRegs.values.forEach { it.remove() } }
    }

    // ── Blocking ────────────────────────────────────────────────────────────────

    override suspend fun blockUser(appId: String): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        // Record the block only; the accepted link is PRESERVED so unblock reinstates the
        // buddy via the existing observeBuddies flow (reversible block). Sensitive paths
        // (locations/trail/locReq/cheers) stay denied server-side via the rules' mutual
        // `!hasBlocked` gate, so a preserved accepted link leaks nothing while blocked.
        db.collection("users").document(me).collection("blocked").document(appId)
            .set(mapOf("at" to FieldValue.serverTimestamp())).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun unblockUser(appId: String): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        db.collection("users").document(me).collection("blocked").document(appId).delete().await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override fun observeBlocked(): Flow<List<String>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = db.collection("users").document(me).collection("blocked")
            .addSnapshotListener { qs, _ -> trySend(qs?.documents?.map { it.id } ?: emptyList()) }
        awaitClose { reg.remove() }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    override suspend fun writeMyLocation(
        lat: Double, lon: Double, atMs: Long, appendTrail: Boolean
    ): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val expireAt = com.google.firebase.Timestamp(java.util.Date(atMs + BuddyConstants.RETENTION_MS))
        val at = com.google.firebase.Timestamp(java.util.Date(atMs))
        val locRef = db.collection("locations").document(me)
        val payload = mapOf("lat" to lat, "lon" to lon, "at" to at, "expireAt" to expireAt)
        // One batch, so a trail point queued offline isn't held back waiting for the fix
        // write's server ack: Firestore persists both and syncs them on reconnect.
        val batch = db.batch()
        batch.set(locRef, payload)
        if (appendTrail) batch.set(locRef.collection("trail").document(), payload)
        batch.commit().await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun requestBuddyLocation(buddyAppId: String): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        // Write a request marker in the buddy's locReq subcollection (design §5, F10);
        // the owner observes locReq and pushes a fresh fix. MUST remain a non-merge set so
        // the payload is always exactly {at} and satisfies the hasOnly(['at']) rule.
        db.collection("locations").document(buddyAppId)
            .collection("locReq").document(me)
            .set(mapOf("at" to FieldValue.serverTimestamp()))
            .await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override fun observeLocationRequests(): Flow<List<String>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        // Listen to the locReq subcollection (design §5.4). Delete each marker after
        // emitting so markers do not accumulate (no TTL on this subcollection); the owner
        // holds delete permission (§5.3). Best-effort; failures ignored.
        val reg = db.collection("locations").document(me).collection("locReq")
            .addSnapshotListener { qs, _ ->
                val ids = qs?.documents?.map { it.id } ?: emptyList()
                trySend(ids)
                qs?.documents?.forEach { it.reference.delete() }
            }
        awaitClose { reg.remove() }
    }

    // ── Live Cheer (S1) ───────────────────────────────────────────────────────

    override suspend fun sendCheer(buddyAppId: String, fromName: String?): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val now = System.currentTimeMillis()
        val at = com.google.firebase.Timestamp(java.util.Date(now))
        val expireAt = com.google.firebase.Timestamp(java.util.Date(now + BuddyConstants.CHEER_RETENTION_MS))
        // Append-only event under the recipient-keyed inbox (design §"Data model"); the
        // create rule enforces accepted + mutually-unblocked + from == myAppId().
        db.collection("cheers").document(buddyAppId)
            .collection("inbox").add(
                mapOf(
                    "from" to me,
                    "fromName" to (fromName ?: ""),
                    "at" to at,
                    "expireAt" to expireAt
                )
            ).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override fun observeCheers(): Flow<List<BuddyCheer>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        // Listen to my inbox (mirrors observeLocationRequests). Delete each consumed
        // cheer after emitting so markers don't accumulate; TTL/expireAt is the backstop.
        // Best-effort delete — a failure just re-emits on the next snapshot, which the
        // controller's seenCheerIds baseline dedupes.
        val reg = db.collection("cheers").document(me).collection("inbox")
            .orderBy("at", Query.Direction.ASCENDING)
            .addSnapshotListener { qs, _ ->
                val cheers = qs?.documents?.mapNotNull { d ->
                    val from = d.getString("from") ?: return@mapNotNull null
                    val at = d.getTimestamp("at")?.toDate()?.time ?: return@mapNotNull null
                    BuddyCheer(d.id, from, d.getString("fromName").orEmpty(), at)
                } ?: emptyList()
                trySend(cheers)
                qs?.documents?.forEach { it.reference.delete() }
            }
        awaitClose { reg.remove() }
    }

    // ── Run together ──────────────────────────────────────────────────────────

    override suspend fun sendRunSignal(
        buddyAppId: String, kind: RunSignalKind, inviteId: String, activityType: Int, fromName: String?
    ): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val now = System.currentTimeMillis()
        db.collection("runSignals").document(buddyAppId)
            .collection("inbox").add(
                mapOf(
                    "from" to me,
                    "fromName" to (fromName ?: ""),
                    "kind" to kind.wire,
                    "inviteId" to inviteId,
                    "activityType" to activityType,
                    "at" to com.google.firebase.Timestamp(java.util.Date(now)),
                    "expireAt" to com.google.firebase.Timestamp(java.util.Date(now + BuddyConstants.CHEER_RETENTION_MS))
                )
            ).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override fun observeRunSignals(): Flow<List<RunSignal>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        var reg: ListenerRegistration? = null
        var outage = false
        fun attach() {
            reg = db.collection("runSignals").document(me).collection("inbox")
                .orderBy("at", Query.Direction.ASCENDING)
                .addSnapshotListener { qs, e ->
                    if (e != null) {
                        // A denied listener is dead for good, so re-attach (see observeBuddies).
                        if (!outage) {
                            outage = true
                            AppLogger.log(appContext, LogCategory.ERROR, "Run-together listener stopped: ${e.code} — will retry")
                        }
                        reg?.remove(); reg = null
                        launch(Dispatchers.Main) { delay(BuddyConstants.LISTENER_RETRY_MS); attach() }
                        return@addSnapshotListener
                    }
                    if (qs?.metadata?.isFromCache == false) outage = false
                    val signals = qs?.documents?.mapNotNull { d ->
                        val from = d.getString("from") ?: return@mapNotNull null
                        val kind = RunSignalKind.fromWire(d.getString("kind")) ?: return@mapNotNull null
                        val inviteId = d.getString("inviteId") ?: return@mapNotNull null
                        val at = d.getTimestamp("at")?.toDate()?.time ?: return@mapNotNull null
                        RunSignal(
                            d.id, from, d.getString("fromName").orEmpty(), kind, inviteId,
                            d.getLong("activityType")?.toInt() ?: 0, at
                        )
                    } ?: emptyList()
                    trySend(signals)
                    qs?.documents?.forEach { it.reference.delete() }
                }
        }
        attach()
        awaitClose { reg?.remove() }
    }

    // ── Live-tracking share link for non-app users (S2) ──────────────────────────

    override suspend fun createShareLink(label: String?): BuddyResult<ShareLink> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val token = ShareToken.generate()
        val expiresAtMs = ShareLifetime.expiresAt(System.currentTimeMillis())
        val expiresAt = com.google.firebase.Timestamp(java.util.Date(expiresAtMs))
        val trimmedLabel = label?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
        // Write the owner-mapping doc FIRST so the shares/{token} create rule can verify
        // this caller owns the token (mirrors the ensureSession authMap-first ordering).
        db.collection("shareOwners").document(token).set(
            mapOf(
                "ownerAppId" to me,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt
            )
        ).await()
        // Control-only public doc: NO lat/lon/at yet (the first fix arrives via
        // writeShareFix). If this create fails, best-effort delete the orphan owner doc.
        try {
            val shareData = buildMap<String, Any> {
                put("expiresAt", expiresAt)
                put("revoked", false)
                trimmedLabel?.let { put("label", it) }
            }
            db.collection("shares").document(token).set(shareData).await()
        } catch (t: Throwable) {
            runCatching { db.collection("shareOwners").document(token).delete().await() }
            throw t
        }
        // Persist the active share to jog_prefs so the service can mirror fixes into it.
        appContext.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE).edit().apply {
            putString(BuddyConstants.PREFS_ACTIVE_SHARE_TOKEN, token)
            putLong(BuddyConstants.PREFS_ACTIVE_SHARE_EXPIRES_AT, expiresAtMs)
            if (trimmedLabel != null) putString(BuddyConstants.PREFS_ACTIVE_SHARE_LABEL, trimmedLabel)
            else remove(BuddyConstants.PREFS_ACTIVE_SHARE_LABEL)
            apply()
        }
        BuddyResult.Success(ShareLink(token, expiresAtMs))
    }.getOrElse { mapError(it) }

    override suspend fun writeShareFix(
        token: String, lat: Double, lon: Double, atMs: Long, expiresAtMs: Long, label: String?
    ): BuddyResult<Unit> = runCatching {
        val at = com.google.firebase.Timestamp(java.util.Date(atMs))
        // expiresAtMs MUST be the verbatim PREFS_ACTIVE_SHARE_EXPIRES_AT value, never
        // recomputed as now+lifetime, so the write-once rule's expiresAt equality holds.
        val expiresAt = com.google.firebase.Timestamp(java.util.Date(expiresAtMs))
        val trimmedLabel = label?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
        // MERGE only the fix fields + the UNCHANGED expiresAt (re-sent for the equality
        // check); NEVER write `revoked` (that is the control-plane field).
        val data = buildMap<String, Any> {
            put("lat", lat)
            put("lon", lon)
            put("at", at)
            put("expiresAt", expiresAt)
            trimmedLabel?.let { put("label", it) }
        }
        db.collection("shares").document(token).set(data, SetOptions.merge()).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun revokeShareLink(token: String): BuddyResult<Unit> = runCatching {
        // Mark revoked FIRST so any in-flight reader sees the kill switch immediately.
        db.collection("shares").document(token).update("revoked", true).await()
        // Clear the active-share prefs so the service stops mirroring fixes.
        appContext.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE).edit().apply {
            remove(BuddyConstants.PREFS_ACTIVE_SHARE_TOKEN)
            remove(BuddyConstants.PREFS_ACTIVE_SHARE_EXPIRES_AT)
            remove(BuddyConstants.PREFS_ACTIVE_SHARE_LABEL)
            apply()
        }
        // Best-effort cleanup of the control + owner docs.
        runCatching { db.collection("shares").document(token).delete().await() }
        runCatching { db.collection("shareOwners").document(token).delete().await() }
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    // ── Data lifecycle ──────────────────────────────────────────────────────────

    override suspend fun deleteAllMyData(): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val locRef = db.collection("locations").document(me)
        val userRef = db.collection("users").document(me)
        // Any failure stops here and is reported, with ownership intact so a retry can finish.
        deleteMatching(locRef.collection("trail"))
        deleteMatching(locRef.collection("locReq"))
        locRef.delete().await()
        deleteMatching(db.collection("cheers").document(me).collection("inbox"))
        deleteMatching(db.collection("runSignals").document(me).collection("inbox"))
        deleteMatching(userRef.collection("blocked"))
        deleteMatching(db.collection("links").whereEqualTo("a", me))
        deleteMatching(db.collection("links").whereEqualTo("b", me))
        userRef.delete().await()
        // private/self and authMap prove ownership to the rules, so they must go last. A
        // parent delete doesn't cascade, so both need removing explicitly.
        userRef.collection("private").document("self").delete().await()
        auth.currentUser?.uid?.let { db.collection("authMap").document(it).delete().await() }
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun pruneMyTrail(olderThanMs: Long): BuddyResult<Int> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val cutoff = com.google.firebase.Timestamp(java.util.Date(olderThanMs))
        BuddyResult.Success(
            deleteMatching(
                db.collection("locations").document(me).collection("trail").whereLessThan("at", cutoff)
            )
        )
    }.getOrElse { mapError(it) }

    /** Deletes everything [query] matches, reading from the server so nothing is missed. */
    private suspend fun deleteMatching(query: Query): Int {
        var deleted = 0
        while (true) {
            val docs = query.limit(BuddyConstants.DELETE_PAGE_SIZE).get(Source.SERVER).await().documents
            if (docs.isEmpty()) return deleted
            Tasks.whenAll(docs.map { it.reference.delete() }).await()
            deleted += docs.size
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun linkIdFor(x: String, y: String): String =
        listOf(x, y).sorted().joinToString("_")

    private fun mapError(t: Throwable): BuddyResult.Failure {
        val reason = when {
            t is com.google.firebase.firestore.FirebaseFirestoreException &&
                t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                BuddyResult.Reason.PERMISSION
            t is com.google.firebase.firestore.FirebaseFirestoreException &&
                t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                BuddyResult.Reason.BACKEND_UNAVAILABLE
            else -> BuddyResult.Reason.UNKNOWN
        }
        AppLogger.log(appContext, LogCategory.ERROR, "Firebase buddy op failed: ${t.message}")
        return BuddyResult.Failure(reason, t.message)
    }
}

// ── Firestore document mappers ────────────────────────────────────────────────

private fun com.google.firebase.firestore.DocumentSnapshot.toBuddyUser(appId: String): BuddyUser =
    BuddyUser(
        appId = appId,
        displayName = getString("displayName") ?: "",
        photoRef = getString("photoRef"),
        buddyEnabled = getBoolean("buddyEnabled") ?: false,
        gpsOn = getBoolean("gpsOn") ?: false,
        sharing = getBoolean("sharing") ?: false,
        shareMode = BuddyShareMode.fromWire(getString("shareMode")),
        updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L
    )

private fun com.google.firebase.firestore.DocumentSnapshot.toBuddyLink(): BuddyLink {
    val stateStr = getString("state") ?: "pending"
    return BuddyLink(
        linkId = id,
        a = getString("a") ?: "",
        b = getString("b") ?: "",
        state = when (stateStr) {
            "accepted" -> BuddyLinkState.ACCEPTED
            "declined" -> BuddyLinkState.DECLINED
            "removed" -> BuddyLinkState.REMOVED
            else -> BuddyLinkState.PENDING
        },
        reqCount = (getLong("reqCount") ?: 0L).toInt(),
        reqWindowStart = getLong("reqWindowStart") ?: 0L,
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: 0L,
        fromName = getString("fromName") ?: ""
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toBuddyLocation(): BuddyLocation? {
    val lat = getDouble("lat") ?: return null
    val lon = getDouble("lon") ?: return null
    val at = getTimestamp("at")?.toDate()?.time ?: getLong("at") ?: return null
    return BuddyLocation(lat, lon, at)
}
