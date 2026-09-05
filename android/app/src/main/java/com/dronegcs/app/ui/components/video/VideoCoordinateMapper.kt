package com.dronegcs.app.ui.components.video

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Maps a tap in view pixels to drone-camera coordinates (1280x720),
 * accounting for how the video content is fitted inside the view:
 *
 *  - FILL_CENTER (CameraX phone preview): content is scaled so it COVERS the
 *    view and the overflow is cropped -> use the LARGER scale.
 *  - FIT / letterboxed (RTSP PlayerView default): content is scaled so it FITS
 *    inside the view with black bars -> use the SMALLER scale.
 *
 * The video the drone receives is 1280x720, so any click command must land on
 * that grid regardless of the phone's screen aspect (e.g. 20:9).
 */
object VideoCoordinateMapper {

    const val VIDEO_W = 1280
    const val VIDEO_H = 720

    /** Returns (x, y) in 1280x720 video coordinates, clamped to the frame. */
    fun map(
        tapX: Float,
        tapY: Float,
        viewW: Int,
        viewH: Int,
        fillCenter: Boolean
    ): Pair<Int, Int> {
        val w = viewW.coerceAtLeast(1).toFloat()
        val h = viewH.coerceAtLeast(1).toFloat()

        // Scale between view pixels and video pixels for the visible content.
        val scale = if (fillCenter) {
            max(w / VIDEO_W, h / VIDEO_H)
        } else {
            min(w / VIDEO_W, h / VIDEO_H)
        }
        // Video is centered in the view: compute the top-left of the (possibly
        // cropped) video rect in view coordinates.
        val originX = (w - VIDEO_W * scale) / 2f
        val originY = (h - VIDEO_H * scale) / 2f

        val x = ((tapX - originX) / scale).roundToInt().coerceIn(0, VIDEO_W)
        val y = ((tapY - originY) / scale).roundToInt().coerceIn(0, VIDEO_H)
        return x to y
    }
}
