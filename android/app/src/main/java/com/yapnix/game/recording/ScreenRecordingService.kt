package com.yapnix.game.recording

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.media.MediaRecorder
import android.os.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Encodes only the game composition. No display capture or MediaProjection access. */
class ScreenRecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var file: java.io.File? = null
    private val handler = Handler(Looper.getMainLooper())
    private val frame = object : Runnable {
        override fun run() {
            val encoder = recorder ?: return
            val render = drawFrame
            if (render == null) { handler.postDelayed(this, 42); return }
            try {
                val canvas = encoder.surface.lockHardwareCanvas()
                try { canvas.drawColor(android.graphics.Color.rgb(13, 21, 38)); render(canvas) }
                finally { encoder.surface.unlockCanvasAndPost(canvas) }
                handler.postDelayed(this, 42)
            } catch (_: Exception) { finishRecording() }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finishRecording(); return START_NOT_STICKY }
        if (recorder != null) return START_NOT_STICKY
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("game-video", "Game video", NotificationManager.IMPORTANCE_LOW))
            val notification = Notification.Builder(this, "game-video").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("Recording your reaction").setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 30) startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1001, notification)
            val output = RecordingStore(this).newFile().also { file = it }
            @Suppress("DEPRECATION")
            val encoder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).also { recorder = it }
            encoder.setAudioSource(MediaRecorder.AudioSource.MIC)
            encoder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            encoder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            encoder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            encoder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            encoder.setAudioSamplingRate(44100)
            encoder.setAudioEncodingBitRate(128000)
            encoder.setVideoSize(720, 1280)
            encoder.setVideoFrameRate(24)
            encoder.setVideoEncodingBitRate(4000000)
            encoder.setOutputFile(output.absolutePath)
            encoder.prepare(); encoder.start()
            mutableStatus.value = CaptureStatus(active = true)
            handler.post(frame)
        } catch (_: Exception) { finishRecording(); mutableStatus.value = CaptureStatus(error = "Could not start video. Try again or play without recording.") }
        return START_NOT_STICKY
    }
    private fun finishRecording() {
        handler.removeCallbacks(frame)
        val encoder = recorder ?: return
        recorder = null
        mutableStatus.value = CaptureStatus(finalizing = true)
        val saved = runCatching { encoder.stop(); check(RecordingStore(this).finish(checkNotNull(file))) }.isSuccess
        encoder.release()
        if (!saved) file?.delete()
        file = null
        mutableStatus.value = CaptureStatus(saved = saved, error = if (saved) null else "Could not save recording")
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { finishRecording(); super.onDestroy() }
    companion object {
        var drawFrame: ((Canvas) -> Unit)? = null
        private val mutableStatus = MutableStateFlow(CaptureStatus())
        val status = mutableStatus.asStateFlow()
        fun start(context: Context) {
            if (mutableStatus.value.active || mutableStatus.value.starting || mutableStatus.value.finalizing) return
            mutableStatus.value = CaptureStatus(starting = true)
            try { ContextCompat.startForegroundService(context, Intent(context, ScreenRecordingService::class.java)) }
            catch (_: Exception) { mutableStatus.value = CaptureStatus(error = "Could not start recording") }
        }
        fun stop(context: Context) {
            if (mutableStatus.value.active || mutableStatus.value.starting) context.startService(Intent(context, ScreenRecordingService::class.java).setAction("stop"))
        }
    }
}
data class CaptureStatus(val starting: Boolean = false, val active: Boolean = false, val finalizing: Boolean = false, val saved: Boolean = false, val error: String? = null)
