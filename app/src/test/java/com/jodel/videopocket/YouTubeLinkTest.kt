package com.jodel.videopocket
import org.junit.Assert.*
import org.junit.Test
class YouTubeLinkTest {
    @Test fun acceptsSharedLinks() {
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", YouTubeLink.capture("Watch this: https://youtu.be/abcdefghijk?si=test"))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", YouTubeLink.canonical("https://m.youtube.com/shorts/abcdefghijk"))
    }
    @Test fun rejectsUnrelatedAndUnsafeLinks() {
        for (url in listOf("https://youtube.com.evil.org/watch?v=abcdefghijk", "https://youtube.com@localhost/watch?v=abcdefghijk", "http://localhost/video", "https://youtube.com/playlist?list=abc", "https://youtu.be/short", "magnet:?xt=test")) assertNull(url, YouTubeLink.canonical(url))
    }
}
