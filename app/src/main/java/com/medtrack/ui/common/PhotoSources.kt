package com.medtrack.ui.common

import com.medtrack.data.photoReady
import com.medtrack.data.PendingCapture
import com.medtrack.container
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** The two ways to add photos: take one with the camera, or choose some from the phone's photos. */
class PhotoSources(val takePhoto: () -> Unit, val choosePhotos: () -> Unit)

/**
 * Launchers for the camera and the photo picker; [onPicked] gets what came back (copy it before the next capture,
 * since the camera always writes to the same cache file). Both screens crop what they get, the same way.
 *
 * [owner] names the screen (e.g. "edit:0", "detail:12"). Some phones close MedTrack while the camera app is open,
 * and then the photo never comes back to the screen; so the request is noted on disk, and when the same screen
 * opens again, a photo taken meanwhile is picked up there (never on another screen, so it can't join the wrong
 * medication).
 */
@Composable
fun rememberPhotoSources(owner: String, onPicked: (List<Uri>) -> Unit): PhotoSources {
    val context = LocalContext.current
    val prefs = context.container.prefs
    val captureFile = remember { File(File(context.cacheDir, "camera"), "capture.jpg") }
    val captureUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.files", captureFile) }
    val latestOnPicked by rememberUpdatedState(onPicked)

    /** Hands over the photo if it's the one this screen asked for; only the first caller gets it. */
    fun takeCapture(): Boolean {
        val pending = prefs.pendingCapture ?: return false
        val ready = pending.photoReady(owner, captureFile.lastModified(), captureFile.length(), System.currentTimeMillis())
        if (!ready && pending.owner != owner) return false
        prefs.pendingCapture = null
        if (ready) latestOnPicked(listOf(captureUri))
        return ready
    }

    // The camera reported back. "Cancelled" still counts when the photo was saved (some camera apps misreport).
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { takeCapture() }
    // Opened again after MedTrack was closed while the camera was open: pick up the photo taken meanwhile.
    LaunchedEffect(owner) { takeCapture() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return remember(camera, gallery) {
        PhotoSources(
            takePhoto = {
                captureFile.parentFile?.mkdirs()
                captureFile.delete() // so an old photo is never mistaken for the new one
                prefs.pendingCapture = PendingCapture(owner, System.currentTimeMillis())
                camera.launch(captureUri)
            },
            choosePhotos = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/** "Take a photo" and "Add photos" side by side (wrapping at large text sizes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoSourceButtons(sources: PhotoSources, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = sources.takePhoto, modifier = Modifier.heightIn(min = 48.dp)) { Text("Take a photo") }
        OutlinedButton(onClick = sources.choosePhotos, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add photos") }
    }
}
