package com.dronegcs.app

import com.dronegcs.app.ui.components.video.VideoCoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the tap -> 1280x720 video coordinate mapping (FIT vs FILL_CENTER).
 */
class VideoCoordinateMapperTest {

    @Test
    fun `fit letterbox maps through visible video rect`() {
        // View 1600x720, video 1280x720: scale = min(1.25, 1.0) = 1.0,
        // originX = (1600 - 1280)/2 = 160 -> letterbox bars of 160px each side.
        val (x1, y1) = VideoCoordinateMapper.map(160f, 0f, 1600, 720, fillCenter = false)
        assertEquals(0, x1); assertEquals(0, y1)
        val (x2, y2) = VideoCoordinateMapper.map(1439f, 719f, 1600, 720, fillCenter = false)
        assertEquals(1279, x2); assertEquals(719, y2)
        // Tap in the left bar clamps to x=0, not negative.
        val (x3, _) = VideoCoordinateMapper.map(10f, 360f, 1600, 720, fillCenter = false)
        assertEquals(0, x3)
    }

    @Test
    fun `fill center maps through cropped video rect`() {
        // View 720x1280 portrait, video 1280x720: scale = max(720/1280, 1280/720) = 1.777,
        // originX = (720 - 1280*1.7777)/2 = (720-2275)/2 = -777 (cropped edges).
        val w = 720; val h = 1280
        val scale = maxOf(w / 1280f, h / 720f) // 1.7778
        val originX = (w - 1280 * scale) / 2f
        val (x1, _) = VideoCoordinateMapper.map(originX, 640f, w, h, fillCenter = true)
        assertEquals(0, x1)
        val (x2, _) = VideoCoordinateMapper.map(originX + 1280 * scale, 640f, w, h, fillCenter = true)
        assertEquals(1280, x2)
        // Center tap -> center of video.
        val (cx, cy) = VideoCoordinateMapper.map(w / 2f, h / 2f, w, h, fillCenter = true)
        assertEquals(640, cx); assertEquals(360, cy)
    }

    @Test
    fun `exact match when view equals video`() {
        val (x, y) = VideoCoordinateMapper.map(640f, 360f, 1280, 720, fillCenter = false)
        assertEquals(640, x); assertEquals(360, y)
    }
}
