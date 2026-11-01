package app.riwaya.listen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.riwaya.MainActivity
import app.riwaya.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps listening going with the screen off: a media notification with play, pause, the paragraph and chapter
 * buttons, the lock screen and Bluetooth controls through a media session.
 */
class ListenService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.listen_channel), NotificationManager.IMPORTANCE_LOW))
        session = MediaSessionCompat(this, "riwaya").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Player.play()
                override fun onPause() = Player.pause()
                override fun onSkipToNext() = Player.nextParagraph()
                override fun onSkipToPrevious() = Player.previousParagraph()
                override fun onFastForward() = Player.nextChapter()
                override fun onRewind() = Player.previousChapter()
                override fun onStop() = Player.stop()
            })
            isActive = true
        }
        ServiceCompat.startForeground(this, ID, build(Player.state.value), foregroundType())
        scope.launch {
            Player.state
                .map { Triple(it.playing, it.chapterIndex, it.paragraph to it.loading) }
                .distinctUntilChanged()
                .collect {
                    val s = Player.state.value
                    if (!s.active) {
                        stopSelf()
                        return@collect
                    }
                    update(s)
                    nm.notify(ID, build(s))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Player.toggle()
            ACTION_NEXT -> Player.nextParagraph()
            ACTION_PREV -> Player.previousParagraph()
            ACTION_NEXT_CHAPTER -> Player.nextChapter()
            ACTION_STOP -> Player.stop()
        }
        return START_NOT_STICKY
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun update(s: PlayerState) {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.chapter?.name.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s.novel?.title.orEmpty())
                .build(),
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_STOP,
                )
                .setState(if (s.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, s.paragraph.toLong(), 1f)
                .build(),
        )
    }

    private fun action(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, ListenService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(s: PlayerState) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ms_headphones)
        .setContentTitle(s.novel?.title ?: getString(R.string.app_name))
        .setContentText(
            if (s.loading) getString(R.string.listen_loading) else s.chapter?.name.orEmpty(),
        )
        .setOngoing(s.playing)
        .setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_LISTEN, true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        .addAction(R.drawable.ms_skip_previous, getString(R.string.listen_prev_paragraph), action(ACTION_PREV, 1))
        .addAction(
            if (s.playing) R.drawable.ms_pause_fill1 else R.drawable.ms_play_arrow_fill1,
            getString(if (s.playing) R.string.listen_pause else R.string.listen_play),
            action(ACTION_TOGGLE, 2),
        )
        .addAction(R.drawable.ms_skip_next, getString(R.string.listen_next_paragraph), action(ACTION_NEXT, 3))
        .addAction(R.drawable.ms_close, getString(R.string.listen_stop), action(ACTION_STOP, 4))
        .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        .build()

    override fun onDestroy() {
        scope.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "listen"
        private const val ID = 7
        const val ACTION_TOGGLE = "riwaya.toggle"
        const val ACTION_NEXT = "riwaya.next"
        const val ACTION_PREV = "riwaya.prev"
        const val ACTION_NEXT_CHAPTER = "riwaya.next_chapter"
        const val ACTION_STOP = "riwaya.stop"
    }
}
