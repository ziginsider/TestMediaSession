package io.github.ziginsider.mediasessiontest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private var mediaServiceBinder: MediaService.MediaServiceBinder? = null

    private var mediaController: MediaControllerCompat? = null
    private var callback: MediaControllerCompat.Callback? = null
    private var serviceConnection: ServiceConnection? = null
    private var bluetoothIntentListener: BluetoothIntentListener? = null
    private var callButtonEventListener: BluetoothIntentListener.CallButtonEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                state?.let {
                    val playing = it.state == PlaybackStateCompat.STATE_PLAYING
                    playButton.isEnabled = !playing
                    pauseButton.isEnabled = playing
                    stopButton.isEnabled = playing

                    when (it.state) {
                        PlaybackStateCompat.STATE_PLAYING -> callbackPlay()
                        PlaybackStateCompat.STATE_PAUSED -> callbackPause()
                        PlaybackStateCompat.STATE_STOPPED -> callbackStop()
                        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> callbackNext()
                        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> callbackPrev()
                        else -> callbackUnknown()
                    }
                }
            }
        }

        serviceConnection = object : ServiceConnection {

            override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
                mediaServiceBinder = service as MediaService.MediaServiceBinder
                try {
                    mediaController = MediaControllerCompat(
                        this@MainActivity,
                        mediaServiceBinder?.getMediaSessionToken()!!
                    )
                    mediaController?.registerCallback(callback as MediaControllerCompat.Callback)
                    callback?.onPlaybackStateChanged(mediaController?.playbackState)
                    mediaController?.transportControls?.play()
                } catch (e: RemoteException) {
                    mediaController = null
                }
            }

            override fun onServiceDisconnected(className: ComponentName?) {
                mediaServiceBinder = null
                if (mediaController != null) {
                    mediaController?.unregisterCallback(callback as MediaControllerCompat.Callback)
                    mediaController = null
                }
            }
        }

        bindService(
            Intent(this, MediaService::class.java),
            serviceConnection!!,
            Context.BIND_AUTO_CREATE
        )

        prevButton.setOnClickListener { previousTrack() }
        playButton.setOnClickListener { playTrack() }
        stopButton.setOnClickListener { stopPlaying() }
        pauseButton.setOnClickListener { pausePlaying() }
        nextButton.setOnClickListener { nextTrack() }

        //prepare to catch bluetooth incoming call button press event
        bluetoothIntentListener = BluetoothIntentListener.getInstance(this)

        callButtonEventListener = object : BluetoothIntentListener.CallButtonEventListener {
            override fun answerOrHangoutButtonEvent() {
                outputTextView.append(">>> Hangup of bluetooth headset pressed <<<\n")
            }
        }

        bluetoothIntentListener?.init(callButtonEventListener)
    }

    private fun nextTrack() {
        mediaController?.transportControls?.skipToNext()
    }

    private fun pausePlaying() {
        mediaController?.transportControls?.pause()
    }

    private fun stopPlaying() {
        mediaController?.transportControls?.stop()
    }

    private fun playTrack() {
        mediaController?.transportControls?.play()
    }

    private fun previousTrack() {
        mediaController?.transportControls?.skipToPrevious()
    }

    private fun callbackNext() {
        val description = mediaController?.metadata?.description ?: return
        outputTextView.append("next track ${description.title} was chosen...\n")
        cover.setImageBitmap(description.iconBitmap)
        title_track.text = description.title
        buttonChangeColor(BUTTON_NEXT)
    }

    private fun callbackPause() {
        val description = mediaController?.metadata?.description ?: return
        outputTextView.append("track ${description.title} was paused...\n")
        buttonChangeColor(BUTTON_PAUSE)
    }

    private fun callbackStop() {
        val description = mediaController?.metadata?.description ?: return
        outputTextView.append("track ${description.title} was stopped...\n")
        buttonChangeColor(BUTTON_STOP)
    }

    private fun callbackPlay() {
        val description = mediaController?.metadata?.description ?: return
        outputTextView.append("track ${description.title} is playing...\n")
        cover.setImageBitmap(description.iconBitmap)
        title_track.text = description.title
        buttonChangeColor(BUTTON_PLAY)
    }

    private fun callbackPrev() {
        val description = mediaController?.metadata?.description ?: return
        outputTextView.append("previous track ${description.title} was chosen...\n")
        cover.setImageBitmap(description.iconBitmap)
        title_track.text = description.title
        buttonChangeColor(BUTTON_PREVIOUS)
    }

    private fun callbackUnknown() {
        outputTextView.append("Unknown playback state change...\n")
    }

    fun buttonChangeColor(typeButton: Int) {
        pauseButton.setBackgroundResource(android.R.drawable.btn_default)
        playButton.setBackgroundResource(android.R.drawable.btn_default)
        stopButton.setBackgroundResource(android.R.drawable.btn_default)
        nextButton.setBackgroundResource(android.R.drawable.btn_default)
        prevButton.setBackgroundResource(android.R.drawable.btn_default)
        when (typeButton) {
            BUTTON_PLAY -> playButton.setBackgroundResource(R.color.colorButtonClick)
            BUTTON_PAUSE -> pauseButton.setBackgroundResource(R.color.colorButtonClick)
            BUTTON_STOP -> stopButton.setBackgroundResource(R.color.colorButtonClick)
            BUTTON_NEXT -> nextButton.setBackgroundResource(R.color.colorButtonClick)
            BUTTON_PREVIOUS -> prevButton.setBackgroundResource(R.color.colorButtonClick)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaServiceBinder = null
        if (mediaController != null) {
            mediaController?.unregisterCallback(callback as MediaControllerCompat.Callback)
            mediaController = null
        }
        unbindService(serviceConnection!!)
        bluetoothIntentListener?.destroy()
    }

    companion object {

        private const val BUTTON_PLAY = 1
        private const val BUTTON_STOP = 2
        private const val BUTTON_PAUSE = 3
        private const val BUTTON_NEXT = 4
        private const val BUTTON_PREVIOUS = 5
    }
}
