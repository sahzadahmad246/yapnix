package com.yapnix.game.gameplay

import com.yapnix.game.data.SavedProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressSyncPolicyTest {
    private val local = SavedProgress("level", "Level", 1, 1, false, lastPlayedAt = 100)
    @Test fun keepsNewerLocalProgressEvenWhenCloudHasAHigherOldScore() {
        assertEquals(local, ProgressSyncPolicy.choose(local, local.copy(score = 9, lastPlayedAt = 50)))
    }
    @Test fun restoresNewerCloudProgressAndCompletionOnTies() {
        val cloud = local.copy(completed = true, lastPlayedAt = 200)
        assertEquals(cloud, ProgressSyncPolicy.choose(local, cloud))
        assertEquals(cloud, ProgressSyncPolicy.choose(cloud.copy(completed = false), cloud))
    }
    @Test fun handlesGuestOnlyAndCloudOnlySaves() {
        assertEquals(local, ProgressSyncPolicy.choose(local, null))
        assertEquals(local, ProgressSyncPolicy.choose(null, local))
    }
}
