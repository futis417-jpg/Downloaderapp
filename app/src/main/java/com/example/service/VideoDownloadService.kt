package com.example.service

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class VideoDownloadService(private val context: Context) {
    fun downloadVideo(url: String, title: String, isAudio: Boolean = false): Long {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            // Create a safe title
            val safeTitle = title.take(50).replace("[^a-zA-Z0-9.-]".toRegex(), "_").ifEmpty { "file_${System.currentTimeMillis()}" }
            
            request.setTitle(safeTitle)
            request.setDescription(if (isAudio) "Descargando audio..." else "Descargando video...")
            
            val fileName = if (isAudio) "${safeTitle}.mp3" else "${safeTitle}_nowm.mp4"
            request.setDestinationInExternalPublicDir(
                if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            return downloadManager.enqueue(request)
        } catch (e: Exception) {
            e.printStackTrace()
            return -1L
        }
    }
}
