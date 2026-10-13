package com.yapnix.game.gameplay

/** A late image or speech callback from the previous question cannot start this timer. */
object RoundReadiness {
    fun ready(questionId: String?, imageId: String?, promptId: String?): Boolean =
        questionId != null && imageId == questionId && promptId == questionId
}
