// Reading a recognizer's output back into text.
package converter.core

/** What a recognizer made of one cropped line. */
data class Recognized(val text: String, val confidence: Float)

/**
 * Greedy CTC decoding: the best label at each step, with repeats collapsed and
 * blanks dropped.
 *
 * [logits] is row-major, [timeSteps] rows of [classes] values. Index 0 is the
 * CTC blank, so [charset] must be laid out the same way — blank first, then the
 * model's dictionary.
 *
 * Confidence is the mean of the scores of the labels that survived, not of
 * every step: the blanks between characters are the easy part of the problem
 * and averaging them in only flatters the result.
 */
fun ctcDecode(
    logits: FloatArray,
    timeSteps: Int,
    classes: Int,
    charset: List<String>,
): Recognized {
    require(logits.size >= timeSteps * classes) {
        "logits are ${logits.size}, expected at least ${timeSteps * classes}"
    }

    val text = StringBuilder()
    var kept = 0
    var confidenceSum = 0.0
    var previous = -1

    for (step in 0 until timeSteps) {
        val offset = step * classes
        var best = 0
        var bestScore = logits[offset]
        for (label in 1 until classes) {
            val score = logits[offset + label]
            if (score > bestScore) {
                bestScore = score
                best = label
            }
        }

        if (best != previous && best != 0 && best < charset.size) {
            text.append(charset[best])
            confidenceSum += bestScore
            kept++
        }
        previous = best
    }

    val confidence = if (kept == 0) 0f else (confidenceSum / kept).toFloat()
    return Recognized(text.toString(), confidence)
}
