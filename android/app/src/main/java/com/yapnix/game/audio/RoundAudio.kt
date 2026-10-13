package com.yapnix.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speech output never takes ownership of the microphone. Callbacks always return on main. */
class RoundAudio(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var closed = false
    private var completion: (() -> Unit)? = null
    private var utterance = 0
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 25) }.getOrNull()
    private val engine = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }
    init {
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { handler.post { if (id == utterance.toString()) finish() } }
            @Deprecated("Platform callback")
            override fun onError(id: String?) { onDone(id) }
        })
    }
    private fun finish() { val callback = completion; completion = null; callback?.invoke() }
    fun speak(text: String, language: String, done: () -> Unit) {
        stop()
        completion = done
        val id = ++utterance
        fun start() {
            if (closed || id != utterance) return
            if (!ready || engine.setLanguage(Locale.forLanguageTag(language)) < 0) { finish(); return }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id.toString()) == TextToSpeech.ERROR) finish()
            // A broken speech engine must never block the round forever.
            handler.postDelayed({ if (id == utterance) { engine.stop(); finish() } }, 12_000)
        }
        if (ready) start() else handler.postDelayed({ start() }, 1_500)
    }
    fun tick() { runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 35) } }
    fun result(correct: Boolean) { runCatching { tone?.startTone(if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 150) } }
    fun stop() { utterance++; completion = null; engine.stop() }
    fun close() { closed = true; stop(); handler.removeCallbacksAndMessages(null); engine.shutdown(); tone?.release() }
}
