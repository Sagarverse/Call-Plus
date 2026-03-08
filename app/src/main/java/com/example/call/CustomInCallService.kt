package com.example.call

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import com.example.call.util.ShakeDetector

class CustomInCallService : InCallService() {
    private val CHANNEL_ID = "call_channel"
    private val CHANNEL_ID_INCOMING = "incoming_call_channel"
    private val NOTIFICATION_ID = 123
    private val INCOMING_NOTIFICATION_ID = 124
    private var shakeDetector: ShakeDetector? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isFlipped = false

    companion object {
        var instance: CustomInCallService? = null
        private const val TAG = "InCallService"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannels()
        setupShakeDetector()
        Log.d(TAG, "InCallService created")
    }

    private fun setupShakeDetector() {
        val prefs = getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("shake_enabled", false)) {
            shakeDetector = ShakeDetector(this) {
                handleShake()
            }
            shakeDetector?.start()
        }
    }

    private fun handleShake() {
        CallManager.activeCall.value?.let { call ->
            when (call.state) {
                Call.STATE_RINGING -> call.answer(0)
                Call.STATE_ACTIVE -> call.disconnect()
                else -> {}
            }
        }
    }

    private val flipListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            
            val z = event.values[2]
            // Z < -7.0 means face down
            val flippedCurrent = z < -7.0f
            
            if (flippedCurrent && !isFlipped) {
                // Just flipped face down
                CallManager.activeCall.value?.let { call ->
                    if (call.state == Call.STATE_RINGING) {
                        Log.d(TAG, "Flip detected, silencing call")
                        silenceRinger()
                    }
                }
            }
            isFlipped = flippedCurrent
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun silenceRinger() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // For modern Android, we can just set to silent/vibrate or use TelecomManager silenceRinger
                // telecomManager.silenceRinger() is easiest if we have the reference
                @Suppress("MissingPermission")
                (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.silenceRinger()
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error silencing ringer", e)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this

        Log.d(TAG, "Call added: state=${call.state} number=${call.details.handle?.schemeSpecificPart}")

        // Check blacklist — silently reject blocked numbers
        val number = call.details.handle?.schemeSpecificPart
        if (com.example.call.data.BlacklistRepository(this).isBlocked(number)) {
            Log.d(TAG, "Blocking call from $number")
            call.disconnect()
            return
        }

        CallManager.addCall(call)

        val videoCallback = object : android.telecom.InCallService.VideoCall.Callback() {
            override fun onSessionModifyRequestReceived(videoProfile: android.telecom.VideoProfile?) {
                Log.d(TAG, "Video modify request received")
                // Automatically accept video requests for this demo
                val response = android.telecom.VideoProfile(videoProfile?.videoState ?: android.telecom.VideoProfile.STATE_BIDIRECTIONAL)
                call.videoCall?.sendSessionModifyResponse(response)
            }
            override fun onSessionModifyResponseReceived(status: Int, requestedProfile: android.telecom.VideoProfile?, responseProfile: android.telecom.VideoProfile?) {}
            override fun onCallSessionEvent(event: Int) {}
            override fun onPeerDimensionsChanged(width: Int, height: Int) {}
            override fun onVideoQualityChanged(videoQuality: Int) {}
            override fun onCallDataUsageChanged(dataUsage: Long) {}
            override fun onCameraCapabilitiesChanged(cameraCapabilities: android.telecom.VideoProfile.CameraCapabilities?) {}
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call?, state: Int) {
                Log.d(TAG, "Call state changed: $state")
                c?.let { showCallNotification(it) }
                // Cancel incoming notification once answered or connected
                if (state != Call.STATE_RINGING) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(INCOMING_NOTIFICATION_ID)
                }
            }
            override fun onVideoCallChanged(c: Call?, videoCall: android.telecom.InCallService.VideoCall?) {
                videoCall?.registerCallback(videoCallback)
            }
        }
        call.videoCall?.registerCallback(videoCallback)
        call.registerCallback(callback)

        if (call.state == Call.STATE_RINGING) {
            showIncomingCallNotification(call)
            accelerometer?.let {
                sensorManager.registerListener(flipListener, it, SensorManager.SENSOR_DELAY_UI)
            }
        } else {
            showCallNotification(call)
        }

        // Bring our UI to the foreground.
        // Use singleTop + clear top so we never stack multiple MainActivity instances.
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity for incoming call", e)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
        CallManager.removeCall(call)
        sensorManager.unregisterListener(flipListener)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(INCOMING_NOTIFICATION_ID)

        if (CallManager.calls.value.isEmpty()) {
            instance = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            nm.cancel(NOTIFICATION_ID)
        } else {
            CallManager.activeCall.value?.let { showCallNotification(it) }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "InCallService destroyed")
        shakeDetector?.stop()
        instance = null
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudioState(audioState)
        CallManager.activeCall.value?.let { showCallNotification(it) }
    }

    // ─── Notification helpers ─────────────────────────────────────────────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Active call — low priority (ongoing)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active Call", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing call controls"
                }
            )
            // Incoming call — high priority (heads-up / full-screen)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_INCOMING, "Incoming Call", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Incoming call alerts"
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    /**
     * Full-screen high-priority notification for incoming calls.
     * On API 29+ this fires a full-screen intent that shows the app over the lock screen.
     */
    private fun showIncomingCallNotification(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val fullScreenPI = PendingIntent.getActivity(
            this, INCOMING_NOTIFICATION_ID, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPI = createBroadcastPI("ACTION_ANSWER", "answer")
        val declinePI = createBroadcastPI("ACTION_END_CALL", "decline")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming Call")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPI, true)
            .setContentIntent(fullScreenPI)
            .addAction(0, "Answer", answerPI)
            .addAction(0, "Decline", declinePI)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.app.ServiceCompat.startForeground(this, INCOMING_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(INCOMING_NOTIFICATION_ID, notification)
        }
    }

    private fun showCallNotification(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val audioState = CallManager.audioState.value
        val isMuted = audioState?.isMuted ?: false
        val isSpeaker = (audioState?.route ?: 0) == CallAudioState.ROUTE_SPEAKER

        val openPI = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Active Call")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openPI)
            .addAction(0, if (isMuted) "Unmute" else "Mute", createBroadcastPI("ACTION_MUTE", "mute"))
            .addAction(0, if (isSpeaker) "Earpiece" else "Speaker", createBroadcastPI("ACTION_SPEAKER", "speaker"))
            .addAction(0, "End Call", createBroadcastPI("ACTION_END_CALL", "end"))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createBroadcastPI(action: String, tag: String): PendingIntent {
        val intent = Intent(this, CallNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ─── Public API for CallingScreen ─────────────────────────────────────────

    fun toggleMute(mute: Boolean) = setMuted(mute)

    fun toggleSpeaker(speaker: Boolean) {
        val route = if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        setAudioRoute(route)
    }
}
