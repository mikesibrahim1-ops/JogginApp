package com.example.joggingapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackupDataTest {

    private fun sampleRoute(id: String) =
        SavedRoute(id, listOf(Point(1.0, 2.0, 1000L, 1.0f)), 1.0f, 0.5f, 2.0f, 10f, 1000L)

    private fun sampleTarget(id: String) =
        ExerciseTarget(id = id, type = ExerciseType.PUSHUPS, period = TargetPeriod.DAILY, amount = 50f)

    private fun sampleRep(id: String) =
        RepLogEntry(id = id, type = ExerciseType.PUSHUPS, count = 20, timestamp = System.currentTimeMillis())

    private fun sampleCompleted(id: String) =
        CompletedTarget(
            id = id, targetId = "tgt-$id", periodKey = "DAILY:1000", type = ExerciseType.PUSHUPS,
            period = TargetPeriod.DAILY, amount = 50f, achievedAt = 1000L
        )

    @Test
    fun backupV3_roundtrip_preservesRoutesBuddyIdAndExerciseData() {
        val json = BackupData.toJson(
            listOf(sampleRoute("r1"), sampleRoute("r2")),
            "abc123appid",
            listOf(sampleTarget("t1")),
            listOf(sampleRep("rl1"), sampleRep("rl2")),
            listOf(sampleCompleted("c1"))
        )
        val parsed = BackupData.parse(json)
        assertNotNull(parsed)
        assertEquals(BackupData.CURRENT_VERSION, parsed!!.version)
        assertEquals(3, parsed.version)
        assertEquals("abc123appid", parsed.buddyAppId)
        assertEquals(2, parsed.routes.size)
        assertEquals("r1", parsed.routes[0].id)
        assertEquals(1, parsed.exerciseTargets.size)
        assertEquals("t1", parsed.exerciseTargets[0].id)
        assertEquals(ExerciseType.PUSHUPS, parsed.exerciseTargets[0].type)
        assertEquals(2, parsed.repLog.size)
        assertEquals("rl1", parsed.repLog[0].id)
        assertEquals(1, parsed.completedTargets.size)
        assertEquals("c1", parsed.completedTargets[0].id)
    }

    @Test
    fun v2Json_withoutExerciseFields_parsesWithEmptyExerciseLists() {
        // A v2 backup is an object with version/buddyAppId/routes and NO exercise arrays.
        val routesJson = Gson().toJson(listOf(sampleRoute("r1")))
        val v2Json = """{"version":2,"buddyAppId":"buddy1","routes":$routesJson}"""
        val parsed = BackupData.parse(v2Json)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.version)
        assertEquals("buddy1", parsed.buddyAppId)
        assertEquals(1, parsed.routes.size)
        assertTrue(parsed.exerciseTargets.isEmpty())
        assertTrue(parsed.repLog.isEmpty())
        assertTrue(parsed.completedTargets.isEmpty())
    }

    @Test
    fun legacyV1_bareArray_stillParses_withEmptyExerciseLists() {
        val legacyJson = Gson().toJson(listOf(sampleRoute("old1")))
        val parsed = BackupData.parse(legacyJson)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.version)
        assertNull(parsed.buddyAppId)
        assertEquals("old1", parsed.routes.single().id)
        assertTrue(parsed.exerciseTargets.isEmpty())
        assertTrue(parsed.repLog.isEmpty())
        assertTrue(parsed.completedTargets.isEmpty())
    }

    @Test
    fun garbage_returnsNull() {
        assertNull(BackupData.parse("not json at all"))
        assertNull(BackupData.parse("{ this is : broken"))
    }

    @Test
    fun importTargets_mergesById_addsOnlyNew() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ExerciseTargetStorage.clearTargets(ctx)
        val a = sampleTarget("a"); val b = sampleTarget("b")
        assertEquals(2, ExerciseTargetStorage.importTargets(ctx, listOf(a, b)))
        // Re-importing the same ids adds nothing; a new id adds one.
        assertEquals(1, ExerciseTargetStorage.importTargets(ctx, listOf(a, b, sampleTarget("c"))))
        assertEquals(3, ExerciseTargetStorage.loadTargets(ctx).size)
        ExerciseTargetStorage.clearTargets(ctx)
    }

    @Test
    fun importRepLog_mergesById_addsOnlyNew() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ExerciseTargetStorage.clearRepLog(ctx)
        val r1 = sampleRep("r1"); val r2 = sampleRep("r2")
        assertEquals(2, ExerciseTargetStorage.importRepLog(ctx, listOf(r1, r2)))
        assertEquals(0, ExerciseTargetStorage.importRepLog(ctx, listOf(r1, r2)))
        assertEquals(2, ExerciseTargetStorage.loadRepLog(ctx).size)
        ExerciseTargetStorage.clearRepLog(ctx)
    }

    @Test
    fun importCompleted_mergesByIdAndNaturalKey() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ExerciseTargetStorage.clearCompleted(ctx)
        val c1 = sampleCompleted("c1")
        assertEquals(1, ExerciseTargetStorage.importCompleted(ctx, listOf(c1)))
        // Same id -> skip. Different id but same (targetId, periodKey) -> skip too.
        val dupKey = c1.copy(id = "c2")
        assertEquals(0, ExerciseTargetStorage.importCompleted(ctx, listOf(c1, dupKey)))
        assertEquals(1, ExerciseTargetStorage.loadCompleted(ctx).size)
        ExerciseTargetStorage.clearCompleted(ctx)
    }
}
