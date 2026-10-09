package com.yapnix.game.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/** The game engine has no dependency on camera, audio capture or video output. */
@SuppressLint("MissingPermission")
@Composable
fun ReactionCamera(modifier: Modifier, onSaved: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val latestSaved = rememberUpdatedState(onSaved)
    val latestError = rememberUpdatedState(onError)
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(context, lifecycle, view) {
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        val store = RecordingStore(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var recording: Recording? = null
        var preview: Preview? = null
        var capture: VideoCapture<Recorder>? = null
        future.addListener({
            if (!disposed) try {
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                val camera = future.get().also { provider = it }
                val selector = if (camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val newPreview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider); preview = it }
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.fromOrderedList(
                    listOf(Quality.HD, Quality.SD), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )).build()
                val newCapture = VideoCapture.withOutput(recorder).also { capture = it }
                camera.bindToLifecycle(lifecycle, selector, newPreview, newCapture)
                val file = store.newFile()
                recording = recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                    .withAudioEnabled().start(executor) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (event.hasError()) {
                                file.delete() // Incomplete output is not a saved recording.
                                latestError.value("Recording could not be saved. Your game progress is safe.")
                            } else if (store.finish(file)) latestSaved.value()
                            else latestError.value("Recording could not be saved. Your game progress is safe.")
                            if (disposed) camera.unbind(newPreview, newCapture)
                        }
                    }
            } catch (_: Exception) {
                latestError.value("Camera recording is unavailable. You can keep playing.")
                preview?.let { provider?.unbind(it) }
                capture?.let { provider?.unbind(it) }
            }
        }, executor)
        onDispose {
            disposed = true
            if (recording != null) recording?.stop()
            else {
                preview?.let { provider?.unbind(it) }
                capture?.let { provider?.unbind(it) }
            }
        }
    }
}
