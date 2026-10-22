package com.yapnix.game

import org.junit.Assert.*
import org.junit.Test

class ReelGeometryTest {
    @Test fun outputAndQuestionHaveRequestedAspectRatios() {
        assertEquals(9f / 16f, ReelGeometry.WIDTH / ReelGeometry.HEIGHT, .0001f)
        assertEquals(ReelGeometry.RIGHT - ReelGeometry.LEFT,
            ReelGeometry.IMAGE_BOTTOM - ReelGeometry.IMAGE_TOP, .0001f)
    }
    @Test fun cameraCannotCoverQuestionAtAnyEditorLimit() {
        for (round in listOf(false, true)) for (x in listOf(-2f, 0f, .5f, 1f, 2f))
            for (y in listOf(-2f, 0f, .5f, 1f, 2f)) for (size in listOf(-1f, .3f, .5f, 2f)) {
                val rect = ReelGeometry.cameraRect(x, y, size, round)
                assertTrue(rect.left >= ReelGeometry.LEFT - .001f)
                assertTrue(rect.right <= ReelGeometry.RIGHT + .001f)
                assertTrue(rect.top >= ReelGeometry.CAMERA_TOP - .001f)
                assertTrue(rect.bottom <= ReelGeometry.CAMERA_BOTTOM + .001f)
                assertTrue(rect.top > ReelGeometry.IMAGE_BOTTOM)
                assertTrue(rect.width > 0 && rect.height > 0)
            }
    }
}
