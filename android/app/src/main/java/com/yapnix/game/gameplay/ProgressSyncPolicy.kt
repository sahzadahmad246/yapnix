package com.yapnix.game.gameplay

import com.yapnix.game.data.SavedProgress

object ProgressSyncPolicy {
    /** Progress is a non-authoritative resumable save, not a leaderboard score. */
    fun choose(local: SavedProgress?, cloud: SavedProgress?): SavedProgress? {
        if (local == null) return cloud
        if (cloud == null) return local
        return if (cloud.lastPlayedAt > local.lastPlayedAt ||
            (cloud.lastPlayedAt == local.lastPlayedAt && cloud.completed && !local.completed)) cloud else local
    }
}
