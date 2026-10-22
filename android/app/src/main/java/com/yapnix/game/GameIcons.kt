package com.yapnix.game

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/** Small app-owned vector set, shared by the game and local video studio. */
@Composable
internal fun GameIcon(name: String, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, description: String? = null) {
    val resource = when (name) {
        "play" -> R.drawable.game_play
        "arrow" -> R.drawable.game_arrow
        "chevron" -> R.drawable.game_chevron
        "camera" -> R.drawable.game_camera
        "mic" -> R.drawable.game_mic
        "spark" -> R.drawable.game_spark
        "shapes" -> R.drawable.game_shapes
        "mirror" -> R.drawable.game_mirror
        "reset" -> R.drawable.game_reset
        "resize" -> R.drawable.game_resize
        "check" -> R.drawable.game_check
        "close" -> R.drawable.game_close
        "home" -> R.drawable.game_home
        "download" -> R.drawable.game_download
        "film" -> R.drawable.game_film
        "share" -> R.drawable.game_share
        "trash" -> R.drawable.game_trash
        "refresh" -> R.drawable.game_refresh
        "pause" -> R.drawable.game_pause
        "clock" -> R.drawable.game_clock
        "picture" -> R.drawable.game_picture
        "circle" -> R.drawable.game_circle
        "square" -> R.drawable.game_square
        "volume" -> R.drawable.game_volume
        "edit" -> R.drawable.game_edit
        else -> R.drawable.game_spark
    }
    Icon(painterResource(resource), contentDescription = description, modifier = modifier, tint = tint)
}
