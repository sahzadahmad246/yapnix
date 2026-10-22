package com.yapnix.game.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.concurrent.thread

/** One AudioRecord owner. The exact same PCM is saved and decoded locally. */
class SharedMicrophone(private val model: Model, private val pcm: File, private val onFailure: () -> Unit) {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var decoder: Recognizer? = null
    private var listener: ((String) -> Unit)? = null
    @Volatile private var running = false
    private var worker: Thread? = null
    private var input: AudioRecord? = null
    private var generation = 0
    var startedAtUs: Long = 0
        private set
    @Volatile var failed = false
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val audio = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum, 6400)).build()
        input = audio
        check(audio.state == AudioRecord.STATE_INITIALIZED)
        startedAtUs = SystemClock.elapsedRealtimeNanos() / 1000L
        audio.startRecording(); check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        running = true
        worker = thread(name = "Yapnix shared microphone") {
            try {
                pcm.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1600)
                    while (running) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count <= 0) { if (running) error("Microphone read failed") else break }
                        check(count % 2 == 0) { "Incomplete microphone sample" }
                        output.write(buffer, 0, count)
                        synchronized(lock) {
                            val recognizer = decoder
                            val callback = listener
                            if (recognizer != null && callback != null) {
                                val final = recognizer.acceptWaveForm(buffer, count)
                                val words = JSONObject(if (final) recognizer.result else recognizer.partialResult)
                                    .optString(if (final) "text" else "partial").trim()
                                val token = generation
                                if (words.isNotEmpty()) main.post {
                                    synchronized(lock) { if (running && generation == token) callback(words) }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                if (running) { failed = true; main.post(onFailure) }
            }
        }
    }
    fun listen(callback: ((String) -> Unit)?) = synchronized(lock) {
        generation++
        listener = callback
        decoder?.close(); decoder = null
        if (callback != null) decoder = Recognizer(model, RATE.toFloat())
    }
    fun stop() {
        running = false
        runCatching { input?.stop() }
        try {
            worker?.join(2_000)
            check(worker?.isAlive != true) { "Microphone did not stop" }
        } finally {
            synchronized(lock) { generation++; listener = null; decoder?.close(); decoder = null }
            input?.release(); input = null
            main.removeCallbacksAndMessages(null)
        }
    }
    companion object { const val RATE = PcmClock.SAMPLE_RATE }
}
