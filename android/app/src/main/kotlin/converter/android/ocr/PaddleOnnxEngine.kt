package converter.android.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import converter.core.Box
import converter.core.OcrLine
import converter.core.TrackedLine
import converter.core.RotatedBox
import converter.core.TrackerState
import converter.core.ctcDecode
import converter.core.detectBoxes
import converter.core.extendedBack
import converter.core.intersects
import converter.core.leadingBareNumber
import converter.core.withPrefixFrom
import converter.core.reuseFor
import converter.core.scaleTo
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * PaddleOCR's PP-OCRv5 mobile detector and East Slavic recognizer, run through
 * ONNX Runtime.
 *
 * ONNX Runtime rather than Paddle Lite because it publishes an official Android
 * artifact; what is on Maven Central under Paddle Lite's name is third-party
 * repackaging. The models are converted by tools/export-ocr-models.py and read
 * from assets.
 *
 * The post-processing this leans on — thresholding the probability map, finding
 * regions, growing boxes, decoding CTC — lives in `:core`, where it is tested on
 * the JVM. What stays here is only what needs Android: bitmaps and tensors.
 */
class PaddleOnnxEngine private constructor(
    private val environment: OrtEnvironment,
    private val detector: OrtSession,
    private val recognizer: OrtSession,
    private val charset: List<String>,
) : OcrEngine, Closeable {

    override val name = "PP-OCRv5 mobile"

    // The East Slavic recognizer is the whole reason this engine was chosen.
    override val readsCyrillic = true

    /**
     * What the previous frame read. Detection runs on every frame; recognition
     * is skipped for a box that has barely moved, which is where the time goes
     * when several prices are in view.
     *
     * Guarded by the instance lock. CameraX uses one analyser thread, but a
     * still photograph is read from another while the camera keeps running, and
     * two readings sharing a tracker would carry text from one picture onto
     * another.
     */
    private var tracker = TrackerState()

    /**
     * What the last frame cost, split by stage.
     *
     * Kept because the total alone cannot say what to fix: detection and
     * recognition are improved by opposite things, and on a shelf most of the
     * recognition is spent reading text that could never be a price.
     */
    @Volatile
    var timings: Timings = Timings()
        private set

    /** Where one frame's time went. */
    data class Timings(
        val detectMillis: Long = 0,
        val postProcessMillis: Long = 0,
        val recogniseMillis: Long = 0,
        val boxes: Int = 0,
        val recognised: Int = 0,
        val reused: Int = 0,
        val skipped: Int = 0,
    )

    @Synchronized
    override fun reset() {
        tracker = TrackerState()
    }

    @Synchronized
    override fun recognize(frame: Bitmap, thorough: Boolean): List<OcrLine> {
        val detectStart = System.currentTimeMillis()
        val (input, scale) = detectorInput(frame)
        val probabilities: FloatArray
        val mapWidth: Int
        val mapHeight: Int

        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input.values),
            longArrayOf(1, 3, input.height.toLong(), input.width.toLong()),
        ).use { tensor ->
            detector.run(mapOf(detector.inputNames.first() to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val shape = output.info.shape
                mapHeight = shape[2].toInt()
                mapWidth = shape[3].toInt()
                val buffer = output.floatBuffer
                probabilities = FloatArray(mapWidth * mapHeight)
                buffer.get(probabilities)
            }
        }

        val detectMillis = System.currentTimeMillis() - detectStart

        val postStart = System.currentTimeMillis()
        val detections = detectBoxes(probabilities, mapWidth, mapHeight)
        val postMillis = System.currentTimeMillis() - postStart

        val recogniseStart = System.currentTimeMillis()
        val lines = mutableListOf<OcrLine>()
        val carried = mutableListOf<TrackedLine>()
        var recognised = 0
        var reusedCount = 0
        var skipped = 0
        var looked = 0
        val fresh = mutableListOf<Fresh>()

        // Reading costs about the same for every box, and a shelf is mostly
        // fine print that could never be a price: on the benchmark's shelf, 28
        // boxes read cost 368 ms of a 535 ms frame. So the tallest text is read
        // first — a price is the large type on a tag — and only so much of it
        // per frame. Nothing is lost by the cap: a box left unread now is read
        // in a later frame, and one read now is free in every frame after that,
        // because the tracker carries it.
        val minHeight = frame.height * MIN_TEXT_HEIGHT
        val ordered = detections
            .map { detection ->
                Triple(
                    detection.box.scaleTo(scale.x, scale.y, frame.width, frame.height),
                    detection.rotated.scaledBy(scale.x, scale.y),
                    detection,
                )
            }
            .sortedByDescending { (box, _, _) -> box.height }

        for ((box, tilted, _) in ordered) {

            val previous = tracker.reuseFor(box)
            if (previous != null) {
                // Marked reused so the voting downstream holds this price's
                // score rather than raising it: the same reading repeated is
                // not the same as several frames agreeing.
                lines += OcrLine(previous.text, previous.confidence, box, reused = true)
                carried += TrackedLine(box, previous.text, previous.confidence, previous.reuses + 1)
                reusedCount++
                continue
            }

            // Text this small reads as noise whatever the budget allows.
            if (box.height < minHeight) {
                skipped++
                continue
            }
            if (!thorough && recognised >= MAX_NEW_READINGS) {
                skipped++
                continue
            }

            val crop = crop(frame, tilted) ?: continue
            val reading = read(crop)
            crop.recycle()
            if (reading == null || reading.text.isBlank()) continue

            val confidence = reading.confidence.toDouble()
            lines += OcrLine(reading.text, confidence, box)
            carried += TrackedLine(box, reading.text, confidence, reuses = 0)
            recognised++
            fresh += Fresh(lines.lastIndex, carried.lastIndex, tilted)
        }

        // A number with nothing to say its currency may have a symbol just
        // before it that the detector found only as a scrap the recognizer
        // could not read. Looked for once everything else has been read, so
        // that only text actually read counts as being in the way: reading
        // across a neighbour could only confuse the two. See PrefixLook.kt.
        for (candidate in fresh) {
            if (!thorough && looked >= MAX_PREFIX_LOOKS) break
            val line = lines[candidate.line]
            val box = line.box ?: continue
            if (leadingBareNumber(line.text) == null) continue

            val wider = candidate.tilted.extendedBack(candidate.tilted.height * PREFIX_LOOK)
            val inset = box.height * 0.2
            val before = Box(wider.bounds.x0, box.y0 + inset, box.x0, box.y1 - inset)
            val crowded = lines.any { other -> other !== line && other.box?.intersects(before) == true }
            if (crowded) continue

            looked++
            // The symbol reads only in some crops and not others, so a few are
            // tried: turned to the line's angle, as it was read, and square to
            // the frame, each at full height and trimmed towards the middle.
            // A line's box is as tall as its tallest part, and on a chalked
            // sign that is the "Kg" hanging below the digits, not the digits;
            // trimmed nearer their band, "$" reads where it did not. Whatever
            // is tried, only a symbol can be taken from it, never a digit.
            val bounds = wider.bounds
            val upright = RotatedBox(
                centerX = (bounds.x0 + bounds.x1) / 2,
                centerY = (bounds.y0 + bounds.y1) / 2,
                width = bounds.x1 - bounds.x0,
                height = bounds.y1 - bounds.y0,
                angle = 0.0,
            )
            val tries = PREFIX_TRIMS.flatMap { trim ->
                listOf(wider, upright).map { it.copy(height = it.height * (1 - 2 * trim)) }
            }.let { if (thorough) it else it.take(2) }
            val text = tries.firstNotNullOfOrNull { region ->
                val widerCrop = crop(frame, region) ?: return@firstNotNullOfOrNull null
                val again = read(widerCrop)
                widerCrop.recycle()
                again?.let { withPrefixFrom(line.text, it.text) }
            } ?: continue
            // Grown to the left only, over the symbol; the looked-at region is
            // taller than the line and would put the label over the line above.
            lines[candidate.line] = line.copy(text = text, box = box.copy(x0 = minOf(box.x0, bounds.x0)))
            carried[candidate.carried] = carried[candidate.carried].copy(text = text)
        }

        tracker = TrackerState(carried)
        timings = Timings(
            detectMillis = detectMillis,
            postProcessMillis = postMillis,
            recogniseMillis = System.currentTimeMillis() - recogniseStart,
            boxes = detections.size,
            recognised = recognised,
            reused = reusedCount,
            skipped = skipped,
        )
        return lines
    }

    private fun read(crop: Bitmap): converter.core.Recognized? {
        val input = recognizerInput(crop) ?: return null
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input.values),
            longArrayOf(1, 3, input.height.toLong(), input.width.toLong()),
        ).use { tensor ->
            recognizer.run(mapOf(recognizer.inputNames.first() to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                val shape = output.info.shape
                val steps = shape[1].toInt()
                val classes = shape[2].toInt()
                val logits = FloatArray(steps * classes)
                output.floatBuffer.get(logits)
                return ctcDecode(logits, steps, classes, charset)
            }
        }
    }

    /** A line read in this frame: where it sits in both lists, and how it lies. */
    private class Fresh(val line: Int, val carried: Int, val tilted: RotatedBox)

    // --- tensors ------------------------------------------------------------
    private class Planar(val values: FloatArray, val width: Int, val height: Int)
    private class Scale(val x: Double, val y: Double)

    /**
     * Scales the long side to 960 and rounds both sides to a multiple of 32 —
     * what PP-OCRv5's own preprocessing does — then applies the ImageNet
     * normalisation the model was trained with.
     */
    private fun detectorInput(frame: Bitmap): Pair<Planar, Scale> {
        val factor = minOf(DET_LONG_SIDE.toDouble() / maxOf(frame.width, frame.height), 1.0)
        val width = roundTo32(frame.width * factor)
        val height = roundTo32(frame.height * factor)

        val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== frame) scaled.recycle()

        val values = FloatArray(3 * width * height)
        val plane = width * height
        for (i in pixels.indices) {
            val pixel = pixels[i]
            values[i] = ((pixel ushr 16 and 0xFF) / 255f - DET_MEAN[0]) / DET_STD[0]
            values[plane + i] = ((pixel ushr 8 and 0xFF) / 255f - DET_MEAN[1]) / DET_STD[1]
            values[2 * plane + i] = ((pixel and 0xFF) / 255f - DET_MEAN[2]) / DET_STD[2]
        }
        return Planar(values, width, height) to
            Scale(frame.width.toDouble() / width, frame.height.toDouble() / height)
    }

    /**
     * Keeps the crop's aspect ratio and feeds its natural width.
     *
     * The recognizer's width is a dynamic axis, so there is nothing to pad to.
     * Forcing the nominal 320 squashes a long line until it stops being
     * readable — in the prototype "Аренда квартиры — 2 500 рублей в сутки,"
     * came back as "Аренда ква2500 уов с".
     */
    private fun recognizerInput(crop: Bitmap): Planar? {
        if (crop.width <= 0 || crop.height <= 0) return null
        val natural = (REC_HEIGHT.toDouble() * crop.width / crop.height).toInt()
        val width = ((natural.coerceIn(16, REC_MAX_WIDTH) + 7) / 8) * 8

        val scaled = Bitmap.createScaledBitmap(crop, width, REC_HEIGHT, true)
        val pixels = IntArray(width * REC_HEIGHT)
        scaled.getPixels(pixels, 0, width, 0, 0, width, REC_HEIGHT)
        if (scaled !== crop) scaled.recycle()

        val values = FloatArray(3 * width * REC_HEIGHT)
        val plane = width * REC_HEIGHT
        for (i in pixels.indices) {
            val pixel = pixels[i]
            values[i] = ((pixel ushr 16 and 0xFF) / 255f - 0.5f) / 0.5f
            values[plane + i] = ((pixel ushr 8 and 0xFF) / 255f - 0.5f) / 0.5f
            values[2 * plane + i] = ((pixel and 0xFF) / 255f - 0.5f) / 0.5f
        }
        return Planar(values, width, REC_HEIGHT)
    }

    /**
     * Cuts the region out of the frame and turns it upright.
     *
     * The rotation is not a refinement. A tag photographed from the side has
     * its text running diagonally through an upright crop, and the recognizer
     * has no model for that — it returns a plausible wrong number rather than
     * nothing: at eight degrees "$ 3.648,75" read as 1648, and obliquely as
     * 38648.
     *
     * The side room is not cosmetic either. A currency glyph in front of the
     * digits sits at the very edge of the detector's box, where the recognizer
     * reads it worst, and reading it wrong is what fuses it into the number.
     * A third of the text's height of margin turns that failure into a safe one.
     */
    private fun crop(frame: Bitmap, region: RotatedBox): Bitmap? {
        val margin = region.height * SIDE_MARGIN
        val width = (region.width + margin * 2).toInt()
        val height = region.height.toInt()
        if (width < MIN_CROP || height < MIN_CROP) return null
        if (width > frame.width * 4 || height > frame.height * 4) return null

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postTranslate(-region.centerX.toFloat(), -region.centerY.toFloat())
            postRotate(Math.toDegrees(-region.angle).toFloat())
            postTranslate(width / 2f, height / 2f)
        }
        Canvas(out).drawBitmap(frame, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * The same region in the source image's pixels.
     *
     * The two axes are scaled by almost the same factor — they differ only by
     * the detector rounding each side to a multiple of 32 — so the extents take
     * the mean and the angle is carried across unchanged.
     */
    private fun RotatedBox.scaledBy(scaleX: Double, scaleY: Double): RotatedBox {
        val mean = (scaleX + scaleY) / 2
        return RotatedBox(
            centerX = centerX * scaleX,
            centerY = centerY * scaleY,
            width = width * mean,
            height = height * mean,
            angle = angle,
        )
    }

    override fun close() {
        detector.close()
        recognizer.close()
    }

    companion object {
        private const val DET_LONG_SIDE = 960
        private const val REC_HEIGHT = 48
        private const val REC_MAX_WIDTH = 1600
        private const val MIN_CROP = 3

        /**
         * How many boxes may be read afresh in one frame. The rest wait for the
         * next one, by which time these are free.
         */
        private const val MAX_NEW_READINGS = 12

        /** Text shorter than this fraction of the frame reads as noise. */
        private const val MIN_TEXT_HEIGHT = 0.012

        /** How far to look before a bare number for its symbol, in text heights. */
        private const val PREFIX_LOOK = 1.0

        /** How much of a line's height each second look trims from top and bottom. */
        private val PREFIX_TRIMS = listOf(0.0, 0.15, 0.25)

        /** Second looks per live frame; a still takes as many as it needs. */
        private const val MAX_PREFIX_LOOKS = 4

        /** Side margin around a crop, in units of the text height. */
        private const val SIDE_MARGIN = 0.3

        private val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        private fun roundTo32(value: Double): Int =
            maxOf(32, (Math.round(value / 32.0) * 32).toInt())

        /**
         * Reads the models out of assets. Costs a second or two and a good deal
         * of memory, so it belongs off the main thread and wants doing once.
         */
        fun create(context: Context): PaddleOnnxEngine {
            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }

            val detector = environment.createSession(
                context.assets.open("det.onnx").use { it.readBytes() }, options
            )
            val recognizer = environment.createSession(
                context.assets.open("rec.onnx").use { it.readBytes() }, options
            )
            val charset = context.assets.open("charset.txt").use { stream ->
                stream.bufferedReader().readLines()
            }

            return PaddleOnnxEngine(environment, detector, recognizer, charset)
        }
    }
}
