package com.medtrack.ui.common

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.medtrack.R
import kotlinx.coroutines.delay

/** The videos' own background colours (the system launch screen uses the same, as @color/splash). */
private val SplashLight = Color(0xFFF6F8F3)
private val SplashDark = Color(0xFF070906)

/** Both videos are 1080 × 2340. */
private const val VIDEO_RATIO = 1080f / 2340f

/** However the video fares (no decoder, a stalled file), the app is never held back longer than this. */
private const val MAX_WAIT_MS = 6_000L

/**
 * The opening video, drawn over the app while it loads underneath. It plays once, muted, then fades out once
 * [ready] is true as well; a tap skips the rest once the app is ready. With animations turned off in the phone's
 * settings the video isn't played, and the splash only lasts until the app is ready.
 */
@Composable
fun VideoSplash(dark: Boolean, ready: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var played by remember { mutableStateOf(!animationsOn) }
    LaunchedEffect(Unit) {
        delay(MAX_WAIT_MS)
        played = true
    }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(played, ready) {
        if (played && ready) {
            if (animationsOn) alpha.animateTo(0f, tween(280))
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(if (dark) SplashDark else SplashLight)
            // Takes every touch, so the app underneath can't be used before it shows.
            .pointerInput(ready) { detectTapGestures { if (ready) played = true } },
        contentAlignment = Alignment.Center,
    ) {
        if (animationsOn) {
            val video = if (dark) R.raw.splash_dark else R.raw.splash_light
            // Fitted whole; the space around it is the video's own background colour, so it reads as full screen.
            AndroidView(
                factory = { ctx -> TextureView(ctx).apply { isOpaque = false; surfaceTextureListener = SplashPlayer(ctx, video) { played = true } } },
                onRelease = { (it.surfaceTextureListener as? SplashPlayer)?.release() },
                modifier = Modifier.aspectRatio(VIDEO_RATIO),
            )
        }
    }
}

/** Plays [video] once onto the TextureView's surface, then calls [onDone] (also on any error). */
private class SplashPlayer(private val context: Context, @RawRes private val video: Int, private val onDone: () -> Unit) :
    TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val target = Surface(texture).also { surface = it }
        player = try {
            MediaPlayer().apply {
                context.resources.openRawResourceFd(video).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setSurface(target)
                setVolume(0f, 0f)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { onDone() }
                setOnErrorListener { _, _, _ -> onDone(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("MedTrack", "Splash video couldn't play", e)
            onDone()
            null
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }
}
