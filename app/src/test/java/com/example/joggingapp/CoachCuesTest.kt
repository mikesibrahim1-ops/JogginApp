package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachCuesTest {
    private val en = stringsFor(Language.ENGLISH)
    private val el = stringsFor(Language.GREEK)

    @Test fun `no cue before the first kilometre`() {
        assertNull(CoachCues.kmCrossed(0.0, 999.9))
    }

    @Test fun `cue on crossing each whole kilometre`() {
        assertEquals(1, CoachCues.kmCrossed(990.0, 1004.0))
        assertEquals(3, CoachCues.kmCrossed(2995.0, 3010.0))
    }

    @Test fun `no repeat cue within the same kilometre`() {
        assertNull(CoachCues.kmCrossed(1004.0, 1500.0))
    }

    @Test fun `a jump over two kilometres announces the latest`() {
        assertEquals(3, CoachCues.kmCrossed(1900.0, 3100.0))
    }

    @Test fun `spoken durations read naturally`() {
        assertEquals("6 minutes 10 seconds", CoachCues.spokenDuration(en, 370))
        assertEquals("1 minute", CoachCues.spokenDuration(en, 60))
        assertEquals("45 seconds", CoachCues.spokenDuration(en, 45))
        assertEquals("1 hour 5 minutes", CoachCues.spokenDuration(en, 3900))
    }

    @Test fun `full english cue`() {
        assertEquals("2 kilometres. Time 12 minutes 30 seconds. Last kilometre 6 minutes 5 seconds.",
            CoachCues.kmCue(en, 2, 750, 365))
    }

    @Test fun `greek cue uses greek words`() {
        assertEquals("1 χιλιόμετρο. Χρόνος 6 λεπτά. Τελευταίο χιλιόμετρο 6 λεπτά.",
            CoachCues.kmCue(el, 1, 360, 360))
    }
}
