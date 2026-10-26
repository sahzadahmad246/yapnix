package com.yapnix.game

import org.junit.Assert.*
import org.junit.Test

class ReelGeometryTest {
    @Test fun outputStaysPortraitAndQuestionIsHalfWidthSquare() {
        assertEquals(9f / 16f, ReelGeometry.WIDTH / ReelGeometry.HEIGHT, .0001f)
        assertEquals(ReelGeometry.IMAGE_SIZE, ReelGeometry.IMAGE_BOTTOM - ReelGeometry.IMAGE_TOP, .0001f)
        assertTrue(ReelGeometry.IMAGE_SIZE < ReelGeometry.WIDTH / 2)
    }
    @Test fun portraitCameraAlignsWithPictureAndAnswerCardWithoutOverlap() {
        val rect = ReelGeometry.cameraRect()
        assertEquals(ReelGeometry.IMAGE_TOP, rect.top, .0001f)
        assertEquals(ReelGeometry.CAMERA_BOTTOM, rect.bottom, .0001f)
        assertTrue(rect.left > ReelGeometry.LEFT + ReelGeometry.IMAGE_SIZE)
        assertEquals(9f / 16f, rect.width / rect.height, .001f)
        assertTrue(rect.bottom < ReelGeometry.STAGE_HEIGHT)
    }
}
