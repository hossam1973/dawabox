package com.dawabox.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Arabic text-to-speech. Used as the pillbox's "speaker" when no real
 * device is connected, and to read reminders aloud.
 */
object Speaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<String, (() -> Unit)?>>()
    private val callbacks = mutableMapOf<String, () -> Unit>()

    @Synchronized
    fun init(context: Context) {
        if (tts == null) tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status == TextToSpeech.SUCCESS) {
            val r = engine.setLanguage(Locale("ar"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
            engine.setSpeechRate(0.9f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { utteranceId?.let { callbacks.remove(it)?.invoke() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { utteranceId?.let { callbacks.remove(it)?.invoke() } }
            })
            ready = true
            val queued = pending.toList(); pending.clear()
            queued.forEach { (t, cb) -> speakNow(t, cb) }
        } else {
            val queued = pending.toList(); pending.clear()
            queued.forEach { it.second?.invoke() }
        }
    }

    /** Speaks [text]; [onDone] runs when speech ends (or fails). */
    fun say(context: Context, text: String, onDone: (() -> Unit)? = null) {
        init(context)
        if (ready) speakNow(text, onDone) else pending += text to onDone
    }

    private fun speakNow(text: String, onDone: (() -> Unit)?) {
        val id = UUID.randomUUID().toString()
        if (onDone != null) callbacks[id] = onDone
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun stop() { tts?.stop() }
}
