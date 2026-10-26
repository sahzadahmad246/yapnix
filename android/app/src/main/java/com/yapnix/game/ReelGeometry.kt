package com.yapnix.game

/** Shared positions keep the live challenge and exported reaction aligned. */
internal object ReelGeometry {
    const val WIDTH = 720f
    const val HEIGHT = 1280f
    const val STAGE_HEIGHT = 800f
    const val LEFT = 48f
    const val RIGHT = 672f
    const val IMAGE_TOP = 140f
    const val IMAGE_SIZE = 296f
    const val IMAGE_BOTTOM = IMAGE_TOP + IMAGE_SIZE
    const val CAMERA_LEFT = 376f
    const val CAMERA_TOP = IMAGE_TOP
    const val CAMERA_BOTTOM = 666f
    const val PANE_WIDTH = RIGHT - CAMERA_LEFT
    const val PANE_HEIGHT = CAMERA_BOTTOM - CAMERA_TOP

    fun cameraRect() = ReelRect(CAMERA_LEFT, CAMERA_TOP, RIGHT, CAMERA_BOTTOM)
}

internal data class ReelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}
