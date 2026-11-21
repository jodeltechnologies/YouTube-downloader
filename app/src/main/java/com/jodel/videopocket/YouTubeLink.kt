package com.jodel.videopocket

import java.net.URI
import java.net.URLDecoder

object YouTubeLink {
    fun capture(text: String): String? {
        val urls = Regex("https?://[^\\s<>\"']+").findAll(text)
        return urls.mapNotNull { canonical(it.value.trimEnd('.', ',', ')', ']')) }.firstOrNull()
    }
    fun canonical(value: String): String? = try {
        val uri = URI(value.trim())
        val host = uri.host?.lowercase()
        if (uri.scheme !in listOf("https", "http") || host !in listOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be") || uri.userInfo != null || uri.port !in listOf(-1, 80, 443)) null
        else {
            val id = when {
                host == "youtu.be" -> uri.path.trim('/').split('/').firstOrNull()
                uri.path == "/watch" -> uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == "v" }?.getOrNull(1)?.let { URLDecoder.decode(it, "UTF-8") }
                else -> Regex("^/(shorts|live|embed)/([\\w-]{11})/?$").matchEntire(uri.path)?.groupValues?.get(2)
            }
            if (id != null && Regex("[\\w-]{11}").matches(id)) "https://www.youtube.com/watch?v=$id" else null
        }
    } catch (_: Exception) { null }
}
