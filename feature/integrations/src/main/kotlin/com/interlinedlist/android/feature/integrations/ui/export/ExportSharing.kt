package com.interlinedlist.android.feature.integrations.ui.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded CSV to the Android share sheet via the module's FileProvider,
 * granting the receiving app temporary read access to the cached file. Kept as a
 * small platform-facing helper so the ViewModel stays free of Android UI plumbing.
 */
object ExportSharing {

    /** Must match the authority declared in the module's AndroidManifest. */
    private fun authority(context: Context): String =
        "${context.packageName}.integrations.fileprovider"

    /** Launches a chooser to save/send [file] as CSV. */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ${file.name}")
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(chooser)
    }
}
