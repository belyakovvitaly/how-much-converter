package converter.android.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface

/**
 * Loads a photograph the right way up and small enough to work on.
 *
 * Orientation is the trap. A camera writes the picture in the sensor's own
 * frame and records how the phone was held in EXIF; nothing in BitmapFactory
 * applies it. Skip that and every portrait photograph arrives sideways, and the
 * recognizer reads nothing — the same failure the live camera had, arriving by
 * a different route.
 */
object StillImages {

    private const val TAG = "HowMuchStill"

    /** Beyond this the extra pixels cost time and buy nothing: the detector
     *  scales its input to a long side of 960 regardless. */
    private const val MAX_SIDE = 2400

    fun loadUpright(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not measure $uri", e)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = try {
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not decode $uri", e)
            return null
        } ?: return null

        val orientation = try {
            context.contentResolver.openInputStream(uri).use { stream ->
                stream?.let { ExifInterface(it) }?.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "no usable EXIF on $uri", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        return decoded.applying(orientation)
    }

    /** Halves the image until its long side is within [MAX_SIDE]. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_SIDE) sample *= 2
        return sample
    }

    private fun Bitmap.applying(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return this
        }
        val turned = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (turned !== this) recycle()
        return turned
    }
}
