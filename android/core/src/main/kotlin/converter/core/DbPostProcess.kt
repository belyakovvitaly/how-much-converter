// Turning a text detector's probability map into boxes.
//
// PaddleOCR's DBPostProcess traces contours and fits rotated minimum-area
// rectangles, which needs OpenCV. This does neither, and does not need to: the
// price rules only ever ask which boxes share a line and which sit next to each
// other, so an axis-aligned box carries everything that is later used.
//
// Measured rather than assumed — the same simplification scored 25 of the
// benchmark's 26 prices with nothing invented, matching full PaddleOCR.
package converter.core

/** Detector settings, defaulted to the values PP-OCRv5 ships with. */
data class DetectionSettings(
    val threshold: Float = 0.3f,
    val boxThreshold: Float = 0.6f,
    val unclipRatio: Float = 1.5f,
    val minPixels: Int = 4,
)

/** A detected region: where it is, and how sure the detector was. */
data class Detection(val box: Box, val score: Float)

/**
 * Finds text regions in a detector probability map.
 *
 * [probs] is row-major, [width] * [height] values in 0..1.
 *
 * The flood fill keeps its own stack. Recursion would be the shorter spelling
 * and would overflow on a real frame: one connected region of a 960x960 map can
 * run to hundreds of thousands of pixels.
 */
fun detectBoxes(
    probs: FloatArray,
    width: Int,
    height: Int,
    settings: DetectionSettings = DetectionSettings(),
): List<Detection> {
    require(probs.size >= width * height) {
        "probability map is ${probs.size}, expected at least ${width * height}"
    }

    val seen = BooleanArray(width * height)
    val stack = IntArray(width * height)
    val out = mutableListOf<Detection>()

    for (start in 0 until width * height) {
        if (seen[start] || probs[start] <= settings.threshold) continue

        var top = 0
        stack[top++] = start
        seen[start] = true

        var minX = start % width
        var maxX = minX
        var minY = start / width
        var maxY = minY
        var total = 0.0
        var count = 0

        while (top > 0) {
            val index = stack[--top]
            val x = index % width
            val y = index / width
            total += probs[index]
            count++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y

            if (x > 0) {
                val next = index - 1
                if (!seen[next] && probs[next] > settings.threshold) {
                    seen[next] = true
                    stack[top++] = next
                }
            }
            if (x < width - 1) {
                val next = index + 1
                if (!seen[next] && probs[next] > settings.threshold) {
                    seen[next] = true
                    stack[top++] = next
                }
            }
            if (y > 0) {
                val next = index - width
                if (!seen[next] && probs[next] > settings.threshold) {
                    seen[next] = true
                    stack[top++] = next
                }
            }
            if (y < height - 1) {
                val next = index + width
                if (!seen[next] && probs[next] > settings.threshold) {
                    seen[next] = true
                    stack[top++] = next
                }
            }
        }

        if (count < settings.minPixels) continue
        val score = (total / count).toFloat()
        if (score < settings.boxThreshold) continue

        val grown = unclip(
            Box(minX.toDouble(), minY.toDouble(), (maxX + 1).toDouble(), (maxY + 1).toDouble()),
            settings.unclipRatio,
            width,
            height,
        ) ?: continue
        out += Detection(grown, score)
    }
    return out
}


/**
 * Grows a box the way PaddleOCR grows a detection before cropping it.
 *
 * It offsets the polygon by `area * ratio / perimeter`; for a rectangle that
 * expression is exact, so there is no polygon clipper to reimplement.
 */
fun unclip(box: Box, ratio: Float, widthLimit: Int, heightLimit: Int): Box? {
    val w = box.x1 - box.x0
    val h = box.y1 - box.y0
    if (w <= 0 || h <= 0) return null

    val distance = w * h * ratio / (2.0 * (w + h))
    return Box(
        x0 = maxOf(0.0, box.x0 - distance),
        y0 = maxOf(0.0, box.y0 - distance),
        x1 = minOf(widthLimit.toDouble(), box.x1 + distance),
        y1 = minOf(heightLimit.toDouble(), box.y1 + distance),
    )
}

/** Maps a box from the detector's scaled input back to the source image. */
fun Box.scaleTo(scaleX: Double, scaleY: Double, width: Int, height: Int): Box = Box(
    x0 = (x0 * scaleX).coerceIn(0.0, width.toDouble()),
    y0 = (y0 * scaleY).coerceIn(0.0, height.toDouble()),
    x1 = (x1 * scaleX).coerceIn(0.0, width.toDouble()),
    y1 = (y1 * scaleY).coerceIn(0.0, height.toDouble()),
)
