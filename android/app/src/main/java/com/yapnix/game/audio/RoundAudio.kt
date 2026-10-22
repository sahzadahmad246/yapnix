package com.yapnix.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.yapnix.game.R
import java.util.Locale

/** Tiny original sound effects and system TTS; neither opens the microphone. */
class RoundAudio(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var initialized = false
    private var closed = false
    private var pendingStart: (() -> Unit)? = null
    private var completion: (() -> Unit)? = null
    private var utterance = 0
    var muted: Boolean = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 20) }.getOrNull()
    private val loaded = mutableSetOf<Int>()
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build().apply {
        setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
    }
    private val tapSound = pool.load(context, R.raw.game_tap, 1)
    private val tickSound = pool.load(context, R.raw.game_tick, 1)
    private val startSound = pool.load(context, R.raw.game_start, 1)
    private val revealSound = pool.load(context, R.raw.game_reveal, 1)
    private val completeSound = pool.load(context, R.raw.game_complete, 1)
    private val engine = TextToSpeech(context.applicationContext) { status ->
        handler.post {
            if (!closed) {
                ready = status == TextToSpeech.SUCCESS
                initialized = true
                val start = pendingStart; pendingStart = null; start?.invoke()
            }
        }
    }
    init {
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
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
        if (muted || text.isBlank()) { done(); return }
        completion = done
        val id = ++utterance
        val start = start@{
            if (closed || id != utterance) return@start
            if (!ready || engine.setLanguage(Locale.forLanguageTag(language)) < 0) { finish(); return@start }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id.toString()) == TextToSpeech.ERROR) finish()
            handler.postDelayed({ if (id == utterance) { engine.stop(); finish() } }, 12_000)
        }
        if (initialized) start() else {
            pendingStart = start
            // Only a genuinely unresponsive TTS engine uses this fallback.
            handler.postDelayed({
                if (id == utterance && !initialized) { pendingStart = null; finish() }
            }, 2_500)
        }
    }
    private fun effect(sound: Int, fallback: Int = ToneGenerator.TONE_PROP_BEEP) {
        if (closed || muted) return
        if (sound in loaded) pool.play(sound, .65f, .65f, 1, 0, 1f)
        else runCatching { tone?.startTone(fallback, 45) }
    }
    fun click() = effect(tapSound)
    fun tick() = effect(tickSound)
    fun start() = effect(startSound, ToneGenerator.TONE_PROP_ACK)
    fun reveal() = effect(revealSound, ToneGenerator.TONE_PROP_ACK)
    fun complete() = effect(completeSound, ToneGenerator.TONE_PROP_ACK)
    fun result(correct: Boolean) { if (correct) reveal() else effect(tickSound) }
    fun stop() { utterance++; pendingStart = null; completion = null; engine.stop() }
    fun close() { closed = true; stop(); handler.removeCallbacksAndMessages(null); engine.shutdown(); pool.release(); tone?.release() }
}
