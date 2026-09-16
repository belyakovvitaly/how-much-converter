package converter.android.ocr

import android.graphics.Bitmap
import converter.core.OcrLine

/**
 * The one piece of this app that is genuinely platform-specific.
 *
 * Everything above it — what counts as a currency token, how a number is
 * spelled, which boxes belong to one price — lives in `:core` and is shared
 * with iOS. Only the recognizer differs: PaddleOCR here, Vision there.
 *
 * Implementations return lines in image pixel coordinates with the origin at
 * the top left, because that is what [converter.core.mergeBoxes] expects and
 * what the benchmark's recorded output uses.
 */
interface OcrEngine {

    /** Shown in the UI, so it is always clear which recognizer produced a reading. */
    val name: String

    /**
     * Whether this engine can actually read anything yet.
     *
     * The models take a second or two to load, and until they have, the app is
     * holding a placeholder. Saying so is better than reporting "no price in
     * view" about a price that is plainly in view.
     */
    val ready: Boolean get() = true

    /**
     * Whether this engine has a Cyrillic model. A Latin-only recognizer does
     * not merely miss Cyrillic prices, it corrupts them — `180 ₽` comes back as
     * `18oP` — so the UI has to be able to say when it cannot be trusted with
     * one. See the benchmark in tools/ocr-bench.
     */
    val readsCyrillic: Boolean

    /**
     * Recognizes one frame. Called off the main thread, one call at a time.
     *
     * [thorough] lifts the per-frame reading budget. A live camera has to give
     * an answer before the next frame arrives, so it reads only the largest
     * text and lets later frames catch the rest; a still photograph is looked
     * at once and can afford to read all of it.
     */
    fun recognize(frame: Bitmap, thorough: Boolean = false): List<OcrLine>

    /**
     * Forgets anything carried between frames.
     *
     * An engine may reuse a reading when a box has barely moved, which is only
     * sound while the frames are a continuation of one another. Call this
     * before handing it an image that is not — a new photo, or the next of a
     * batch — or a reading may be carried onto text it never came from.
     */
    fun reset() = Unit
}

/**
 * Stands in until PaddleOCR is wired up.
 *
 * It reads nothing, on purpose: an engine that returns plausible rubbish would
 * be worse than one that returns nothing, and the camera pipeline is easier to
 * verify when the only thing missing is the recognizer.
 */
object UnwiredEngine : OcrEngine {
    override val name = "none yet"
    override val ready = false
    override val readsCyrillic = false
    override fun recognize(frame: Bitmap, thorough: Boolean): List<OcrLine> = emptyList()
}
