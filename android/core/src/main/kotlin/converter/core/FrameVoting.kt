// Deciding what a stream of frames actually says.
//
// One frame is not evidence. A recognizer reading a live camera disagrees with
// itself between frames: a price appears, flickers out while a hand moves,
// comes back a digit different. Showing the newest frame's answer makes the
// display blink and puts every momentary misreading on screen.
//
// So a reading has to be seen repeatedly before it is shown, and keeps being
// shown for a while after it stops being seen. The two thresholds are
// deliberately different — a reading that had to earn its place should not lose
// it to a single blurred frame.
package converter.core

/** How much agreement a reading needs, and how much forgiveness it gets. */
data class VoteSettings(
    /** Sightings needed before a reading is shown at all. */
    val confirmAt: Int = 3,
    /** A shown reading disappears once its score falls to this. */
    val dropAt: Int = 0,
    /** Ceiling on the score, so a reading cannot outstay the thing it read. */
    val maxScore: Int = 5,
) {
    init {
        require(confirmAt > dropAt) { "confirmAt must be above dropAt to give any hysteresis" }
        require(maxScore >= confirmAt) { "maxScore below confirmAt would never confirm anything" }
    }
}

/** One candidate reading and how much the frames agree about it. */
data class PriceVote(val price: Price, val score: Int, val showing: Boolean)

/**
 * What the frames so far add up to.
 *
 * Votes keep the order they were first seen in, so a price does not jump around
 * the display as its neighbours come and go.
 */
data class VoteState(val votes: List<PriceVote> = emptyList()) {

    /** The readings worth showing. */
    val confirmed: List<Price> get() = votes.filter { it.showing }.map { it.price }
}

/**
 * Folds one frame's readings into the running state.
 *
 * A reading present in the frame gains a point, one absent loses one. Crossing
 * [VoteSettings.confirmAt] upwards starts showing it; falling to
 * [VoteSettings.dropAt] stops. A reading at zero that is not showing is
 * forgotten entirely, so a camera panning across a shelf does not accumulate
 * every price it ever glimpsed.
 *
 * Repeats within one frame count once: two identical price tags in view are one
 * answer, not twice the confidence.
 */
fun VoteState.observe(
    frame: List<Price>,
    settings: VoteSettings = VoteSettings(),
    fresh: Set<Price>? = null,
): VoteState {
    val seen = frame.toSet()
    // A reading carried over from an earlier frame keeps a price on screen but
    // is not new evidence for it. Without this, box tracking would manufacture
    // the very agreement the voting exists to require, and one bad recognition
    // could confirm itself by being copied forward.
    val independent = fresh ?: seen
    val updated = mutableListOf<PriceVote>()

    for (vote in votes) {
        val score = when {
            vote.price in independent -> minOf(vote.score + 1, settings.maxScore)
            vote.price in seen -> vote.score          // still there, but not re-read
            else -> maxOf(vote.score - 1, 0)
        }
        val showing = if (vote.showing) score > settings.dropAt else score >= settings.confirmAt
        if (score == 0 && !showing) continue
        updated += PriceVote(vote.price, score, showing)
    }

    // Anything in this frame that is not already tracked starts at one point,
    // which is never enough to be shown — that is the whole protection against
    // a single bad frame reaching the screen.
    val tracked = updated.mapTo(mutableSetOf()) { it.price }
    for (price in frame) {
        if (price in tracked) continue
        tracked += price
        // A price first seen on a carried line starts at zero: it has not been
        // read in any frame yet, only copied into this one.
        val score = if (price in independent) 1 else 0
        updated += PriceVote(price, score, showing = score >= settings.confirmAt)
    }

    return VoteState(updated)
}
