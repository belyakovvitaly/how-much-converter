package converter.android.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import converter.core.Box
import converter.core.OcrLine
import converter.core.TrackedLine
import converter.core.TrackerState
import converter.core.ctcDecode
import converter.core.detectBoxes
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
     * Not synchronised: CameraX hands frames to one analyser thread at a time,
     * which is the only way this class is meant to be used.
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

    override fun reset() {
        tracker = TrackerState()
    }

    override fun recognize(frame: Bitmap): List<OcrLine> {
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

        // Reading costs about the same for every box, and a shelf is mostly
        // fine print that could never be a price: on the benchmark's shelf, 28
        // boxes read cost 368 ms of a 535 ms frame. So the tallest text is read
        // first — a price is the large type on a tag — and only so much of it
        // per frame. Nothing is lost by the cap: a box left unread now is read
        // in a later frame, and one read now is free in every frame after that,
        // because the tracker carries it.
        val minHeight = frame.height * MIN_TEXT_HEIGHT
        val ordered = detections
            .map { it to it.box.scaleTo(scale.x, scale.y, frame.width, frame.height) }
            .sortedByDescending { (_, box) -> box.height }

        for ((_, box) in ordered) {

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
            if (recognised >= MAX_NEW_READINGS) {
                skipped++
                continue
            }

            val crop = crop(frame, box) ?: continue
            val reading = read(crop)
            crop.recycle()
            if (reading == null || reading.text.isBlank()) continue

            val confidence = reading.confidence.toDouble()
            lines += OcrLine(reading.text, confidence, box)
            carried += TrackedLine(box, reading.text, confidence, reuses = 0)
            recognised++
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
     * Cuts the box out of the frame, with room to its left and right.
     *
     * The side room is not cosmetic. A currency glyph standing in front of the
     * digits sits at the very edge of the detector's box, where the recognizer
     * reads it worst — and reading it *wrong* is far more costly than missing
     * it, because a glyph misread as a digit fuses into the number: "₴1 200,50
     * грн" came back as "21 200,50 грн", a price seventeen times too large,
     * with its currency still attached so nothing downstream could refuse it.
     *
     * A third of the text's height of margin changes that failure into a safe
     * one — the glyph comes back as a letter or not at all, and the number is
     * intact. Measured across the prefix-written symbols (₴ ₹ ¥ ₩ $ € £): it
     * costs nothing on the benchmark corpus, and more margin than this starts
     * losing readings.
     */
    private fun crop(frame: Bitmap, box: Box): Bitmap? {
        val margin = (box.height * SIDE_MARGIN).toInt()
        val x = (box.x0.toInt() - margin).coerceIn(0, frame.width - 1)
        val right = (box.x1.toInt() + margin).coerceIn(0, frame.width)
        val y = box.y0.toInt().coerceIn(0, frame.height - 1)
        val width = (right - x).coerceAtMost(frame.width - x)
        val height = (box.y1 - box.y0).toInt().coerceAtMost(frame.height - y)
        if (width < MIN_CROP || height < MIN_CROP) return null
        return Bitmap.createBitmap(frame, x, y, width, height)
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
