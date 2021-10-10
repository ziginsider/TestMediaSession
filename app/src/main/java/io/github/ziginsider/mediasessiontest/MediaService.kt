package io.github.ziginsider.mediasessiontest

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.PRIORITY_HIGH
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Clock
import java.io.File
import android.media.browse.MediaBrowser.MediaItem.FLAG_PLAYABLE as FLAG_PL

class MediaService : MediaBrowserServiceCompat() {

    private val stateBuilder = PlaybackStateCompat.Builder().setActions(
        PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
    )

    private val metadataBuilder = MediaMetadataCompat.Builder()
    private var _catalog: TestMusicCatalog? = null
    private val musicCatalog: TestMusicCatalog by lazy { getMusicccCatalog() }

    private fun getMusicccCatalog(): TestMusicCatalog {
        return if (_catalog == null) {
            val catalog = TestMusicCatalog(this.applicationContext)
            _catalog = catalog
            catalog
        } else {
            requireNotNull(_catalog)
        }
    }

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
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Player controls",
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        mediaSession?.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession?.setCallback(mediaSessionCallback)

        val activityIntent = Intent(applicationContext, MainActivity::class.java)
        mediaSession?.setSessionActivity(
            PendingIntent.getActivity(
                applicationContext, 0,
                activityIntent, 0
            )
        )

        val mediaButtonIntent = Intent(
            Intent.ACTION_MEDIA_BUTTON, null, applicationContext,
            MediaButtonReceiver::class.java
        )
        mediaSession?.setMediaButtonReceiver(
            PendingIntent.getBroadcast(
                applicationContext, 0,
                mediaButtonIntent, 0
            )
        )

        // Build a HttpDataSource.Factory with cross-protocol redirects enabled.
        val httpDataSourceFactory2: HttpDataSource.Factory =
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

        val cache = SimpleCache(
            File(this.cacheDir.absolutePath + "/exoplayer"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
        )

        dataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory2)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        exoPlayer = SimpleExoPlayer.Builder(
            this,
            DefaultRenderersFactory(this),
            DefaultTrackSelector(),
            DefaultMediaSourceFactory(dataSourceFactory!!),
            DefaultLoadControl(),
            DefaultBandwidthMeter(),
            AnalyticsCollector(Clock.DEFAULT)
        ).build()

        exoPlayer?.addListener(exoPlayerListener)

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
        if (SERVICE_INTERFACE == intent?.action) {
            return super.onBind(intent)!!
        }
        return MediaServiceBinder()
    }

    inner class MediaServiceBinder : Binder() {
        fun getMediaSessionToken() = mediaSession?.sessionToken
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot("Root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val data = ArrayList<MediaBrowserCompat.MediaItem>(musicCatalog.countTracks)
        val descriptionBuilder = MediaDescriptionCompat.Builder()

        for ((i, track) in musicCatalog.getTrackCatalog().withIndex()) {
            Log.i("TAG", "track = ${track.title}")
            //val track = musicCatalog.getTrackByIndex(i)
            val description = descriptionBuilder
                .setDescription(track.artist)
                .setTitle(track.title)
                .setSubtitle(track.artist)
                .setIconUri(Uri.parse(track.bitmapUri))
                .setMediaId(i.toString())
                .build()
            data.add(MediaBrowserCompat.MediaItem(description, FLAG_PL))
        }
        result.sendResult(data)
    }

    private val exoPlayerListener = object : Player.EventListener {

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
        }

        override fun onPlayerError(error: PlaybackException) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                mediaSessionCallback.onSkipToNext()
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
        }

        override fun onPositionDiscontinuity(reason: Int) {
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> currentAudioFocusState = AUDIO_FOCUSED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                currentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
            }
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
            registerReceiver(
                becomingNoiseReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
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
            try {
                unregisterReceiver(becomingNoiseReceiver)
            } catch (e: IllegalArgumentException) {
                e.stackTrace
            }
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        private var currentUri: Uri? = null
        private var currentState = PlaybackStateCompat.STATE_STOPPED

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            playTrack(musicCatalog.getTrackByIndex(Integer.parseInt(mediaId!!)))
        }

        private fun playTrack(trackByIndex: TestMusicCatalog.JsonTrack) {
            if (!exoPlayer?.playWhenReady!!) {
                startService(Intent(applicationContext, MediaService::class.java))
                //val track = musicCatalog.currentTrack
                updateMetadataFromTrack(trackByIndex)
                prepareToPlay(Uri.parse(trackByIndex.trackUri))

                if (!audioFocusRequested) {
                    audioFocusRequested = true
                    val audioFocusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioManager?.requestAudioFocus(audioFocusRequest!!)!!
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
                registerReceiver(
                    becomingNoiseReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
                exoPlayer?.playWhenReady = true
            }

            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onPlay() {
            playTrack(musicCatalog.currentTrack)
        }

        override fun onPause() {
            if (exoPlayer?.playWhenReady!!) {
                exoPlayer?.playWhenReady = false
                unregisterReceiver(becomingNoiseReceiver)
            }

            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)

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
                    audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                } else {
                    audioManager?.abandonAudioFocus(audioFocusChangeListener)
                }
            }

            mediaSession?.isActive = false

            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)

            refreshNotificationAndForegroundStatus(currentState)

            stopSelf()
        }

        override fun onSkipToNext() {
            val track = musicCatalog.next()
            updateMetadataFromTrack(track)

            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1F
                ).build()
            )
            currentState = PlaybackStateCompat.STATE_SKIPPING_TO_NEXT

            prepareToPlay(Uri.parse(track.trackUri))

            if (exoPlayer?.playWhenReady!!) {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            } else {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSkipToPrevious() {
            val track = musicCatalog.previous()
            updateMetadataFromTrack(track)

            updatePlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS)

            prepareToPlay(Uri.parse(track.trackUri))

            if (exoPlayer?.playWhenReady!!) {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            } else {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            }

            refreshNotificationAndForegroundStatus(currentState)
        }

        private fun updatePlaybackState(playbackState: Int) {
            mediaSession?.setPlaybackState(
                stateBuilder.setState(
                    playbackState,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1F
                ).build()
            )
            currentState = playbackState
        }

        private fun prepareToPlay(uri: Uri) {
            if (uri != currentUri) {
                currentUri = uri
                val mediaSource =
                    ProgressiveMediaSource.Factory(dataSourceFactory!!, extractorsFactory!!)
                        .createMediaSource(MediaItem.fromUri(uri))
                exoPlayer?.apply {
                    setMediaSource(mediaSource)
                    prepare()
                }
            }
        }

        private fun updateMetadataFromTrack(track: TestMusicCatalog.JsonTrack) {
            with(metadataBuilder) {
                putBitmap(
                    MediaMetadataCompat.METADATA_KEY_ART,
                    musicCatalog.bitmaps[track.bitmapUri]
                )
                putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.artist)
                putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)
            }
            mediaSession?.setMetadata(metadataBuilder.build())
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query == "play next") {
                Log.d("TAG", ">>>>>>>>>>>>> play next")
            }
            when (query) {
                "media next" -> onSkipToNext()
                "media previous" -> onSkipToPrevious()
                "media stop" -> onStop()
                "media pause" -> onPause()
                "media play" -> onPlay()
            }
            super.onPlayFromSearch(query, extras)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            Log.d("TAG", ">>>>>>>>>>>>> onCustomAction $action")
            super.onCustomAction(action, extras)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            Log.d("TAG", ">>>>>>>>>>>>> onMediaButtonEvent ${mediaButtonEvent?.action}")
            return super.onMediaButtonEvent(mediaButtonEvent)
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
            PlaybackStateCompat.STATE_PLAYING -> startForeground(
                NOTIFICATION_ID,
                getNotification(playbackState)
            )
            PlaybackStateCompat.STATE_PAUSED -> {
                NotificationManagerCompat.from(this@MediaService).notify(
                    NOTIFICATION_ID,
                    getNotification(playbackState)
                )
                stopForeground(false)
            }
            else -> stopForeground(true)
        }
    }

    private fun getNotification(playbackState: Int): Notification {
        // Get the session's metadata
        val controller = mediaSession?.controller
        val mediaMetadata = controller?.metadata
        val description = mediaMetadata?.description


        val builder = NotificationCompat.Builder(this, "channelId111")
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_previous,
                "previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
        )

        if (playbackState == PlaybackStateCompat.STATE_PLAYING) {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PAUSE
                    )
                )
            )
        } else {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play,
                    "play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_PLAY
                    )
                )
            )
        }

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_next,
                "next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
        )


        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
                .setMediaSession(mediaSession?.sessionToken)
        )

        builder.setSmallIcon(R.mipmap.ic_launcher)
        builder.color = ContextCompat.getColor(this, R.color.colorPrimaryDark)
        builder.setShowWhen(false)
        builder.priority = PRIORITY_HIGH
        builder.setOnlyAlertOnce(true)
        builder.setChannelId(NOTIFICATION_CHANNEL_ID)

        builder
            .setContentTitle(description!!.title)
            .setContentText(description.subtitle)
            .setSubText(description.description)
            .setLargeIcon(description.iconBitmap)
            .setContentIntent(controller.sessionActivity)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_STOP
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

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