// Carrying a reading from one frame to the next.
//
// Detection costs one model run for a whole frame; recognition costs one per
// box. On a still scene those boxes barely move, so re-reading every one of
// them every frame is the bulk of the work and almost none of the information.
//
// Reuse is not free of risk, though, and the risk is the one this project keeps
// coming back to: a wrong reading carried forward looks exactly like a reading
// confirmed by several frames. That is why a reused line is marked as such —
// see OcrLine.reused — so the voting can hold its score rather than raise it.
// Only an independent recognition counts as evidence.
package converter.core

/** How close a box has to be to count as the same one, and for how long. */
data class TrackerSettings(
    /** Overlap needed to treat two boxes as the same text. */
    val minOverlap: Double = 0.75,
    /**
     * How many frames in a row a reading may be carried before it must be read
     * again. Without this a price could be shown long after it changed.
     */
    val maxReuses: Int = 3,
)

/** A line read in an earlier frame, and how long it has been carried. */
data class TrackedLine(
    val box: Box,
    val text: String,
    val confidence: Double,
    val reuses: Int = 0,
)

/** What the previous frame read, ready to be matched against this one. */
data class TrackerState(val lines: List<TrackedLine> = emptyList())

/**
 * Intersection over union: 1 when two boxes coincide, 0 when they do not meet.
 *
 * The usual measure for "is this the same thing as before", and it is a ratio
 * rather than a distance, so one threshold works at any text size.
 */
fun overlap(a: Box, b: Box): Double {
    val left = maxOf(a.x0, b.x0)
    val top = maxOf(a.y0, b.y0)
    val right = minOf(a.x1, b.x1)
    val bottom = minOf(a.y1, b.y1)
    if (right <= left || bottom <= top) return 0.0

    val intersection = (right - left) * (bottom - top)
    val areaA = (a.x1 - a.x0) * (a.y1 - a.y0)
    val areaB = (b.x1 - b.x0) * (b.y1 - b.y0)
    val union = areaA + areaB - intersection
    return if (union <= 0.0) 0.0 else intersection / union
}

/**
 * The previous frame's reading for [box], if there is one that can be trusted
 * for another frame.
 *
 * Returns the best overlap rather than the first, so two boxes close together
 * do not swap their texts.
 */
fun TrackerState.reuseFor(box: Box, settings: TrackerSettings = TrackerSettings()): TrackedLine? =
    lines
        .filter { it.reuses < settings.maxReuses }
        .map { it to overlap(it.box, box) }
        .filter { (_, score) -> score >= settings.minOverlap }
        .maxByOrNull { (_, score) -> score }
        ?.first
