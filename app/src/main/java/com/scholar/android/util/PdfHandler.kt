package com.scholar.android.util

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.scholar.android.R
import java.io.File

/**
 * Handles PDF operations including opening in browser and downloading.
 */
object PdfHandler {

    private const val PDF_MIME_TYPE = "application/pdf"
    private const val DOWNLOAD_SUBDIR = "Scholar"

    /**
     * Opens a PDF URL in the default browser or PDF viewer.
     * For direct PDF links, this allows the system to handle it appropriately.
     * For landing pages, it opens in the browser.
     *
     * @param context The context
     * @param url The PDF URL to open
     */
    fun openPdf(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                R.string.error_opening_pdf,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Downloads a PDF using Android's DownloadManager.
     * The file is saved to the Downloads/Scholar directory.
     *
     * @param context The context
     * @param url The PDF URL to download
     * @param title The title to use for the downloaded file and notification
     */
    fun downloadPdf(context: Context, url: String, title: String) {
        try {
            val sanitizedTitle = sanitizeFileName(title)
            val fileName = "$sanitizedTitle.pdf"

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                setDescription(context.getString(R.string.downloading_pdf))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                // Save to Downloads/Scholar directory
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "$DOWNLOAD_SUBDIR/$fileName"
                )

                // Set MIME type
                setMimeType(PDF_MIME_TYPE)

                // Allow download over mobile data and WiFi
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
            }

            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(
                context,
                R.string.download_started,
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                context,
                R.string.error_downloading_pdf,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Checks if a URL is a direct PDF link.
     *
     * @param url The URL to check
     * @return true if the URL ends with .pdf or contains pdf in the path/query
     */
    fun isPdfUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".pdf") ||
               lowerUrl.contains("/pdf/") ||
               lowerUrl.contains("pdf?") ||
               lowerUrl.contains("type=pdf") ||
               lowerUrl.contains("format=pdf")
    }

    /**
     * Checks if a URL is a Scholar PDF landing page.
     *
     * @param url The URL to check
     * @return true if this is a Google Scholar PDF viewer page
     */
    fun isScholarPdfPage(url: String): Boolean {
        return url.contains("scholar.google") &&
               (url.contains("/scholar?") && url.contains("output=citation") ||
                url.contains("scholar_url?") ||
                url.contains("scholar.googleusercontent.com"))
    }

    /**
     * Creates a pending intent for when the download completes.
     * This can be used to open the downloaded file.
     *
     * @param context The context
     * @param downloadId The download ID from DownloadManager
     * @return PendingIntent to open the downloaded file
     */
    fun createDownloadCompletePendingIntent(
        context: Context,
        downloadId: Long
    ): PendingIntent {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return PendingIntent.getActivity(
            context,
            downloadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Gets the file URI for a downloaded PDF using FileProvider.
     *
     * @param context The context
     * @param filePath The path to the downloaded file
     * @return Content URI for the file
     */
    fun getFileUri(context: Context, filePath: String): Uri {
        val file = File(filePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Sanitizes a string to be used as a file name.
     * Removes or replaces characters that are not allowed in file names.
     *
     * @param name The original name
     * @return Sanitized file name
     */
    private fun sanitizeFileName(name: String): String {
        // Remove or replace invalid characters
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(100) // Limit length
            .trim('_')
            .ifBlank { "document" }
    }
}
