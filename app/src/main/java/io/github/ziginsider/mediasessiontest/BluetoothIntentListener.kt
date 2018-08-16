package io.github.ziginsider.mediasessiontest

import android.bluetooth.BluetoothHeadset
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.bluetooth.BluetoothProfile
import android.media.AudioManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.util.Log


open class BluetoothIntentListener private constructor(private val context: Context) {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHeadset: BluetoothHeadset? = null
    private lateinit var mAudioManager: AudioManager
    private var callEventListener: CallButtonEventListener? = null

    private var headsetProfileListener: BluetoothProfile.ServiceListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceDisconnected(profile: Int) {
                try {
                    context.unregisterReceiver(headsetBroadcastReceiver)
                    bluetoothHeadset = null
                } catch (il: IllegalArgumentException) {
                    Log.i(TAG, "Headset broadcast receiver wasn't registered yet.")
                }

            }

            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                bluetoothHeadset = proxy as BluetoothHeadset
                context.registerReceiver(
                    headsetBroadcastReceiver,
                    IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                )
                val f = IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
                f.priority = Integer.MAX_VALUE
                context.registerReceiver(headsetBroadcastReceiver, f)
            }
        }

    protected var headsetBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val state: Int

            if (action == BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED) {
                state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED)
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    Log.d(TAG, "headsetBroadcastReceiver: Connected")
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                    // Hangup of bluetooth headset pressed.
                    Log.d(TAG, ">>>>>>>>>>> Hangup of bluetooth headset pressed <<<<<<<<<<<<")
                    callEventListener?.answerOrHangoutButtonEvent()
                }
            }
        }
    }

    fun init(callButtonEventListener: CallButtonEventListener?) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        Log.d(TAG, "init(): adapter name = ${bluetoothAdapter?.name}\n" +
                "address = ${bluetoothAdapter?.address}\n" +
                "device = ${bluetoothAdapter?.bondedDevices?.forEach { it.name }}")
        if (bluetoothAdapter != null) {
            callEventListener = callButtonEventListener
            mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (mAudioManager.isBluetoothScoAvailableOffCall) {
                bluetoothAdapter!!.getProfileProxy(context, headsetProfileListener, BluetoothProfile.HEADSET)
            }
        }
    }

    fun destroy() {
        headsetProfileListener.onServiceDisconnected(BluetoothProfile.HEADSET)
    }

    interface CallButtonEventListener {
        fun answerOrHangoutButtonEvent()
    }

    companion object {
        private val TAG = "BluetoothIntent"
        private var bluetoothIntentListener: BluetoothIntentListener? = null

        fun getInstance(context: Context): BluetoothIntentListener {
            Log.d(TAG, "getInstance() invoke")
            if (bluetoothIntentListener == null) {
                bluetoothIntentListener = BluetoothIntentListener(context)
            }
            return bluetoothIntentListener as BluetoothIntentListener
        }
    }
}