package com.tanoo.ambient

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

/**
 * Exists only so the system will hand us media sessions; reading other apps' sessions
 * requires being an enabled notification listener. Android TV has no settings screen for
 * that, so it's granted once over ADB:
 *   adb shell cmd notification allow_listener com.tanoo.ambient/com.tanoo.ambient.NowPlayingService
 */
class NowPlayingService : NotificationListenerService()

data class Track(
    val title: String,
    val artist: String,
    val appLabel: String,
    val packageName: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val durationMs: Long,
    private val positionMs: Long,
    private val positionAt: Long,
    private val speed: Float,
) {
    fun positionNow(): Long {
        if (!playing) return positionMs
        val elapsed = ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
        return (positionMs + elapsed).coerceIn(0, if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }
}

object NowPlaying {
    var track by mutableStateOf<Track?>(null)
        private set
    var hasAccess by mutableStateOf(false)
        private set

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            refresh()
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (!hasAccess) return
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val cn = ComponentName(ctx, NowPlayingService::class.java)
        try {
            if (manager == null) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
                manager = msm
            }
            pick(msm.getActiveSessions(cn))
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    fun refresh() {
        val ctx = appContext ?: return
        val msm = manager ?: return
        runCatching { pick(msm.getActiveSessions(ComponentName(ctx, NowPlayingService::class.java))) }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    /** Bring the app that owns the session to the front. */
    fun open(context: Context) {
        val c = controller ?: return
        val pi: PendingIntent? = c.sessionActivity
        if (pi != null && runCatching { pi.send() }.isSuccess) return
        Apps.launch(context, c.packageName)
    }

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val state = c?.playbackState
        if (c == null || meta == null || state == null ||
            state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE
        ) {
            track = null
            return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val art: Bitmap? = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val pm = appContext?.packageManager
        val appLabel = runCatching {
            pm!!.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)

        track = Track(
            title = title,
            artist = artist,
            appLabel = appLabel,
            packageName = c.packageName,
            art = art?.asImageBitmap(),
            playing = state.state == PlaybackState.STATE_PLAYING,
            durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = state.position,
            positionAt = state.lastPositionUpdateTime.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state.playbackSpeed.takeIf { it > 0f } ?: 1f,
        )
    }
}
