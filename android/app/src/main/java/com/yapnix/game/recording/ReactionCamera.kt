package com.yapnix.game.recording

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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

/** TextureView preview is part of the screen recording. It never opens the microphone. */
@Composable
fun ReactionCamera(modifier: Modifier, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val error = rememberUpdatedState(onError)
    AndroidView(factory = { view }, modifier = modifier)
    DisposableEffect(context, lifecycle, view) {
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        future.addListener({
            if (!disposed) try {
                val camera = future.get().also { provider = it }
                check(camera.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) { "Front camera unavailable" }
                val useCase = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider); preview = it }
                camera.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
            } catch (_: Exception) { error.value("Camera preview is unavailable. Your screen recording can continue.") }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; preview?.let { provider?.unbind(it) } }
    }
}
