package com.yapnix.game.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Video-only capture leaves the microphone exclusively available to SpeechRecognizer. */
class ScreenRecordingService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var started = false
    private var finishing = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { finishRecording(); stopSelf() }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finishRecording(); stopSelf(); return START_NOT_STICKY }
        if (started || projection != null) return START_NOT_STICKY
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Game recording", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, ScreenRecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Recording your Yapnix game").setContentText("Screen + camera · video only · tap Stop to finish")
                .setOngoing(true).addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(1001, notification)
            val consent = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("consent", Intent::class.java)
                else @Suppress("DEPRECATION") (intent?.getParcelableExtra("consent") as? Intent)
            checkNotNull(consent) { "Screen recording consent is missing" }
            val capture = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent!!.getIntExtra("resultCode", 0), consent)
            projection = capture
            capture.registerCallback(callback, Handler(Looper.getMainLooper()))
            val metrics = resources.displayMetrics
            val scale = minOf(1f, 720f / metrics.widthPixels)
            val width = ((metrics.widthPixels * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val height = ((metrics.heightPixels * scale).toInt() / 2 * 2).coerceAtLeast(2)
            val output = RecordingStore(this).newFile().also { file = it }
            @Suppress("DEPRECATION")
            val encoder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).also { recorder = it }
            encoder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            encoder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            encoder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            encoder.setVideoSize(width, height)
            encoder.setVideoFrameRate(24)
            encoder.setVideoEncodingBitRate(2_500_000)
            encoder.setOutputFile(output.absolutePath)
            encoder.prepare()
            display = capture.createVirtualDisplay("Yapnix game", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, encoder.surface, null, null)
            encoder.start(); started = true
            mutableStatus.value = CaptureStatus(active = true)
        } catch (_: Exception) {
            finishRecording()
            mutableStatus.value = CaptureStatus(error = "Screen recording could not start. You can play without recording.")
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun finishRecording() {
        if (finishing) return
        finishing = true
        val hadStarted = started
        val stopped = if (hadStarted) runCatching { recorder?.stop() }.isSuccess else false
        started = false
        display?.release(); display = null
        recorder?.release(); recorder = null
        projection?.unregisterCallback(callback)
        projection?.stop(); projection = null
        val output = file; file = null
        val saved = stopped && output != null && output.length() > 0 && RecordingStore(this).finish(output)
        if (!saved) output?.delete()
        mutableStatus.value = CaptureStatus(saved = saved, error = if (hadStarted && !saved) "Recording could not be finalized. Your game progress is safe." else null)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { finishRecording(); super.onDestroy() }
    companion object {
        private const val CHANNEL = "yapnix_screen_recording"
        private const val ACTION_STOP = "com.yapnix.game.STOP_RECORDING"
        private val mutableStatus = MutableStateFlow(CaptureStatus())
        val status = mutableStatus.asStateFlow()
        fun start(context: Context, resultCode: Int, consent: Intent) {
            mutableStatus.value = CaptureStatus(starting = true)
            try { ContextCompat.startForegroundService(context, Intent(context, ScreenRecordingService::class.java).putExtra("resultCode", resultCode).putExtra("consent", consent)) }
            catch (_: Exception) { mutableStatus.value = CaptureStatus(error = "Screen recording could not start. Try playing without recording.") }
        }
        fun stop(context: Context) { context.stopService(Intent(context, ScreenRecordingService::class.java)) }
    }
}
data class CaptureStatus(val starting: Boolean = false, val active: Boolean = false, val saved: Boolean = false, val error: String? = null)
