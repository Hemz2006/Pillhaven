package com.medtrack.data

/**
 * A camera photo that was asked for but not yet handled: which screen asked ([owner], e.g. "edit:0") and when.
 * Kept on disk, because some phones close MedTrack while the camera app is open, and the photo would be lost.
 */
data class PendingCapture(val owner: String, val startedAt: Long)

/** A photo taken longer ago than this isn't picked up: it belongs to a moment the user has moved on from. */
const val CAPTURE_RECOVERY_MS = 60 * 60 * 1000L

/**
 * Whether the camera's file holds the photo [pending] asked for, for the screen [owner]: written after the camera
 * opened, not empty, and recent. Used both when the camera reports back (some camera apps report "cancelled" even
 * though they saved the photo) and when the screen opens again after MedTrack was closed meanwhile.
 */
fun PendingCapture.photoReady(owner: String, fileModified: Long, fileLength: Long, now: Long): Boolean =
    this.owner == owner && fileLength > 0 && fileModified >= startedAt - 2_000 && now - startedAt <= CAPTURE_RECOVERY_MS
