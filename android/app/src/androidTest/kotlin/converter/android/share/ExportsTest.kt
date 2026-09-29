package converter.android.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * A report copies the picture's own file, and a phone's photo says where it was
 * taken. The report has no use for that, and the privacy policy says it is not
 * sent — so this holds the copy to it.
 */
class ExportsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun photoWithLocation(): File {
        val file = File(context.cacheDir, "located.jpg")
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setLatLong(-34.6037, -58.3816)
            setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "25/1")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        assertNotNull("the fixture should carry a location", ExifInterface(file).latLong)
        return file
    }

    /** The compressed image data, from the first scan header to the end. */
    private fun scan(file: File): ByteArray {
        val bytes = file.readBytes()
        val sos = (0 until bytes.size - 1).first {
            bytes[it] == 0xFF.toByte() && bytes[it + 1] == 0xDA.toByte()
        }
        return bytes.copyOfRange(sos, bytes.size)
    }

    @Test
    fun removesWhereAPhotoWasTaken() {
        val file = photoWithLocation()
        Exports.stripLocation(file)
        val exif = ExifInterface(file)
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE))
    }

    @Test
    fun keepsThePixelsAndTheOrientation() {
        val file = photoWithLocation()
        val before = scan(file)
        Exports.stripLocation(file)
        assertArrayEquals("the image data must not be re-encoded", before, scan(file))
        assertEquals(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0),
        )
    }
}
