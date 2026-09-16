// Zooming into a photograph, as arithmetic that can be checked without a screen.
package converter.core

/**
 * How a picture is magnified on screen: a [scale] about the view's centre, then
 * a shift of [offsetX], [offsetY] view pixels.
 *
 * The overlay is transformed with the picture, so a label stays on its price at
 * any magnification and nothing about the reading has to change.
 */
data class Zoom(val scale: Double = 1.0, val offsetX: Double = 0.0, val offsetY: Double = 0.0) {

    /**
     * Magnified by [factor] about ([focusX], [focusY]) in view pixels, keeping
     * whatever is under the fingers under them, then moved by ([panX], [panY]),
     * and held within [MIN_SCALE]..[MAX_SCALE] and within the picture's edges.
     */
    fun transformed(
        factor: Double,
        focusX: Double,
        focusY: Double,
        panX: Double,
        panY: Double,
        width: Double,
        height: Double,
    ): Zoom {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = newScale / scale
        // A point at focus, measured from the centre, stays where it is:
        // offset' = (focus - centre)(1 - k) + k * offset.
        val fx = focusX - width / 2
        val fy = focusY - height / 2
        return Zoom(
            scale = newScale,
            offsetX = fx * (1 - k) + k * offsetX + panX,
            offsetY = fy * (1 - k) + k * offsetY + panY,
        ).clamped(width, height)
    }

    /**
     * Double tap: from the whole picture into [DOUBLE_TAP_SCALE] at the tapped
     * point, and from any magnification back out to the whole picture.
     */
    fun toggled(focusX: Double, focusY: Double, width: Double, height: Double): Zoom =
        if (scale > 1.0) Zoom()
        else transformed(DOUBLE_TAP_SCALE / scale, focusX, focusY, 0.0, 0.0, width, height)

    /** No further than the picture's edge: a magnified picture always fills the view. */
    fun clamped(width: Double, height: Double): Zoom {
        val limitX = (scale - 1) * width / 2
        val limitY = (scale - 1) * height / 2
        return copy(
            offsetX = offsetX.coerceIn(-limitX, limitX),
            offsetY = offsetY.coerceIn(-limitY, limitY),
        )
    }

    companion object {
        const val MIN_SCALE = 1.0
        const val MAX_SCALE = 6.0
        const val DOUBLE_TAP_SCALE = 2.5
    }
}
