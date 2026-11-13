package com.rallyinreallife.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Voce del copilota: legge le note con TTS in italiano, come il co-pilota
 * che legge il libretto al pilota.
 */
class CoDriverTts(context: Context) {

    private var ready = false
    private var pending: String? = null

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.ITALIAN
            tts.setSpeechRate(1.15f)
            pending?.let { speak(it) }
            pending = null
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pace_note_${System.nanoTime()}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        ready = false
    }

    @Suppress("unused")
    private fun dummyListener(): UtteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {}
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {}
    }
}
