package com.jodel.videopocket
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class TransferFilesTest {
    @Test fun pausedFilesSurviveAndResumeUsesSameDirectory() {
        val root = Files.createTempDirectory("pocket-test").toFile()
        try {
            val folder = TransferFiles.directory(root, "session-123").apply { mkdirs() }
            File(folder, "video.mp4.part").writeText("partial bytes")
            File(folder, "video.mp4.ytdl").writeText("fragment state")
            TransferFiles.finish(folder, completed = false, discarded = false)
            val resumed = TransferFiles.directory(root, "session-123")
            assertEquals("partial bytes", File(resumed, "video.mp4.part").readText())
            assertTrue(File(resumed, "video.mp4.ytdl").exists())
            TransferFiles.finish(resumed, completed = true, discarded = false)
            assertFalse(resumed.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellationRemovesSavedParts() {
        val root = Files.createTempDirectory("pocket-test").toFile()
        try {
            val folder = TransferFiles.directory(root, "session-123").apply { mkdirs() }
            File(folder, "video.part").writeText("partial")
            TransferFiles.finish(folder, completed = false, discarded = true)
            assertFalse(folder.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun warningsDoNotHideTheActualError() {
        val message = DownloadError.message("WARNING: Your yt-dlp version is old\nWARNING: Some formats were skipped\nERROR: Requested format is not available")
        assertTrue(message.contains("lower quality"))
        assertFalse(message.contains("WARNING"))
    }
    @Test fun forbiddenResponseHasUsefulMessage() {
        assertTrue(DownloadError.message("WARNING: SABR\nERROR: HTTP Error 403: Forbidden").contains("YouTube refused"))
    }
}
