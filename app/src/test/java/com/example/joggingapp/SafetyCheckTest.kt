package com.example.joggingapp

import com.example.joggingapp.SafetyCheck.Action
import com.example.joggingapp.SafetyCheck.Phase
import com.example.joggingapp.SafetyCheck.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCheckTest {
    private val stop = SafetyCheck.STOP_MS
    private val prompt = SafetyCheck.PROMPT_MS

    @Test fun `no prompt while moving`() {
        val (s, a) = SafetyCheck.next(State(), nowMs = stop - 1, lastMovementMs = 0, okAtMs = 0)
        assertEquals(Phase.IDLE, s.phase); assertEquals(Action.NONE, a)
    }

    @Test fun `prompt after standing still for the stop window`() {
        val (s, a) = SafetyCheck.next(State(), nowMs = stop, lastMovementMs = 0, okAtMs = 0)
        assertEquals(Phase.PROMPTING, s.phase); assertEquals(Action.SHOW_PROMPT, a)
    }

    @Test fun `alert sent when the prompt goes unanswered`() {
        val prompting = State(Phase.PROMPTING, promptAtMs = stop)
        val (s, a) = SafetyCheck.next(prompting, nowMs = stop + prompt, lastMovementMs = 0, okAtMs = 0)
        assertEquals(Phase.ALERTED, s.phase); assertEquals(Action.SEND_ALERT, a)
    }

    @Test fun `I'm OK cancels the prompt and restarts the stop timer`() {
        val prompting = State(Phase.PROMPTING, promptAtMs = stop)
        val ok = stop + 10_000
        val (s, a) = SafetyCheck.next(prompting, nowMs = ok + 1, lastMovementMs = 0, okAtMs = ok)
        assertEquals(Phase.IDLE, s.phase); assertEquals(Action.CLEAR_PROMPT, a)
        // Still standing: no new prompt until a full stop window after "I'm OK".
        assertEquals(Action.NONE, SafetyCheck.next(s, ok + stop - 1, 0, ok).second)
        assertEquals(Action.SHOW_PROMPT, SafetyCheck.next(s, ok + stop, 0, ok).second)
    }

    @Test fun `moving again during the prompt cancels it`() {
        val prompting = State(Phase.PROMPTING, promptAtMs = stop)
        val (_, a) = SafetyCheck.next(prompting, nowMs = stop + 5_000, lastMovementMs = stop + 4_000, okAtMs = 0)
        assertEquals(Action.CLEAR_PROMPT, a)
    }

    @Test fun `alert is not repeated until the user moves again`() {
        val alerted = State(Phase.ALERTED, promptAtMs = stop)
        assertEquals(Action.NONE, SafetyCheck.next(alerted, stop * 10, lastMovementMs = 0, okAtMs = 0).second)
        val (s, a) = SafetyCheck.next(alerted, stop * 10, lastMovementMs = stop * 9, okAtMs = 0)
        assertEquals(Phase.IDLE, s.phase); assertEquals(Action.CLEAR_PROMPT, a)
    }

    @Test fun `sms text includes name and map link`() {
        val text = SafetyCheck.smsText(stringsFor(Language.ENGLISH), "Mike", 51.5, -0.12)
        assertTrue(text.contains("Mike"))
        assertTrue(text.contains("https://maps.google.com/?q=51.500000,-0.120000"))
    }

    @Test fun `sms text without a fix says so`() {
        val en = stringsFor(Language.ENGLISH)
        assertTrue(SafetyCheck.smsText(en, "", null, null).contains(en.safetyNoLocation))
    }
}
