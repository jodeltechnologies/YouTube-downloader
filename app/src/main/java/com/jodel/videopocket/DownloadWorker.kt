package com.jodel.videopocket

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val processId = id.toString()
    private var lastUpdate = 0L
    private fun notification(text: String, progress: Int = -1): android.app.Notification {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        val intent = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Video Pocket")
            .setContentText(text).setOngoing(true).setContentIntent(intent)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .addAction(android.R.drawable.ic_delete, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
    }
    private fun foreground(text: String, progress: Int = -1) = ForegroundInfo(41, notification(text, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    private fun report(text: String, progress: Int = -1) {
        setProgressAsync(workDataOf("message" to text, "percent" to progress))
        setForegroundAsync(foreground(text, progress))
    }
    override fun doWork(): Result {
        val folder = File(applicationContext.cacheDir, "download-$processId")
        var published: Uri? = null
        val deadline = Executors.newSingleThreadScheduledExecutor()
        try {
            setForegroundAsync(foreground("Preparing download engine…")).get()
            YoutubeDL.init(applicationContext)
            FFmpeg.init(applicationContext)
            if (inputData.getString("action") == "update") {
                report("Checking for a download engine update…")
                YoutubeDL.updateYoutubeDL(applicationContext)
                return Result.success(workDataOf("message" to "Download engine is up to date."))
            }
            val url = YouTubeLink.canonical(inputData.getString("url") ?: "")
                ?: return Result.failure(workDataOf("message" to "Invalid YouTube link."))
            val audio = inputData.getBoolean("audio", false)
            val height = inputData.getInt("height", 720).takeIf { it in listOf(360, 480, 720, 1080) } ?: 720
            folder.mkdirs()
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-mtime")
            request.addOption("--socket-timeout", "25")
            request.addOption("--retries", "2")
            request.addOption("--fragment-retries", "2")
            request.addOption("--max-filesize", "500M")
            request.addOption("--match-filter", "!is_live & duration <= 7200")
            request.addOption("-o", File(folder, "%(title).100B [%(id)s].%(ext)s").absolutePath)
            if (audio) {
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "192K")
            } else {
                request.addOption("-f", "bv*[height<=$height][ext=mp4][vcodec^=avc1]+ba[ext=m4a]/b[height<=$height][ext=mp4]/bv*[height<=$height][ext=mp4]+ba[ext=m4a]")
                request.addOption("--merge-output-format", "mp4")
            }
            deadline.schedule({ try { YoutubeDL.destroyProcessById(processId) } catch (_: Exception) {} }, 30, TimeUnit.MINUTES)
            val callback: (Float, Long, String) -> Unit = { percent, eta, _ ->
                if (isStopped) {
                    try { YoutubeDL.destroyProcessById(processId) } catch (_: Exception) {}
                } else if (System.currentTimeMillis() - lastUpdate > 650) {
                    lastUpdate = System.currentTimeMillis()
                    val progress = percent.toInt().coerceIn(0, 99)
                    val message = if (percent >= 100) "Combining and saving your file…" else "Downloading: $progress%" + if (eta > 0) " · ${eta}s remaining" else ""
                    report(message, progress)
                }
            }
            YoutubeDL.execute(request, processId, callback)
            if (isStopped) return Result.failure(workDataOf("message" to "Download cancelled."))
            val ext = if (audio) "mp3" else "mp4"
            val file = folder.listFiles()?.firstOrNull { it.extension == ext && it.length() > 0 }
                ?: throw IllegalStateException("No complete file was produced. The video may be live, longer than two hours, or unavailable in this format.")
            if (file.length() > 500L * 1024 * 1024) throw IllegalStateException("The file is larger than 500 MB. Choose a lower quality.")
            report("Saving to Downloads/Video Pocket…", 99)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, if (audio) "audio/mpeg" else "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Video Pocket")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            published = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the file in Downloads.")
            resolver.openOutputStream(published)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IllegalStateException("Could not write the downloaded file.")
            resolver.update(published, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            saveHistory(file.name, published.toString(), audio)
            return Result.success(workDataOf("message" to "Saved to Downloads/Video Pocket", "uri" to published.toString(), "filename" to file.name))
        } catch (error: Exception) {
            published?.let { try { applicationContext.contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            val message = if (isStopped) "Download cancelled." else error.message?.take(450) ?: "Download failed. Check your connection and try again."
            return Result.failure(workDataOf("message" to message))
        } finally {
            deadline.shutdownNow()
            folder.deleteRecursively()
        }
    }
    override fun onStopped() {
        try { YoutubeDL.destroyProcessById(processId) } catch (_: Exception) {}
        super.onStopped()
    }
    private fun saveHistory(name: String, uri: String, audio: Boolean) {
        val prefs = applicationContext.getSharedPreferences("pocket", Context.MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString("history", "[]")) } catch (_: Exception) { JSONArray() }
        val entries = JSONArray().put(JSONObject().put("name", name).put("uri", uri).put("audio", audio).put("date", System.currentTimeMillis()))
        for (i in 0 until minOf(old.length(), 49)) entries.put(old.getJSONObject(i))
        prefs.edit().putString("history", entries.toString()).apply()
    }
}
