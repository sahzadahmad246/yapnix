package com.yapnix.game.recording

data class PortraitVideoQuality(val width: Int, val height: Int, val frameRate: Int, val bitrate: Int)

/** Try Full HD first; preserve an HD path for older encoders rather than failing startup. */
object RecordingQuality {
    val profiles = listOf(
        PortraitVideoQuality(1080, 1920, 30, 8_000_000),
        PortraitVideoQuality(720, 1280, 24, 4_000_000),
    )
}
