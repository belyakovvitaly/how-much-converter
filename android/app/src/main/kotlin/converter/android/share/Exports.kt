package converter.android.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import converter.android.BuildConfig
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * What leaves the app, and only when the reader asks: a converted picture saved
 * to the phone's gallery, and a report of one that went wrong, handed to
 * whichever app the reader picks.
 *
 * Nothing here sends anything anywhere by itself. The gallery is the phone's
 * own, and a report goes through the system's share sheet — the reader chooses
 * where, and sees it go.
 */
object Exports {

    private const val TAG = "HowMuchExports"

    /** Where saved pictures go, under the phone's Pictures. */
    const val ALBUM = "How Much"

    /**
     * Saves [picture] to the gallery, in Pictures/How Much, and says whether
     * it worked. Blocking: call it off the main thread.
     *
     * From Android 10 the gallery takes a new picture without any permission;
     * before it, the caller has to have asked for storage first.
     */
    fun saveToGallery(context: Context, picture: Bitmap): Boolean {
        val name = "how-much-${stamp()}.jpg"
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    // Hidden from the gallery until it is whole.
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("the gallery would not take a new picture")
                try {
                    resolver.openOutputStream(uri).use { out ->
                        out ?: error("could not write to $uri")
                        picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                album.mkdirs()
                val file = File(album, name)
                file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                // Without this the gallery does not know the file is there.
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.path), arrayOf("image/jpeg"), null,
                )
            }
            true
        }.getOrElse {
            Log.e(TAG, "could not save to the gallery", it)
            false
        }
    }

    /**
     * Writes a report as one zip — the picture as it arrived, the picture as it
     * was shown, and [text] — and returns a link another app can read.
     * Blocking: call it off the main thread.
     *
     * One file rather than several, because a messenger compresses a picture
     * sent as a picture: at its size the failure often does not happen, and a
     * report that cannot be reproduced is a report of nothing. Inside a zip the
     * picture travels as it is.
     *
     * [original] is the picture's own file when there is one — from the gallery
     * or a share — and is copied byte for byte, since the app reads a smaller,
     * turned copy of it and a fix has to hold at the original's size too. A
     * photo taken in the app has no file; [read], the picture as the recognizer
     * saw it, is written instead, at a quality where JPEG loses next to nothing.
     */
    fun writeReport(
        context: Context,
        text: String,
        read: Bitmap,
        original: Uri?,
        shown: Bitmap,
    ): Uri {
        val folder = File(context.cacheDir, REPORTS).apply { mkdirs() }
        // Only the newest is kept: the share sheet has had it by now.
        folder.listFiles()?.forEach { it.delete() }
        val zip = File(folder, "how-much-report-${stamp()}.zip")

        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.entry("report.txt") { it.write(text.toByteArray()) }

            val copied = original?.let { uri ->
                runCatching {
                    val resolver = context.contentResolver
                    val extension = when (resolver.getType(uri)) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/heic", "image/heif" -> "heic"
                        else -> "jpg"
                    }
                    resolver.openInputStream(uri).use { input ->
                        input ?: error("could not open $uri")
                        out.entry("original.$extension") { input.copyTo(it) }
                    }
                }.onFailure { Log.w(TAG, "could not copy the original of a report", it) }
                    .isSuccess
            } ?: false
            if (!copied) {
                out.entry("as-read.jpg") { read.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            }

            out.entry("as-shown.jpg") { shown.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
    }

    /**
     * Where reports go, built in from outside the repository — see
     * app/build.gradle.kts. Never shown by the app; the mail app's own "To"
     * shows it, since that is where the reader sends it from.
     */
    val reportEmail: String? = BuildConfig.REPORT_EMAIL.takeIf { it.isNotBlank() }

    /**
     * Hands a report written by [writeReport] on: to a mail app, addressed to
     * [reportEmail] and ready to send, when the build has one; otherwise, or
     * with no mail app on the phone, to the share sheet.
     */
    fun shareReport(context: Context, report: Uri, summary: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, report)
            putExtra(Intent.EXTRA_SUBJECT, "How Much? problem report")
            putExtra(Intent.EXTRA_TEXT, summary)
            // The receiving app reads the file through this grant, and only it.
            clipData = ClipData.newRawUri(null, report)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val email = reportEmail
        if (email != null) {
            val mail = Intent(send).putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            // Only apps that send mail: a chat app would ignore the address
            // and send the report to whoever was picked there. A selector
            // would say this in one line, but the chooser on Android 16 finds
            // nothing through one, Gmail installed or not.
            val targets = mailTargets(context, mail)
            try {
                when (targets.size) {
                    0 -> Log.w(TAG, "no mail app; sharing the report instead")
                    1 -> {
                        context.startActivity(targets.single())
                        return
                    }
                    else -> {
                        val chooser = Intent.createChooser(targets.first(), "Send the report by email")
                            .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
                        context.startActivity(chooser)
                        return
                    }
                }
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "the mail app would not open; sharing the report instead", e)
            }
        }
        context.startActivity(Intent.createChooser(send, "Send the report"))
    }

    /**
     * [send] aimed at each app that both sends mail — answers `mailto:` — and
     * takes a file, one intent per app.
     */
    private fun mailTargets(context: Context, send: Intent): List<Intent> {
        val pm = context.packageManager
        val mailers = pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .mapTo(mutableSetOf()) { it.activityInfo.packageName }
        return pm.queryIntentActivities(send, 0)
            .filter { it.activityInfo.packageName in mailers }
            .distinctBy { it.activityInfo.packageName }
            .map { Intent(send).setClassName(it.activityInfo.packageName, it.activityInfo.name) }
    }

    private inline fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private const val REPORTS = "reports"
    private const val JPEG_QUALITY = 92
}
