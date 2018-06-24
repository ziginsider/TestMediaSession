package io.github.ziginsider.mediasessiontest

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource

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
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> mediaSessionCallback.onPlay()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaSessionCallback.onPause()
            else -> mediaSessionCallback.onPause()
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        private var currentUri: Uri? = null
        private var currentState = PlaybackStateCompat.STATE_STOPPED

        override fun onPlay() {
            super.onPlay()

            if (!exoPlayer?.playWhenReady!!) {
                startService(Intent(applicationContext, MediaService::class.java))
                val track = musicCatalog.currentTrack
                updateMetadataFromTrack(track)
                prepareToPlay(track.uri)

                if (!audioFocusRequested) {
                    audioFocusRequested = true
                    var audioFocusResult = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        audioFocusResult = audioManager?.requestAudioFocus(audioFocusRequest)!!
                    } else {
                        audioFocusResult = audioManager?.requestAudioFocus(
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

            mediaSession?.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1F).build())
            currentState = PlaybackStateCompat.STATE_PLAYING

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onPause() {
            super.onPause()
        }

        override fun onStop() {
            super.onStop()
        }

        override fun onSkipToNext() {
            super.onSkipToNext()
        }

        override fun onSkipToPrevious() {
            super.onSkipToPrevious()
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
                mediaSessionCallback.onPause()
            }
        }
    }

    companion object {

        private const val NOTIFICATION_ID = 33
        private const val NOTIFICATION_CHANNEL_ID = "media_channel"
    }
}