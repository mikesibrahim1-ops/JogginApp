package com.example.joggingapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [UpdateChecker.isNewerVersion] — the semantic-version comparison
 * that decides whether a GitHub release is newer than the installed build. No Android
 * deps (the function is pure), mirroring the ExerciseTargetsTest pattern.
 */
class UpdateCheckerTest {

    @Test fun newerPatch_isNewer() {
        assertTrue(UpdateChecker.isNewerVersion("1.47", "1.46"))
    }

    @Test fun sameVersion_isNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.46", "1.46"))
    }

    @Test fun numericNotLexical_1_10_greaterThan_1_9() {
        assertTrue(UpdateChecker.isNewerVersion("1.10", "1.9"))
    }

    @Test fun majorBump_2_0_greaterThan_1_99() {
        assertTrue(UpdateChecker.isNewerVersion("2.0", "1.99"))
    }

    @Test fun olderVersion_isNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.45", "1.46"))
    }

    @Test fun leadingV_isTolerated() {
        assertTrue(UpdateChecker.isNewerVersion("V1.47", "1.46"))
        assertFalse(UpdateChecker.isNewerVersion("v1.46", "V1.46"))
    }

    @Test fun differingPartCounts_compareNumerically() {
        assertTrue(UpdateChecker.isNewerVersion("1.47.1", "1.47"))
        assertFalse(UpdateChecker.isNewerVersion("1.47", "1.47.0"))
    }

    @Test fun malformedInput_isGracefulFalse() {
        assertFalse(UpdateChecker.isNewerVersion("abc", "1.46"))
        assertFalse(UpdateChecker.isNewerVersion("1.46", "xyz"))
        assertFalse(UpdateChecker.isNewerVersion("", "1.46"))
        assertFalse(UpdateChecker.isNewerVersion("1.x", "1.46"))
    }
}
