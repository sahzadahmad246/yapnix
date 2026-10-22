package com.yapnix.game.data

import org.junit.Assert.*
import org.junit.Test

class ImageDecodeBudgetTest {
    @Test fun normalRoundsKeepFullVideoResolution() {
        assertEquals(720, ImageDecodeBudget.edge(5, 256L * 1024 * 1024))
    }
    @Test fun maximumRoundsFitHeapBudgetAlongsideCameraAndEncoder() {
        for (heapMb in listOf(64, 128, 256, 512)) {
            val heap = heapMb.toLong() * 1024 * 1024
            val edge = ImageDecodeBudget.edge(100, heap)
            assertTrue(edge > 0)
            assertTrue(100L * edge * edge * 4 <= minOf(heap / 3, 96L * 1024 * 1024))
        }
    }
}
