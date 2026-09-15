package converter.android.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import converter.core.Box
import converter.core.OcrLine
import converter.core.ctcDecode
import converter.core.detectBoxes
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

    override fun recognize(frame: Bitmap): List<OcrLine> {
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

        val lines = mutableListOf<OcrLine>()
        for (detection in detectBoxes(probabilities, mapWidth, mapHeight)) {
            val box = detection.box.scaleTo(scale.x, scale.y, frame.width, frame.height)
            val crop = crop(frame, box) ?: continue
            val reading = read(crop)
            crop.recycle()
            if (reading == null || reading.text.isBlank()) continue
            lines += OcrLine(reading.text, reading.confidence.toDouble(), box)
        }
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

    private fun crop(frame: Bitmap, box: Box): Bitmap? {
        val x = box.x0.toInt().coerceIn(0, frame.width - 1)
        val y = box.y0.toInt().coerceIn(0, frame.height - 1)
        val width = (box.x1 - box.x0).toInt().coerceAtMost(frame.width - x)
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
