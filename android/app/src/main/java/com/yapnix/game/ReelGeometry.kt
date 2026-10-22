package com.yapnix.game

/** Coordinate space shared by the live reel and encoder, independent of Android views. */
internal object ReelGeometry {
    const val WIDTH = 720f
    const val HEIGHT = 1280f
    const val LEFT = 48f
    const val RIGHT = 672f
    const val IMAGE_TOP = 150f
    const val IMAGE_BOTTOM = 774f
    const val CAMERA_TOP = 886f
    const val CAMERA_BOTTOM = 1238f
    const val PANE_WIDTH = RIGHT - LEFT
    const val PANE_HEIGHT = CAMERA_BOTTOM - CAMERA_TOP

    fun maxSize(round: Boolean): Float = if (round) .56f else .75f
    fun ratio(round: Boolean): Float = if (round) 1f else .75f

    fun cameraRect(x: Float, y: Float, size: Float, round: Boolean): ReelRect {
        val width = PANE_WIDTH * size.coerceIn(.30f, maxSize(round))
        val height = width * ratio(round)
        val left = LEFT + x.coerceIn(0f, 1f) * (PANE_WIDTH - width)
        val top = CAMERA_TOP + y.coerceIn(0f, 1f) * (PANE_HEIGHT - height).coerceAtLeast(0f)
        return ReelRect(left, top, left + width, top + height)
    }
}

internal data class ReelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}
