package com.example.joggingapp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * An inert [BuddyRepository] used before Firebase is configured (or if the backend
 * is intentionally disabled). Every observation emits an empty/unavailable value and
 * every action returns BACKEND_UNAVAILABLE, so the rest of the app can be built and
 * run against a working (but no-op) repository. This guarantees the core app is never
 * blocked by Buddy Link (Req 10) and lets Phase 3–4 UI develop before Phase 2.4.
 */
object NoOpBuddyRepository : BuddyRepository {

    private fun <T> unavailable(): BuddyResult<T> =
        BuddyResult.Failure(BuddyResult.Reason.BACKEND_UNAVAILABLE)

    override fun observeAvailability(): Flow<Boolean> = flowOf(false)

    override suspend fun ensureSession(appId: String): BuddyResult<Unit> = unavailable()

    override suspend fun updateMyUser(
        displayName: String?, photoRef: String?, buddyEnabled: Boolean?,
        gpsOn: Boolean?, sharing: Boolean?, fcmToken: String?
    ): BuddyResult<Unit> = unavailable()

    override suspend fun refreshFcmToken(): BuddyResult<Unit> = unavailable()

    override suspend fun requestLink(targetAppId: String): BuddyResult<Unit> = unavailable()
    override suspend fun acceptLink(linkId: String): BuddyResult<Unit> = unavailable()
    override suspend fun declineLink(linkId: String): BuddyResult<Unit> = unavailable()
    override suspend fun removeLink(linkId: String): BuddyResult<Unit> = unavailable()

    override fun observeBuddies(): Flow<List<BuddyView>> = flowOf(emptyList())
    override fun observePendingRequests(): Flow<List<BuddyLink>> = flowOf(emptyList())
    override fun observeBuddyTrail(buddyAppId: String): Flow<List<BuddyTrailPoint>> = flowOf(emptyList())
    override fun observeWatchers(): Flow<List<BuddyUser>> = flowOf(emptyList())

    override suspend fun blockUser(appId: String): BuddyResult<Unit> = unavailable()
    override suspend fun unblockUser(appId: String): BuddyResult<Unit> = unavailable()
    override fun observeBlocked(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun writeMyLocation(lat: Double, lon: Double, atMs: Long): BuddyResult<Unit> = unavailable()
    override suspend fun requestBuddyLocation(buddyAppId: String): BuddyResult<Unit> = unavailable()
    override fun observeLocationRequests(): Flow<List<String>> = flowOf(emptyList())

    override suspend fun deleteAllMyData(): BuddyResult<Unit> = unavailable()
}
