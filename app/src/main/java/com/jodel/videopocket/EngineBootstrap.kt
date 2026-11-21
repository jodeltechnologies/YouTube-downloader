package com.jodel.videopocket

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object EngineBootstrap {
    private fun read(url: String, limit: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "JODEL-Video-Pocket")
        try {
            check(connection.responseCode in 200..299) { "Engine update server is unavailable" }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= limit) { "Engine update is larger than expected" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    private fun install(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "engine-install.tmp")
        temporary.outputStream().use { it.write(bytes) }
        check(temporary.renameTo(target)) { "Could not install the download engine" }
    }
    fun prepare(context: Context, forceUpdate: Boolean, report: (String) -> Unit) {
        YoutubeDL.init(context)
        val prefs = context.getSharedPreferences("pocket", Context.MODE_PRIVATE)
        val bundled = context.assets.open("engine-version.txt").bufferedReader().use { it.readText().trim() }
        var installed = prefs.getString("engineVersion", null) ?: YoutubeDL.version(context)
        val target = File(File(context.noBackupFilesDir, YoutubeDL.baseName), "${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
        if (installed == null || installed < bundled) {
            context.resources.openRawResource(R.raw.ytdlp).use { install(target, it.readBytes()) }
            installed = bundled
            prefs.edit().putString("engineVersion", bundled).commit()
        }
        if (forceUpdate || System.currentTimeMillis() - prefs.getLong("lastEngineCheck", 0) > 24 * 60 * 60 * 1000L) {
            report("Checking for a download engine update…")
            try {
                val metadata = JSONObject(read("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest", 1024 * 1024).toString(Charsets.UTF_8))
                val latest = metadata.getString("tag_name")
                check(Regex("\\d{4}\\.\\d{2}\\.\\d{2}(?:\\.\\d+)?").matches(latest)) { "Unexpected engine version" }
                if (installed < latest) {
                    val base = "https://github.com/yt-dlp/yt-dlp/releases/download/$latest"
                    val sums = read("$base/SHA2-256SUMS", 1024 * 1024).toString(Charsets.UTF_8)
                    val expected = sums.lineSequence().map { it.trim().split(Regex("\\s+")) }.first { it.size == 2 && it[1] == "yt-dlp" }[0]
                    val binary = read("$base/yt-dlp", 20 * 1024 * 1024)
                    val actual = MessageDigest.getInstance("SHA-256").digest(binary).joinToString("") { "%02x".format(it.toInt() and 255) }
                    check(expected == actual) { "Engine checksum verification failed" }
                    install(target, binary)
                    prefs.edit().putString("engineVersion", latest).commit()
                }
                prefs.edit().putLong("lastEngineCheck", System.currentTimeMillis()).commit()
            } catch (error: Exception) {
                if (forceUpdate) throw IllegalStateException("Could not check for an engine update. Check your internet and try again.", error)
                // The APK already supplies a verified recent engine if an update check fails.
                report("Update check unavailable. Using the installed engine…")
            }
        }
    }
}
