package com.yapnix.game.recording

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * The visible preview and exported reaction use the same crop and mirror setting.
 * Frames belong to this component: consume on the main thread and do not recycle them.
 */
@Composable
fun ReactionCamera(
    modifier: Modifier,
    onError: (String) -> Unit,
    onFrame: (Bitmap?) -> Unit = {},
    frontCamera: Boolean = true,
    mirrored: Boolean = true,
    onReady: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val view = remember(context) {
        PreviewView(context).apply {
            // TextureView supports clipped, movable camera overlays and bitmap capture.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val error = rememberUpdatedState(onError)
    val frame = rememberUpdatedState(onFrame)
    val ready = rememberUpdatedState(onReady)
    var streaming by remember(view) { mutableStateOf(false) }
    // CameraX mirrors front-camera previews itself; apply only the additional flip.
    val flip = frontCamera != mirrored
    AndroidView(factory = { view }, modifier = modifier, update = { it.scaleX = if (flip) -1f else 1f })

    DisposableEffect(view, lifecycle, frontCamera) {
        ready.value(false)
        frame.value(null)
        val observer = Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
            if (!streaming) ready.value(false)
        }
        view.previewStreamState.observe(lifecycle, observer)
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        future.addListener({
            if (!disposed) try {
                val camera = future.get().also { provider = it }
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                check(camera.hasCamera(selector)) { "Camera unavailable" }
                @Suppress("DEPRECATION")
                val useCase = Preview.Builder().setTargetResolution(Size(640, 480)).build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                    preview = it
                }
                camera.bindToLifecycle(lifecycle, selector, useCase)
            } catch (_: Exception) {
                ready.value(false)
                error.value("Camera unavailable. Check camera access and try again.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            streaming = false
            view.previewStreamState.removeObserver(observer)
            ready.value(false)
            frame.value(null)
            // Unbind only this component's preview, leaving other CameraX owners alone.
            preview?.let { provider?.unbind(it) }
        }
    }

    LaunchedEffect(view, streaming, flip) {
        if (!streaming) return@LaunchedEffect
        var output: Bitmap? = null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var firstFrame = true
        try {
            while (coroutineContext.isActive) {
                // PreviewView applies camera rotation and center crop to this snapshot.
                // Recycle that short-lived snapshot; keep one bounded output for the encoder.
                val snapshot = runCatching { view.bitmap }.getOrNull()
                if (snapshot != null) {
                    try {
                        val scale = minOf(1f, 480f / maxOf(snapshot.width, snapshot.height))
                        val width = (snapshot.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (snapshot.height * scale).roundToInt().coerceAtLeast(1)
                        val previous = output
                        val target = if (previous != null && previous.width == width && previous.height == height) previous
                        else Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { output = it }
                        val canvas = Canvas(target)
                        if (flip) canvas.scale(-1f, 1f, width / 2f, height / 2f)
                        canvas.drawBitmap(snapshot, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
                        frame.value(target)
                        if (target !== previous) previous?.recycle()
                        if (firstFrame) {
                            ready.value(true)
                            firstFrame = false
                        }
                    } finally {
                        snapshot.recycle()
                    }
                }
                delay(83)
            }
        } finally {
            frame.value(null)
            output?.recycle()
        }
    }
}
