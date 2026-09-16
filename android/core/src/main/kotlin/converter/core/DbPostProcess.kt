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
    /**
     * How much longer than it is wide a region must be before its angle is
     * believed. Below this it is read upright.
     */
    val minElongation: Double = 2.0,
)

/**
 * A text region as it actually sits, which is usually not square to the frame.
 *
 * [angle] is the tilt of the text's own direction, in radians; [width] runs
 * along it and [height] across it. The recognizer has no model for tilted text,
 * so the crop has to be turned upright before it is read — without that, a tag
 * photographed from the side does not merely fail, it yields a plausible wrong
 * number: at eight degrees "$ 3.648,75" came back as 1648, and obliquely as
 * 38648.
 */
data class RotatedBox(
    val centerX: Double,
    val centerY: Double,
    val width: Double,
    val height: Double,
    val angle: Double,
) {
    /** The upright rectangle containing it, which is what a label is drawn in. */
    val bounds: Box
        get() {
            val c = kotlin.math.cos(angle)
            val s = kotlin.math.sin(angle)
            val dx = kotlin.math.abs(width * c) / 2 + kotlin.math.abs(height * s) / 2
            val dy = kotlin.math.abs(width * s) / 2 + kotlin.math.abs(height * c) / 2
            return Box(centerX - dx, centerY - dy, centerX + dx, centerY + dy)
        }
}

/** A detected region: where it is, how it sits, and how sure the detector was. */
data class Detection(val box: Box, val score: Float, val rotated: RotatedBox)

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
    val member = IntArray(width * height)
    val out = mutableListOf<Detection>()

    for (start in 0 until width * height) {
        if (seen[start] || probs[start] <= settings.threshold) continue

        var top = 0
        var count = 0
        stack[top++] = start
        seen[start] = true

        var total = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumYY = 0.0
        var sumXY = 0.0

        while (top > 0) {
            val index = stack[--top]
            member[count++] = index
            val x = (index % width).toDouble()
            val y = (index / width).toDouble()
            total += probs[index]
            sumX += x; sumY += y
            sumXX += x * x; sumYY += y * y; sumXY += x * y

            val ix = index % width
            val iy = index / width
            if (ix > 0) {
                val next = index - 1
                if (!seen[next] && probs[next] > settings.threshold) { seen[next] = true; stack[top++] = next }
            }
            if (ix < width - 1) {
                val next = index + 1
                if (!seen[next] && probs[next] > settings.threshold) { seen[next] = true; stack[top++] = next }
            }
            if (iy > 0) {
                val next = index - width
                if (!seen[next] && probs[next] > settings.threshold) { seen[next] = true; stack[top++] = next }
            }
            if (iy < height - 1) {
                val next = index + width
                if (!seen[next] && probs[next] > settings.threshold) { seen[next] = true; stack[top++] = next }
            }
        }

        if (count < settings.minPixels) continue
        val score = (total / count).toFloat()
        if (score < settings.boxThreshold) continue

        val rotated = fit(member, count, width, sumX, sumY, sumXX, sumYY, sumXY, settings)
        val grown = grow(rotated, settings.unclipRatio)
        out += Detection(grown.bounds.clampTo(width, height), score, grown)
    }
    return out
}

/**
 * The tightest rotated rectangle around one component.
 *
 * The direction comes from the second moments — for a line of text that is the
 * direction the text runs — and the extents from projecting every pixel onto it,
 * so the rectangle is exact rather than an ellipse dressed up as one.
 *
 * A component that is not clearly longer than it is wide has no reliable
 * direction: a single character, a bullet, a speck. Those are left upright,
 * because a confidently wrong angle is worse than none.
 */
private fun fit(
    member: IntArray,
    count: Int,
    width: Int,
    sumX: Double,
    sumY: Double,
    sumXX: Double,
    sumYY: Double,
    sumXY: Double,
    settings: DetectionSettings,
): RotatedBox {
    val n = count.toDouble()
    val meanX = sumX / n
    val meanY = sumY / n
    val covXX = sumXX / n - meanX * meanX
    val covYY = sumYY / n - meanY * meanY
    val covXY = sumXY / n - meanX * meanY

    // Eigenvalues of the 2x2 covariance, to judge how elongated this is.
    val middle = (covXX + covYY) / 2
    val spread = kotlin.math.sqrt(((covXX - covYY) / 2).let { it * it } + covXY * covXY)
    val major = middle + spread
    val minor = middle - spread

    val elongated = minor > 1e-6 && major / minor >= settings.minElongation
    val angle = if (elongated) 0.5 * kotlin.math.atan2(2 * covXY, covXX - covYY) else 0.0

    val cos = kotlin.math.cos(angle)
    val sin = kotlin.math.sin(angle)
    var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE
    var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
    for (i in 0 until count) {
        val index = member[i]
        val x = (index % width).toDouble()
        val y = (index / width).toDouble()
        val u = x * cos + y * sin
        val v = -x * sin + y * cos
        if (u < minU) minU = u
        if (u > maxU) maxU = u
        if (v < minV) minV = v
        if (v > maxV) maxV = v
    }

    val halfU = (maxU - minU + 1) / 2
    val halfV = (maxV - minV + 1) / 2
    val centreU = (minU + maxU + 1) / 2
    val centreV = (minV + maxV + 1) / 2
    return RotatedBox(
        centerX = centreU * cos - centreV * sin,
        centerY = centreU * sin + centreV * cos,
        width = halfU * 2,
        height = halfV * 2,
        angle = angle,
    )
}

/** Grows a rotated rectangle the way PaddleOCR grows a detection before cropping. */
private fun grow(box: RotatedBox, ratio: Float): RotatedBox {
    if (box.width <= 0 || box.height <= 0) return box
    val distance = box.width * box.height * ratio / (2.0 * (box.width + box.height))
    return box.copy(width = box.width + distance * 2, height = box.height + distance * 2)
}

private fun Box.clampTo(width: Int, height: Int) = Box(
    x0 = x0.coerceIn(0.0, width.toDouble()),
    y0 = y0.coerceIn(0.0, height.toDouble()),
    x1 = x1.coerceIn(0.0, width.toDouble()),
    y1 = y1.coerceIn(0.0, height.toDouble()),
)


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
