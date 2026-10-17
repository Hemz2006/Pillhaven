package com.medtrack.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCaptureTest {
    private val asked = PendingCapture("edit:0", startedAt = 1_000_000)

    @Test fun aPhotoSavedAfterTheCameraOpenedIsReadyForTheSameScreen() =
        assertTrue(asked.photoReady("edit:0", fileModified = 1_030_000, fileLength = 250_000, now = 1_060_000))

    @Test fun neverGoesToAnotherScreen() =
        assertFalse(asked.photoReady("detail:12", fileModified = 1_030_000, fileLength = 250_000, now = 1_060_000))

    @Test fun anEmptyOrOlderFileIsNotThePhoto() {
        assertFalse(asked.photoReady("edit:0", fileModified = 1_030_000, fileLength = 0, now = 1_060_000))
        assertFalse(asked.photoReady("edit:0", fileModified = 900_000, fileLength = 250_000, now = 1_060_000))
    }

    @Test fun aPhotoFromLongAgoIsNotPickedUp() =
        assertFalse(asked.photoReady("edit:0", fileModified = 1_030_000, fileLength = 250_000, now = 1_000_000 + CAPTURE_RECOVERY_MS + 1))
}
