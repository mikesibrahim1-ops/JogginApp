package com.example.joggingapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RouteStorageTest {
    @Test
    fun roundtripJson() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        RouteStorage.clearRoutes(ctx)
        val p = Point(1.0, 2.0, 1000L, 1.0f)
        val route = SavedRoute("t1", listOf(p), 1.0f, 0.5f, 2.0f, 10f, 1000L)
        RouteStorage.saveRoute(ctx, route)
        val loaded = RouteStorage.loadRoutes(ctx)
        assertTrue(loaded.any { it.id == "t1" })
        RouteStorage.deleteRoute(ctx, "t1")
    }

    private fun sampleRoute(id: String) =
        SavedRoute(id, listOf(Point(1.0, 2.0, 1000L, 1.0f)), 1.0f, 0.5f, 2.0f, 10f, 1000L)

    @Test
    fun backupV2_roundtrip_preservesRoutesAndBuddyId() {
        val json = BackupData.toJson(listOf(sampleRoute("r1"), sampleRoute("r2")), "abc123appid")
        val parsed = BackupData.parse(json)
        assertNotNull(parsed)
        assertEquals(BackupData.CURRENT_VERSION, parsed!!.version)
        assertEquals("abc123appid", parsed.buddyAppId)
        assertEquals(2, parsed.routes.size)
        assertEquals("r1", parsed.routes[0].id)
    }

    @Test
    fun backupV2_nullBuddyId_isPreserved() {
        val json = BackupData.toJson(listOf(sampleRoute("r1")), null)
        val parsed = BackupData.parse(json)
        assertNotNull(parsed)
        assertNull(parsed!!.buddyAppId)
        assertEquals(1, parsed.routes.size)
    }

    @Test
    fun legacyV1_bareArray_stillParses_withNoBuddyId() {
        // A legacy backup was a bare JSON array of routes.
        val legacyJson = com.google.gson.Gson().toJson(listOf(sampleRoute("old1")))
        val parsed = BackupData.parse(legacyJson)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.version)
        assertNull(parsed.buddyAppId)
        assertEquals("old1", parsed.routes.single().id)
    }

    @Test
    fun garbage_returnsNull() {
        assertNull(BackupData.parse("not json at all"))
        assertNull(BackupData.parse("{ this is : broken"))
    }
}
