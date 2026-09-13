package com.sinyal.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.sinyal.app.R
import java.io.File
import java.io.FileOutputStream

/**
 * Hands a rendered map or report to whatever app the user picks.
 *
 * Files are written into the cache directory and exposed through a
 * [FileProvider]: passing a raw `file://` path would throw on any modern Android,
 * and the app has no business asking for storage permission to share its own
 * output.
 */
object ShareHelper {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun shareImage(context: Context, bitmap: Bitmap, caption: String) {
        val file = File(cacheDirectory(context), context.getString(R.string.share_map_filename))
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, intent, context.getString(R.string.share_map_title))
    }

    /**
     * Shares a long report as an attached text file rather than message body.
     *
     * Mail and chat apps truncate or reflow a large [Intent.EXTRA_TEXT]; a file
     * arrives intact and can be opened on a laptop, which is the point of a
     * report meant to be forwarded to a landlord or an ISP.
     */
    fun shareDocument(context: Context, text: String, fileName: String, caption: String) {
        val file = File(cacheDirectory(context), fileName)
        file.writeText(text)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, caption)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, intent, context.getString(R.string.share_report_title))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launch(context, intent, context.getString(R.string.share_report_title))
    }

    private fun launch(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun cacheDirectory(context: Context): File =
        File(context.cacheDir, "share").apply { mkdirs() }
}
