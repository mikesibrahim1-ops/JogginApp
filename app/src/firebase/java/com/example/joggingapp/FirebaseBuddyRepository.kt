package com.example.joggingapp

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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
 *                                     sharing, fcmToken, authUid, updatedAt
 *   users/{appId}/blocked/{otherId}   at
 *   links/{linkId}                    a, b, state, reqCount, reqWindowStart,
 *                                     createdAt, updatedAt   (linkId = sorted "a_b")
 *   locations/{appId}                 lat, lon, at, expireAt
 *   locations/{appId}/trail/{autoId}  lat, lon, at, expireAt
 */
class FirebaseBuddyRepository(private val appContext: Context) : BuddyRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** This device's App ID (own UUID). Present after ensureSession/first enable. */
    private val myAppId: String? get() = BuddyIdentity.getId(appContext)

    // ── Availability ──────────────────────────────────────────────────────────

    override fun observeAvailability(): Flow<Boolean> = callbackFlow {
        // Firestore has offline persistence; treat "signed in" as available. We also
        // watch the metadata (fromCache) of a cheap doc to reflect connectivity.
        trySend(auth.currentUser != null)
        val id = myAppId
        if (id == null) { trySend(false); awaitClose { }; return@callbackFlow }
        val reg: ListenerRegistration = db.collection("users").document(id)
            .addSnapshotListener { snap, _ ->
                // If we get a server (non-cache) snapshot, we're online.
                val online = snap != null && !snap.metadata.isFromCache
                trySend(online || auth.currentUser != null)
            }
        awaitClose { reg.remove() }
    }

    // ── Identity / session ──────────────────────────────────────────────────────

    override suspend fun ensureSession(appId: String): BuddyResult<Unit> = runCatching {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        val uid = user?.uid ?: return BuddyResult.Failure(BuddyResult.Reason.PERMISSION)
        // Write the resolver doc FIRST so security rules can map this caller (uid) to
        // their App ID before the user-doc create is evaluated (rules require it).
        db.collection("authMap").document(uid).set(mapOf("appId" to appId)).await()
        // Upsert the user doc keyed by our App ID, recording the auth session uid.
        db.collection("users").document(appId).set(
            mapOf(
                "authUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun updateMyUser(
        displayName: String?, photoRef: String?, buddyEnabled: Boolean?,
        gpsOn: Boolean?, sharing: Boolean?, fcmToken: String?
    ): BuddyResult<Unit> = runCatching {
        val id = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val data = buildMap<String, Any> {
            displayName?.let { put("displayName", it) }
            photoRef?.let { put("photoRef", it) }
            buddyEnabled?.let { put("buddyEnabled", it) }
            gpsOn?.let { put("gpsOn", it) }
            sharing?.let { put("sharing", it) }
            fcmToken?.let { put("fcmToken", it) }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        db.collection("users").document(id).set(data, SetOptions.merge()).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun refreshFcmToken(): BuddyResult<Unit> = runCatching {
        val id = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        // Fetch the current registration token and persist it for future server push.
        val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
        db.collection("users").document(id)
            .set(mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        AppLogger.log(appContext, LogCategory.PROFILE, "Buddy Link FCM token stored")
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    // ── Linking ───────────────────────────────────────────────────────────────

    override suspend fun requestLink(targetAppId: String): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        if (targetAppId == me) return BuddyResult.Failure(BuddyResult.Reason.UNKNOWN)
        // Confirm the target exists (Req 3.5 — unknown id → NOT_FOUND).
        val target = db.collection("users").document(targetAppId).get().await()
        if (!target.exists()) return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)

        val linkId = linkIdFor(me, targetAppId)
        val ref = db.collection("links").document(linkId)
        val now = System.currentTimeMillis()
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) {
                tx.set(ref, mapOf(
                    "a" to me, "b" to targetAppId, "state" to "pending",
                    "reqCount" to 1L, "reqWindowStart" to now,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            } else {
                // Existing link: bump the 24h alert counter (Req 3.8), keep state.
                val windowStart = snap.getLong("reqWindowStart") ?: 0L
                val within = now - windowStart <= BuddyConstants.REQUEST_ALERT_WINDOW_MS
                val newCount = if (within) (snap.getLong("reqCount") ?: 0L) + 1 else 1L
                val newWindow = if (within) windowStart else now
                tx.update(ref, mapOf(
                    "reqCount" to newCount,
                    "reqWindowStart" to newWindow,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            null
        }.await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun acceptLink(linkId: String): BuddyResult<Unit> =
        setLinkState(linkId, "accepted")

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

        fun watchOther(otherId: String) {
            if (!userRegs.containsKey(otherId)) {
                userRegs[otherId] = db.collection("users").document(otherId)
                    .addSnapshotListener { s, _ ->
                        if (s != null && s.exists()) { userCache[otherId] = s.toBuddyUser(otherId); emit() }
                    }
            }
            if (!locRegs.containsKey(otherId)) {
                locRegs[otherId] = db.collection("locations").document(otherId)
                    .addSnapshotListener { s, _ ->
                        val loc = s?.toBuddyLocation()
                        if (loc != null) { locCache[otherId] = loc; emit() }
                    }
            }
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
        val reg = db.collection("locations").document(buddyAppId)
            .collection("trail")
            .orderBy("at", Query.Direction.ASCENDING)
            .addSnapshotListener { qs, _ ->
                trySend(qs?.documents?.mapNotNull { d ->
                    val lat = d.getDouble("lat"); val lon = d.getDouble("lon")
                    val at = d.getTimestamp("at")?.toDate()?.time ?: d.getLong("at")
                    if (lat != null && lon != null && at != null) BuddyTrailPoint(lat, lon, at) else null
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
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
        // Unlink + record the block; purge is handled by removing the link + rules.
        db.collection("users").document(me).collection("blocked").document(appId)
            .set(mapOf("at" to FieldValue.serverTimestamp())).await()
        runCatching { setLinkState(linkIdFor(me, appId), "removed") }
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

    override suspend fun writeMyLocation(lat: Double, lon: Double, atMs: Long): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        val expireAt = com.google.firebase.Timestamp(java.util.Date(atMs + BuddyConstants.RETENTION_MS))
        val at = com.google.firebase.Timestamp(java.util.Date(atMs))
        val locRef = db.collection("locations").document(me)
        // Latest fix (overwritten each broadcast).
        locRef.set(mapOf("lat" to lat, "lon" to lon, "at" to at, "expireAt" to expireAt)).await()
        // Sparse trail point (one per broadcast; TTL purges after 7 days).
        locRef.collection("trail").add(
            mapOf("lat" to lat, "lon" to lon, "at" to at, "expireAt" to expireAt)
        ).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override suspend fun requestBuddyLocation(buddyAppId: String): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        // Write a request marker on the buddy's location doc; the buddy's service
        // observes locReq and pushes a fresh fix (design §8).
        db.collection("locations").document(buddyAppId).set(
            mapOf("locReq" to mapOf(me to FieldValue.serverTimestamp())),
            SetOptions.merge()
        ).await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    override fun observeLocationRequests(): Flow<List<String>> = callbackFlow {
        val me = myAppId
        if (me == null) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = db.collection("locations").document(me)
            .addSnapshotListener { s, _ ->
                @Suppress("UNCHECKED_CAST")
                val req = s?.get("locReq") as? Map<String, Any> ?: emptyMap()
                trySend(req.keys.toList())
            }
        awaitClose { reg.remove() }
    }

    // ── Data lifecycle ──────────────────────────────────────────────────────────

    override suspend fun deleteAllMyData(): BuddyResult<Unit> = runCatching {
        val me = myAppId ?: return BuddyResult.Failure(BuddyResult.Reason.NOT_FOUND)
        // Delete location + trail, user doc + blocked, and any links I'm part of.
        val locRef = db.collection("locations").document(me)
        runCatching {
            val trail = locRef.collection("trail").get().await()
            trail.documents.forEach { it.reference.delete().await() }
        }
        runCatching { locRef.delete().await() }
        runCatching {
            val blocked = db.collection("users").document(me).collection("blocked").get().await()
            blocked.documents.forEach { it.reference.delete().await() }
        }
        runCatching {
            val la = db.collection("links").whereEqualTo("a", me).get().await()
            val lb = db.collection("links").whereEqualTo("b", me).get().await()
            (la.documents + lb.documents).forEach { it.reference.delete().await() }
        }
        db.collection("users").document(me).delete().await()
        BuddyResult.Success(Unit)
    }.getOrElse { mapError(it) }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun linkIdFor(x: String, y: String): String =
        listOf(x, y).sorted().joinToString("_")

    private fun mapError(t: Throwable): BuddyResult<Nothing> {
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
        updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: 0L
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
        updatedAt = getTimestamp("updatedAt")?.toDate()?.time ?: 0L
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toBuddyLocation(): BuddyLocation? {
    val lat = getDouble("lat") ?: return null
    val lon = getDouble("lon") ?: return null
    val at = getTimestamp("at")?.toDate()?.time ?: getLong("at") ?: return null
    return BuddyLocation(lat, lon, at)
}
