package com.yapnix.game.recording

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Preview and exported frames share the same sensor crop, rotation and mirror setting.
 * Frames belong to this component: consume on the main thread and do not retain or recycle
 * a replaced frame. A worker can reuse it only after the main-thread replacement callback.
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
            // TextureView follows the clipped, movable reaction pane.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val error = rememberUpdatedState(onError)
    val frame = rememberUpdatedState(onFrame)
    val ready = rememberUpdatedState(onReady)
    val mirror = remember { AtomicBoolean(mirrored) }
    SideEffect { mirror.set(mirrored) }
    // CameraX already mirrors its front preview. Analysis buffers are never mirrored.
    val extraPreviewFlip = frontCamera != mirrored
    AndroidView(factory = { view }, modifier = modifier,
        update = { it.scaleX = if (extraPreviewFlip) -1f else 1f })

    DisposableEffect(view, lifecycle, frontCamera) {
        var disposed = false
        var previewStreaming = false
        var hasHdFrame = false
        ready.value(false)
        frame.value(null)
        val main = Handler(Looper.getMainLooper())
        val executor = Executors.newSingleThreadExecutor { Thread(it, "Yapnix-camera-frames") }
        val pipeline = HdReactionFrames(main, mirror, onFrame = { bitmap ->
            if (!disposed) {
                frame.value(bitmap)
                hasHdFrame = true
                ready.value(previewStreaming)
            }
        }, onError = {
            if (!disposed) {
                ready.value(false)
                error.value("Couldn't read camera frames. Turn the camera off and on to retry.")
            }
        })
        val observer = Observer<PreviewView.StreamState> { state ->
            previewStreaming = state == PreviewView.StreamState.STREAMING
            ready.value(previewStreaming && hasHdFrame)
        }
        view.previewStreamState.observe(lifecycle, observer)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var useCases = emptyArray<UseCase>()
        future.addListener({
            if (!disposed) try {
                val camera = future.get().also { provider = it }
                val selector = if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                check(camera.hasCamera(selector)) { "Camera unavailable" }
                val rotation = view.display?.rotation ?: Surface.ROTATION_0
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .setResolutionFilter { sizes, _ ->
                        // Avoid unexpectedly decoding 4K frames on unusual cameras.
                        sizes.filter { it.width.toLong() * it.height <= 1920L * 1080 }
                            .ifEmpty { sizes.minByOrNull { it.width.toLong() * it.height }?.let(::listOf).orEmpty() }
                    }.build()
                val preview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation)
                    .build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val images = ImageAnalysis.Builder().setResolutionSelector(resolution).setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setOutputImageRotationEnabled(false)
                    .build().also { analysis = it; it.setAnalyzer(executor, pipeline) }
                useCases = arrayOf(preview, images)
                // The fixed portrait viewport retains an HD source when the on-screen pane
                // is small. Both PreviewView and the exporter center-crop this same source
                // again for the user's rectangular or circular reaction shape.
                val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(images)
                    .setViewPort(ViewPort.Builder(Rational(9, 16), rotation).setScaleType(ViewPort.FILL_CENTER).build())
                    .build()
                camera.bindToLifecycle(lifecycle, selector, group)
            } catch (_: Exception) {
                ready.value(false)
                error.value("Camera unavailable. Check camera access and try again.")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            view.previewStreamState.removeObserver(observer)
            ready.value(false)
            // Clear the consumer before releasing a displayed buffer on the main thread.
            frame.value(null)
            pipeline.closeOnMain()
            analysis?.clearAnalyzer()
            provider?.unbind(*useCases)
            // Queued analyses see closed=true and only close their ImageProxy. Worker-owned
            // conversion memory is released after any current conversion, without blocking UI.
            executor.execute { pipeline.releaseWorker() }
            executor.shutdown()
        }
    }
}

/** Two output buffers alternate worker/main ownership; there is at most one pending delivery. */
private class HdReactionFrames(
    private val main: Handler,
    private val mirrored: AtomicBoolean,
    private val onFrame: (Bitmap) -> Unit,
    private val onError: () -> Unit,
) : ImageAnalysis.Analyzer {
    private class Frame(val bitmap: Bitmap) { val canvas = Canvas(bitmap) }
    private val closed = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)
    private val lock = Any()
    private val available = ArrayDeque<Frame>(2)
    private var allocated = 0
    private var displayed: Frame? = null // Main thread only.
    private var pixels: Bitmap? = null // Worker thread only.
    private var packed: ByteBuffer? = null // Reused only when camera rows have padding.
    private val transform = Matrix()
    private val crop = RectF()
    private val rotatedCrop = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var consecutiveFailures = 0

    override fun analyze(image: ImageProxy) {
        if (closed.get() || !pending.compareAndSet(false, true)) { image.close(); return }
        var output: Frame? = null
        var posted = false
        try {
            if (closed.get()) return
            val source = copyRgba(image)
            crop.set(image.cropRect)
            check(crop.width() > 0f && crop.height() > 0f) { "Empty camera crop" }
            transform.setRotate(image.imageInfo.rotationDegrees.toFloat())
            transform.mapRect(rotatedCrop, crop)
            val scale = minOf(1f, 1280f / maxOf(rotatedCrop.width(), rotatedCrop.height()))
            val width = (rotatedCrop.width() * scale).roundToInt().coerceAtLeast(1)
            val height = (rotatedCrop.height() * scale).roundToInt().coerceAtLeast(1)
            val target = acquire(width, height) ?: return
            output = target
            transform.postTranslate(-rotatedCrop.left, -rotatedCrop.top)
            transform.postScale(width / rotatedCrop.width(), height / rotatedCrop.height())
            if (mirrored.get()) transform.postScale(-1f, 1f, width / 2f, height / 2f)
            target.canvas.drawColor(Color.BLACK)
            target.canvas.drawBitmap(source, transform, paint)
            consecutiveFailures = 0
            if (closed.get()) return
            posted = main.post {
                try {
                    if (closed.get()) release(target)
                    else {
                        val previous = displayed
                        displayed = target
                        onFrame(target.bitmap)
                        previous?.let(::release)
                    }
                } finally { pending.set(false) }
            }
        } catch (_: Exception) {
            consecutiveFailures++
            if (consecutiveFailures == 3) main.post { if (!closed.get()) onError() }
        } finally {
            image.close()
            if (!posted) { output?.let(::release); pending.set(false) }
        }
    }

    /** CameraX RGBA is R,G,B,A bytes, also Bitmap.ARGB_8888's native byte layout. */
    private fun copyRgba(image: ImageProxy): Bitmap {
        val previous = pixels
        val bitmap = if (previous != null && previous.width == image.width && previous.height == image.height) previous
        else Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also {
            it.setHasAlpha(false)
            pixels = it
            previous?.recycle()
        }
        val plane = image.planes.first()
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val rowBytes = image.width * 4
        check(plane.pixelStride >= 4 && plane.rowStride >= (image.width - 1L) * plane.pixelStride + 4) {
            "Invalid camera pixel stride"
        }
        val lastPixel = (image.height - 1L) * plane.rowStride + (image.width - 1L) * plane.pixelStride + 4
        check(lastPixel <= buffer.limit()) { "Incomplete camera buffer" }
        if (plane.pixelStride == 4 && plane.rowStride == rowBytes) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            val required = rowBytes * image.height
            val compact = packed?.takeIf { it.capacity() >= required }
                ?: ByteBuffer.allocateDirect(required).also { packed = it }
            compact.clear()
            for (row in 0 until image.height) {
                val start = row * plane.rowStride
                if (plane.pixelStride == 4) {
                    buffer.limit(start + rowBytes)
                    buffer.position(start)
                    compact.put(buffer)
                    buffer.limit(plane.buffer.limit())
                } else {
                    for (column in 0 until image.width) {
                        val offset = start + column * plane.pixelStride
                        compact.put(buffer.get(offset)).put(buffer.get(offset + 1))
                            .put(buffer.get(offset + 2)).put(buffer.get(offset + 3))
                    }
                }
            }
            compact.flip()
            bitmap.copyPixelsFromBuffer(compact)
        }
        return bitmap
    }

    private fun acquire(width: Int, height: Int): Frame? = synchronized(lock) {
        if (closed.get()) return@synchronized null
        while (available.isNotEmpty()) {
            val next = available.removeFirst()
            if (next.bitmap.width == width && next.bitmap.height == height) return@synchronized next
            next.bitmap.recycle()
            allocated--
        }
        if (allocated >= 2) return@synchronized null
        Frame(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) })
            .also { allocated++ }
    }

    private fun release(frame: Frame) {
        synchronized(lock) {
            if (closed.get()) { frame.bitmap.recycle(); allocated-- } else available.addLast(frame)
        }
    }

    /** Must run on main after the consumer has received onFrame(null). */
    fun closeOnMain() {
        closed.set(true)
        displayed?.let(::release)
        displayed = null
        synchronized(lock) {
            while (available.isNotEmpty()) { available.removeFirst().bitmap.recycle(); allocated-- }
        }
    }

    /** Must run on the same executor as analyze, after closeOnMain. */
    fun releaseWorker() {
        pixels?.recycle()
        pixels = null
        packed = null
    }
}
