package com.jodel.videopocket
import org.junit.Assert.*
import org.junit.Test

class NetworkRetryTest {
    @Test fun connectionBlipsRetryWhileNetworkStillReportsConnected() {
        for (error in listOf("ERROR: Connection reset by peer", "ERROR: Read timed out", "ERROR: HTTP Error 503: Unavailable", "ERROR: IncompleteRead(400 bytes read)")) {
            assertTrue(error, NetworkRetry.shouldRetry(error, online = true, attempts = 1))
        }
    }
    @Test fun lostNetworkCanBeRetriedWithoutDeletingParts() {
        assertTrue(NetworkRetry.shouldRetry("Download interrupted", online = false, attempts = 0))
    }
    @Test fun permanentErrorsAreNotRetriedEvenIfNetworkIsOffline() {
        for (error in listOf("ERROR: HTTP Error 403: Forbidden", "ERROR: Private video", "ERROR: Requested format is not available", "ERROR: No space left on device")) {
            assertFalse(error, NetworkRetry.shouldRetry(error, online = false, attempts = 0))
        }
    }
    @Test fun automaticRetriesAreBoundedAndManualResumeCanStartFreshBudget() {
        assertFalse(NetworkRetry.shouldRetry("Connection reset", true, NetworkRetry.MAX_ATTEMPTS))
        assertTrue(NetworkRetry.shouldRetry("Connection reset", true, 0))
    }
    @Test fun irrelevantWarningsDoNotTurnPermanentFailuresIntoRetries() {
        assertFalse(NetworkRetry.shouldRetry("WARNING: Network client changed\nERROR: Requested format is not available", true, 0))
    }
}
