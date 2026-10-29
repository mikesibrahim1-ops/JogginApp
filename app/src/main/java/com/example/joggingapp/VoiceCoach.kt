package com.example.joggingapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** jog_prefs key for the Options-pane "Voice coaching" switch (default off). */
const val PREF_VOICE_COACH = "voice_coach"

/**
 * Pure cue decisions for voice coaching (JVM-tested in CoachCuesTest): which kilometre was
 * just completed, and the spoken sentence for it.
 */
object CoachCues {
    /** The whole kilometre crossed going from [prevM] to [newM] metres, or null if none. */
    fun kmCrossed(prevM: Double, newM: Double): Int? {
        val before = (prevM / 1000.0).toInt()
        val after = (newM / 1000.0).toInt()
        return if (after > before && after >= 1) after else null
    }

    /** "6 minutes 10 seconds" / "1 hour 5 minutes" in the app language. */
    fun spokenDuration(S: AppStrings, totalSec: Long): String {
        val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
        return if (h > 0) S.coachHoursMinutes(h.toInt(), m.toInt()) else S.coachMinutesSeconds(m.toInt(), s.toInt())
    }

    /** The full cue for completing kilometre [km] after [elapsedSec], with [splitSec] for that km. */
    fun kmCue(S: AppStrings, km: Int, elapsedSec: Long, splitSec: Long): String =
        S.coachKmCue(km, spokenDuration(S, elapsedSec), spokenDuration(S, splitSec))
}

/**
 * Thin TextToSpeech wrapper used by ForegroundLocationService. Speaks in the app language
 * when the engine supports it, else falls back to English text + voice. Ducks other audio
 * (music) for the length of each cue via transient audio focus.
 */
class VoiceCoach(private val context: Context, private val language: Language) {
    private var tts: TextToSpeech? = null
    private var ready = false
    /** Strings actually used for cues — English if the engine lacks the app language. */
    var strings: AppStrings = stringsFor(language); private set
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build()
        else null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                AppLogger.log(context, LogCategory.SERVICE, "Voice coach: TTS init failed ($status)")
                return@TextToSpeech
            }
            val wanted = if (language == Language.GREEK) Locale("el", "GR") else Locale.UK
            val result = engine.setLanguage(wanted)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.UK)
                strings = stringsFor(Language.ENGLISH)
                AppLogger.log(context, LogCategory.SERVICE, "Voice coach: ${wanted.language} voice unavailable — using English")
            }
            engine.setAudioAttributes(attrs)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = abandonFocus()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = abandonFocus()
            })
            ready = true
        }
    }

    fun speak(text: String) {
        val engine = tts
        if (!ready || engine == null) {
            AppLogger.log(context, LogCategory.SERVICE, "Voice coach: cue skipped (TTS not ready)")
            return
        }
        requestFocus()
        engine.speak(text, TextToSpeech.QUEUE_ADD, null, "cue-${System.currentTimeMillis()}")
    }

    fun shutdown() {
        abandonFocus()
        tts?.stop(); tts?.shutdown()
        tts = null; ready = false
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { audioManager.requestAudioFocus(it) }
        else @Suppress("DEPRECATION") audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        else @Suppress("DEPRECATION") audioManager.abandonAudioFocus(null)
    }
}
