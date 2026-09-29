// Where each label goes, so that neighbours do not cover each other.
//
// A label covers the price it converts, with a margin around it so the price
// underneath does not peek out. On a receipt the amounts sit a line apart, and
// a detector's box for turned text is taller than the text: margins, and then
// the boxes themselves, overlap, and the labels drawn over them pile up into
// something no one can read. So the areas are laid out first, and drawn after.
package converter.core

/** The margin around a price's box, as a share of the box's height. */
const val LABEL_MARGIN = 0.12

/** The space kept between two labels that had to be pulled apart, likewise. */
const val LABEL_GAP = 0.06

/** The area a label would take with no neighbours: the box and its margin. */
fun Box.labelArea(): Box {
    val margin = height * LABEL_MARGIN
    return Box(x0 - margin, y0 - margin, x1 + margin, y1 + margin)
}

/**
 * The area each label takes, in the order of [boxes], no two overlapping.
 *
 * Two areas that overlap are divided along the axis that separates them — one
 * above the other on a receipt, side by side on a line with two prices. The
 * margins go first: where the boxes themselves are clear of each other, the
 * border is drawn halfway between them, and each label still covers the whole
 * of its price. Only where the boxes overlap too does the border fall halfway
 * between their centres, cutting into both; a label then covers most of its
 * price rather than half of its neighbour's.
 *
 * A label's text is sized to its area, so a divided label is drawn smaller —
 * smaller type in its own place beats full-size type on top of another.
 */
fun labelAreas(boxes: List<Box>): List<Box> {
    val areas = boxes.map { it.labelArea() }.toMutableList()
    // Dividing one pair can only shrink areas, so a few passes settle it.
    repeat(PASSES) {
        var changed = false
        for (i in areas.indices) for (j in i + 1 until areas.size) {
            val divided = divide(boxes[i], areas[i], boxes[j], areas[j]) ?: continue
            areas[i] = divided.first
            areas[j] = divided.second
            changed = true
        }
        if (!changed) return areas
    }
    return areas
}

private const val PASSES = 8

/** The two areas divided, or null when they do not overlap. */
private fun divide(boxA: Box, a: Box, boxB: Box, b: Box): Pair<Box, Box>? {
    val overlapX = minOf(a.x1, b.x1) - maxOf(a.x0, b.x0)
    val overlapY = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
    if (overlapX <= 0 || overlapY <= 0) return null

    val gap = minOf(boxA.height, boxB.height) * LABEL_GAP
    // On the same line when their centres are closer than a quarter of the
    // shorter one's height; otherwise one is above the other.
    val sameLine = kotlin.math.abs(boxA.centreY - boxB.centreY) <
        minOf(boxA.height, boxB.height) * 0.25

    return if (sameLine) {
        val aFirst = boxA.centreX <= boxB.centreX
        val (leftBox, rightBox) = if (aFirst) boxA to boxB else boxB to boxA
        val (left, right) = if (aFirst) a to b else b to a
        val border = border(leftBox.x1, rightBox.x0, leftBox.centreX, rightBox.centreX)
        val newLeft = left.copy(x1 = minOf(left.x1, border - gap / 2))
        val newRight = right.copy(x0 = maxOf(right.x0, border + gap / 2))
        if (aFirst) newLeft to newRight else newRight to newLeft
    } else {
        val aFirst = boxA.centreY <= boxB.centreY
        val (upperBox, lowerBox) = if (aFirst) boxA to boxB else boxB to boxA
        val (upper, lower) = if (aFirst) a to b else b to a
        val border = border(upperBox.y1, lowerBox.y0, upperBox.centreY, lowerBox.centreY)
        val newUpper = upper.copy(y1 = minOf(upper.y1, border - gap / 2))
        val newLower = lower.copy(y0 = maxOf(lower.y0, border + gap / 2))
        if (aFirst) newUpper to newLower else newLower to newUpper
    }
}

/**
 * Where two neighbours meet: halfway across the space between their boxes
 * when there is some, or halfway between their centres when they overlap.
 */
private fun border(firstEnd: Double, secondStart: Double, firstCentre: Double, secondCentre: Double) =
    if (firstEnd <= secondStart) (firstEnd + secondStart) / 2 else (firstCentre + secondCentre) / 2

private val Box.centreX: Double get() = (x0 + x1) / 2
private val Box.centreY: Double get() = (y0 + y1) / 2
