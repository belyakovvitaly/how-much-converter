// Putting a box read from an image onto the view that is showing that image.
//
// The arithmetic is small and the consequence of getting it wrong is large: a
// label a little off sits beside the price instead of on it, and a label a lot
// off points at the wrong thing entirely. So it lives here, where it can be
// checked without a camera, rather than inline in a draw call.
package converter.core

/**
 * An image of [imageWidth] x [imageHeight] shown, whole and undistorted, inside
 * a view of [viewWidth] x [viewHeight].
 *
 * This is the "fit" arrangement — the entire frame is visible and the leftover
 * is empty margin. The alternative, filling the view and cropping, would put
 * part of every frame off-screen, and with it any price the camera read there:
 * the reader would be shown nothing for a price plainly in front of them.
 */
data class Viewport(
    val imageWidth: Int,
    val imageHeight: Int,
    val viewWidth: Float,
    val viewHeight: Float,
) {
    /** How many view pixels one image pixel covers. */
    val scale: Float =
        if (imageWidth <= 0 || imageHeight <= 0) 0f
        else minOf(viewWidth / imageWidth, viewHeight / imageHeight)

    /** Empty margin to the left of the image, in view pixels. */
    val offsetX: Float = (viewWidth - imageWidth * scale) / 2f

    /** Empty margin above the image, in view pixels. */
    val offsetY: Float = (viewHeight - imageHeight * scale) / 2f

    /** The same box, in the view's coordinates. */
    fun map(box: Box): Box = Box(
        x0 = offsetX + box.x0 * scale,
        y0 = offsetY + box.y0 * scale,
        x1 = offsetX + box.x1 * scale,
        y1 = offsetY + box.y1 * scale,
    )
}
