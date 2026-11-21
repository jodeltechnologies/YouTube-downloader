package com.jodel.videopocket

import java.io.File

object TransferFiles {
    fun directory(root: File, session: String): File {
        require(Regex("[a-zA-Z0-9-]{1,64}").matches(session)) { "Invalid download session" }
        return File(root, "transfer-$session")
    }
    fun finish(directory: File, completed: Boolean, discarded: Boolean) {
        // yt-dlp's .part and .ytdl files must survive pauses, failures, and process restarts.
        if (completed || discarded) directory.deleteRecursively()
    }
}
