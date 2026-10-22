package com.yapnix.game.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Encodes only the game composition and microphone. Never captures the device display. */
class ScreenRecordingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private var running = false
    private var stopping = false
    private var destroyed = false
    private var completion: CaptureStatus? = null
    private var frameBitmap: Bitmap? = null
    private var frameCanvas: Canvas? = null

    // Accessed only on the encoder thread. The main-thread painter hands one frame over at a time.
    @Volatile private var recorder: MediaRecorder? = null
    private var frameIntervalMs = 1000L / RecordingQuality.profiles.first().frameRate
    private var renderer: EncoderSurface? = null
    private var output: File? = null
    @Volatile private var encoderStarted = false
    private var framesWritten = 0

    private val frame = object : Runnable {
        override fun run() {
            if (!running || stopping) return
            val painter = drawFrame
            val bitmap = frameBitmap
            val canvas = frameCanvas
            if (painter == null || bitmap == null || canvas == null) {
                main.postDelayed(this, frameIntervalMs)
                return
            }
            val startedAt = SystemClock.uptimeMillis()
            try {
                canvas.drawColor(Color.rgb(13, 21, 38))
                // Compose and camera state stay on the main thread. No encoder Surface canvas locks.
                val checkpoint = canvas.save()
                try { painter(canvas) } finally { canvas.restoreToCount(checkpoint) }
            } catch (error: Exception) {
                Log.e(TAG, "Could not compose video frame", error)
                finishRecording("Recording stopped. Please try again.")
                return
            }
            val timestamp = System.nanoTime()
            worker.post {
                try {
                    checkNotNull(renderer).render(bitmap, timestamp)
                    framesWritten++
                    // Do not let the painter overwrite the bitmap before GL has uploaded it.
                    main.postAtTime(this, maxOf(startedAt + frameIntervalMs, SystemClock.uptimeMillis()))
                } catch (error: Exception) {
                    Log.e(TAG, "Could not encode video frame", error)
                    main.post { finishRecording("Recording stopped. Please try again.") }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("Yapnix-video-encoder").apply { start() }
        worker = Handler(workerThread.looper)
        runningService = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || stopRequested) {
            if (stopping) return START_NOT_STICKY
            if (running) finishRecording() else {
                stopping = true
                completion = CaptureStatus()
                mutableStatus.value = CaptureStatus(finalizing = true)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (running || stopping) return START_NOT_STICKY
        try {
            check(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "Microphone permission missing"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Game video", NotificationManager.IMPORTANCE_LOW),
            )
            val stopIntent = android.app.PendingIntent.getService(this, 0,
                Intent(this, ScreenRecordingService::class.java).setAction(ACTION_STOP),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Recording your reaction")
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop", stopIntent).build())
                .build()
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION_ID, notification)
            running = true
            worker.post { prepareRecording() }
        } catch (error: Exception) {
            Log.e(TAG, "Could not start recording service", error)
            stopping = true
            completion = CaptureStatus(error = "Allow microphone access, then try recording again.")
            mutableStatus.value = CaptureStatus(finalizing = true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun prepareRecording() {
        var lastError: Exception? = null
        for (quality in RecordingQuality.profiles) {
            try {
                output = RecordingStore(this).newFile()
                @Suppress("DEPRECATION")
                val encoder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).also { recorder = it }
                encoder.setOnErrorListener { source, what, extra ->
                    main.post {
                        if (recorder === source && encoderStarted) {
                            Log.e(TAG, "MediaRecorder error $what / $extra")
                            finishRecording("Recording interrupted. Please try again.")
                        }
                    }
                }
                encoder.setAudioSource(MediaRecorder.AudioSource.MIC)
                encoder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                encoder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                encoder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                encoder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                encoder.setAudioSamplingRate(44100)
                encoder.setAudioEncodingBitRate(128000)
                encoder.setVideoSize(quality.width, quality.height)
                encoder.setVideoFrameRate(quality.frameRate)
                encoder.setVideoEncodingBitRate(quality.bitrate)
                encoder.setOutputFile(checkNotNull(output).absolutePath)
                encoder.prepare()
                renderer = EncoderSurface(encoder.surface, quality.width, quality.height)
                checkNotNull(renderer).prepare()
                encoder.start()
                encoderStarted = true
                frameIntervalMs = 1000L / quality.frameRate
                val bitmap = Bitmap.createBitmap(quality.width, quality.height, Bitmap.Config.ARGB_8888)
                main.post {
                    frameBitmap = bitmap
                    frameCanvas = Canvas(bitmap)
                    if (!stopping) {
                        mutableStatus.value = CaptureStatus(active = true)
                        main.post(frame)
                    }
                }
                Log.i(TAG, "Recording ${quality.width}x${quality.height} at ${quality.frameRate}fps")
                return
            } catch (error: Exception) {
                lastError = error
                Log.w(TAG, "Encoder profile ${quality.width}x${quality.height} unavailable", error)
                // Release all producer/encoder resources before trying the next HD profile.
                runCatching { renderer?.close() }
                renderer = null
                val encoder = recorder
                recorder = null
                if (encoderStarted) runCatching { encoder?.stop() }
                runCatching { encoder?.release() }
                encoderStarted = false
                output?.delete()
                output = null
            }
        }
        Log.e(TAG, "Could not prepare any HD video/audio encoder", lastError)
        main.post { finishRecording("Could not start HD video. Close other camera or audio apps and try again.") }
    }

    /** Main-thread entry; stopping and MP4 finalization never block the game UI. */
    private fun finishRecording(error: String? = null) {
        if (stopping) return
        stopping = true
        running = false
        main.removeCallbacks(frame)
        mutableStatus.value = CaptureStatus(finalizing = true)
        worker.post { finalizeRecording(error) }
    }

    private fun finalizeRecording(recordingError: String?) {
        var failure = recordingError
        var stopped = false
        val encoder = recorder
        if (encoderStarted && encoder != null) {
            try {
                encoder.stop()
                stopped = true
            } catch (error: Exception) {
                Log.e(TAG, "Could not finalize MP4 ($framesWritten video frames)", error)
                failure = if (framesWritten == 0) "Recording was too short. Play a round before saving."
                    else "Could not save video. Please try again."
            }
        }
        runCatching { renderer?.close() }.onFailure { Log.w(TAG, "Could not release recording GL surface", it) }
        renderer = null
        runCatching { encoder?.release() }.onFailure { Log.w(TAG, "Could not release recorder", it) }
        recorder = null
        encoderStarted = false
        val video = output
        var saved = false
        if (stopped && framesWritten > 0 && video != null) {
            saved = runCatching {
                requirePlayableTracks(video)
                check(RecordingStore(this).finish(video)) { "Could not move completed recording" }
                true
            }.onFailure { Log.e(TAG, "Invalid or unsaved recording", it) }.getOrDefault(false)
        }
        if (!saved) {
            video?.delete()
            if (failure == null) failure = if (framesWritten == 0) "Recording was too short. Play a round before saving."
                else "Could not save video. Please try again."
        }
        output = null
        framesWritten = 0
        main.post {
            frameCanvas = null
            frameBitmap?.recycle()
            frameBitmap = null
            completion = CaptureStatus(saved = saved, error = failure)
            if (destroyed) mutableStatus.value = checkNotNull(completion)
            else {
                // Keep finalizing until this service is gone. A new Start must get a fresh worker.
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun requirePlayableTracks(file: File) {
        check(file.length() > 0) { "Empty recording" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var hasAudio = false
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                val format = extractor.getTrackFormat(index)
                check(format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 0) {
                    "Recording track has no duration: $mime"
                }
                extractor.selectTrack(index)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val hasSample = extractor.sampleTime >= 0
                if (mime.startsWith("video/")) hasVideo = hasSample
                else hasAudio = hasSample
                extractor.unselectTrack(index)
            }
            check(hasVideo && hasAudio) { "Recording requires playable video and microphone tracks" }
        } finally { extractor.release() }
    }

    override fun onDestroy() {
        destroyed = true
        if (runningService === this) runningService = null
        if (!stopping) finishRecording()
        completion?.let { mutableStatus.value = it }
        workerThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "YapnixRecording"
        private const val CHANNEL = "game-video"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "stop"
        @Volatile private var runningService: ScreenRecordingService? = null
        @Volatile private var stopRequested = false
        var drawFrame: ((Canvas) -> Unit)? = null
        private val mutableStatus = MutableStateFlow(CaptureStatus())
        val status = mutableStatus.asStateFlow()

        fun start(context: Context) {
            if (mutableStatus.value.active || mutableStatus.value.starting || mutableStatus.value.finalizing) return
            stopRequested = false
            mutableStatus.value = CaptureStatus(starting = true)
            try { ContextCompat.startForegroundService(context, Intent(context, ScreenRecordingService::class.java)) }
            catch (error: Exception) {
                Log.e(TAG, "Could not request recording service", error)
                mutableStatus.value = CaptureStatus(error = "Could not start recording. Please try again.")
            }
        }

        fun stop(context: Context) {
            if (!mutableStatus.value.active && !mutableStatus.value.starting) return
            stopRequested = true
            // No new background-service launch is needed when the activity leaves the foreground.
            runningService?.let { service -> service.main.post { if (service.running) service.finishRecording() } }
        }
    }
}

data class CaptureStatus(
    val starting: Boolean = false,
    val active: Boolean = false,
    val finalizing: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)
