package io.github.ziginsider.mediasessiontest

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.*
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import okhttp3.OkHttpClient
import java.io.File

class MediaService : Service() {

    private val stateBuilder = PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    )

    private val metadataBuilder = MediaMetadataCompat.Builder()
    private val musicCatalog = TestMusicCatalog()

    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusRequested = false
    private var currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private var playOnFocusGain = false
    private var exoPlayer: SimpleExoPlayer? = null
    private var extractorsFactory: ExtractorsFactory? = null
    private var dataSourceFactory: com.google.android.exoplayer2.upstream.DataSource.Factory? = null

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    "Player controls",
                    NotificationManagerCompat.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val audioAtributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setAudioAttributes(audioAtributes)
                    .build()
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "MediaService")
        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession?.setCallback(mediaSessionCallback)

        val activityIntent = Intent(applicationContext, MainActivity::class.java)
        mediaSession?.setSessionActivity(PendingIntent.getActivity(applicationContext, 0,
                activityIntent, 0))

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, applicationContext,
                MediaButtonReceiver::class.java)
        mediaSession?.setMediaButtonReceiver(PendingIntent.getBroadcast(applicationContext, 0,
                mediaButtonIntent, 0))

        exoPlayer = ExoPlayerFactory.newSimpleInstance(DefaultRenderersFactory(this),
                DefaultTrackSelector(), DefaultLoadControl())
        exoPlayer?.addListener(exoPlayerListener)
        val httpDataSourceFactory = OkHttpDataSourceFactory(OkHttpClient(),
                Util.getUserAgent(this, getString(R.string.app_name)), null)
        val cache = SimpleCache(File(this.cacheDir.absolutePath + "/exoplayer"),
                LeastRecentlyUsedCacheEvictor(CACHE_SIZE))
        dataSourceFactory = CacheDataSourceFactory(cache,
                httpDataSourceFactory,
                CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        extractorsFactory = DefaultExtractorsFactory()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        exoPlayer?.release()
    }

    override fun onBind(intent: Intent?): IBinder {
        return PlayerServiceBinder()
    }

    inner class PlayerServiceBinder : Binder() {
        fun getMediaSessionToken() = mediaSession?.sessionToken
    }

    private val exoPlayerListener = object : ExoPlayer.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                mediaSessionCallback.onSkipToNext()
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
        }

        override fun onPositionDiscontinuity() {
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?) {
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> currentAudioFocusState = AUDIO_FOCUSED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> currentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                playOnFocusGain = exoPlayer != null && exoPlayer?.playWhenReady!!
            }
            AudioManager.AUDIOFOCUS_LOSS -> currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }

        if (exoPlayer != null) {
            configurePlayerState()
        }
    }

    private fun configurePlayerState() {
        if (currentAudioFocusState == AUDIO_NO_FOCUS_NO_DUCK) {
            playerPause()
        } else {
            registerReceiver(becomingNoiseReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            if (currentAudioFocusState == AUDIO_NO_FOCUS_CAN_DUCK) {
                exoPlayer?.volume = VOLUME_DUCK
            } else {
                exoPlayer?.volume = VOLUME_NORMAL
            }

            // If we were playing when we lost focus, we need to resume playing.
            if (playOnFocusGain) {
                exoPlayer?.playWhenReady = true
                playOnFocusGain = false
            }
        }
    }

    private fun playerPause() {
        exoPlayer?.let {
            it.playWhenReady = false
            unregisterReceiver(becomingNoiseReceiver)
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        private var currentUri: Uri? = null
        private var currentState = PlaybackStateCompat.STATE_STOPPED

        override fun onPlay() {
            if (!exoPlayer?.playWhenReady!!) {
                startService(Intent(applicationContext, MediaService::class.java))
                val track = musicCatalog.currentTrack
                updateMetadataFromTrack(track)
                prepareToPlay(track.uri)

                if (!audioFocusRequested) {
                    audioFocusRequested = true
                    var audioFocusResult = 0
                    audioFocusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager?.requestAudioFocus(audioFocusRequest)!!
                    } else {
                        audioManager?.requestAudioFocus(
                                this@MediaService.audioFocusChangeListener,
                                AudioManager.STREAM_MUSIC,
                                AudioManager.AUDIOFOCUS_GAIN
                        )!!
                    }
                    if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        return
                    }
                }

                mediaSession?.isActive = true
                registerReceiver(becomingNoiseReceiver,
                        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                exoPlayer?.playWhenReady = true
            }

            //playOnFocusGain = true

            mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1F).build())
            currentState = PlaybackStateCompat.STATE_PLAYING

            //configurePlayerState()

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onPause() {
            if (exoPlayer?.playWhenReady!!) {
                exoPlayer?.playWhenReady = false
                unregisterReceiver(becomingNoiseReceiver)
            }

            mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1F).build())
            currentState = PlaybackStateCompat.STATE_PAUSED

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onStop() {
            if (exoPlayer?.playWhenReady!!) {
                exoPlayer?.playWhenReady = false
                unregisterReceiver(becomingNoiseReceiver)
            }

            if (audioFocusRequested) {
                audioFocusRequested = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioManager?.abandonAudioFocusRequest(audioFocusRequest)
                } else {
                    audioManager?.abandonAudioFocus(audioFocusChangeListener)
                }
            }

            mediaSession?.isActive = false

            mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1F).build())
            currentState = PlaybackStateCompat.STATE_STOPPED

            refreshNotificationAndForegroundStatus(currentState)

            stopSelf()
        }

        override fun onSkipToNext() {
            val track = musicCatalog.next()
            updateMetadataFromTrack(track)

            refreshNotificationAndForegroundStatus(currentState)

            prepareToPlay(track.uri)
        }

        override fun onSkipToPrevious() {
            val track = musicCatalog.previous()
            updateMetadataFromTrack(track)

            refreshNotificationAndForegroundStatus(currentState)

            prepareToPlay(track.uri)

        }

        private fun prepareToPlay(uri: Uri) {
            if (uri != currentUri) {
                currentUri = uri
                val mediaSource = ExtractorMediaSource(uri, dataSourceFactory, extractorsFactory,
                        null, null)
                exoPlayer?.prepare(mediaSource)
            }
        }

        private fun updateMetadataFromTrack(track: TestMusicCatalog.Track) {
            with(metadataBuilder) {
                putBitmap(MediaMetadataCompat.METADATA_KEY_ART,
                        BitmapFactory.decodeResource(resources, track.bitmap))
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.artist)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
            }
            mediaSession?.setMetadata(metadataBuilder.build())
        }
    }

    val becomingNoiseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                playerPause()
            }
        }
    }

    private fun refreshNotificationAndForegroundStatus(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> startForeground(NOTIFICATION_ID, getNotification(playbackState))
            PlaybackStateCompat.STATE_PAUSED -> {
                NotificationManagerCompat.from(this@MediaService).notify(NOTIFICATION_ID,
                        getNotification(playbackState))
                stopForeground(false)
            }
            else -> stopForeground(true)
        }
    }

    private fun getNotification(playbackState: Int): Notification {
        val builder = MediaStyleHelper.from(this, mediaSession)

        builder.addAction(Action(android.R.drawable.ic_media_previous,
                "previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))

        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            builder.addAction(Action(android.R.drawable.ic_media_pause,
                    "pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)))
        } else {
            builder.addAction(Action(android.R.drawable.ic_media_play,
                    "play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)))
        }

        builder.addAction(Action(android.R.drawable.ic_media_next,
                "next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
        builder.setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession?.sessionToken))
        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.color = ContextCompat.getColor(this, R.color.colorPrimaryDark)
        builder.setShowWhen(false)
        builder.priority = PRIORITY_HIGH
        builder.setOnlyAlertOnce(true)
        builder.setChannelId(NOTIFICATION_CHANNEL_ID)

        return builder.build()
    }

    companion object {

        private const val NOTIFICATION_ID = 33
        private const val NOTIFICATION_CHANNEL_ID = "media_channel"
        private const val AUDIO_NO_FOCUS_NO_DUCK = 0
        private const val AUDIO_NO_FOCUS_CAN_DUCK = 1
        private const val AUDIO_FOCUSED = 2
        private const val VOLUME_DUCK = 0.2F
        private const val VOLUME_NORMAL = 1.0F
        private const val CACHE_SIZE = 1024 * 1024 * 100L //100Mb
    }
}