package com.jodel.videopocket

object DownloadError {
    fun message(raw: String?): String {
        val error = raw.orEmpty()
        val lines = error.lineSequence().map { it.trim() }.toList()
        val reason = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.lastOrNull { it.isNotBlank() && !it.startsWith("WARNING:") } ?: ""
        val lower = reason.lowercase()
        return when {
            "no space left" in lower || "enospc" in lower -> "Your phone is out of storage. Free some space, then tap Resume."
            "403" in lower || "sign in to confirm" in lower || "not a bot" in lower -> "YouTube refused this download. Update the engine and try Resume, or choose another video."
            "requested format" in lower -> "This video does not offer that MP4 quality. Cancel this download and choose a lower quality."
            "private video" in lower || "not available" in lower || "video unavailable" in lower || "age-restricted" in lower -> "This video is private, restricted, or unavailable. Try another video."
            "timed out" in lower || "unable to download" in lower || "network" in lower || "resolve" in lower -> "The connection was interrupted. Check your internet, then tap Resume."
            "no complete file" in lower -> "The file could not be completed. It may be live, longer than two hours, or unavailable in this format."
            "500 mb" in lower -> "The file is larger than 500 MB. Cancel this download and choose a lower quality."
            "selected folder" in lower || "permission" in lower || "securityexception" in lower -> "Cannot access the selected folder. Choose it again or select Downloads, then tap Resume."
            "could not" in lower && ("downloads" in lower || "write" in lower) -> "The file could not be saved to Downloads. Check available storage, then tap Resume."
            "python" in lower || "javascript" in lower || "js runtime" in lower -> "The download engine could not process this video. Update the engine and tap Resume."
            else -> "The download could not finish. Your partial files are kept. Update the engine or check your connection, then tap Resume."
        }
    }
}
