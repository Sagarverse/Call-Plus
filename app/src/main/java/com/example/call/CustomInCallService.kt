package com.example.call

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.call.util.ShakeDetector

class CustomInCallService : InCallService() {
    private val CHANNEL_ID = "call_channel"
    private val NOTIFICATION_ID = 123
    private var shakeDetector: ShakeDetector? = null

    companion object {
        var instance: CustomInCallService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        setupShakeDetector()
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
                Call.STATE_RINGING -> {
                    call.answer(0)
                }
                Call.STATE_ACTIVE -> {
                    call.disconnect()
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        
        // Check blacklist
        val number = call.details.handle?.schemeSpecificPart
        if (com.example.call.data.BlacklistRepository(this).isBlocked(number)) {
            call.disconnect()
            return
        }

        CallManager.addCall(call)
        showCallNotification(call)
        
        // Ensure our custom UI pops up when a call starts
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.removeCall(call)
        if (CallManager.calls.value.isEmpty()) {
            instance = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } else {
            CallManager.activeCall.value?.let { showCallNotification(it) }
        }
    }

    override fun onDestroy() {
        shakeDetector?.stop()
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudioState(audioState)
        CallManager.activeCall.value?.let { showCallNotification(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Active Call"
            val descriptionText = "Call Controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showCallNotification(call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val audioState = CallManager.audioState.value
        val isMuted = audioState?.isMuted ?: false
        val isSpeaker = (audioState?.route ?: 0) == CallAudioState.ROUTE_SPEAKER

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val muteAction = createAction("ACTION_MUTE", if (isMuted) "Unmute" else "Mute")
        val speakerAction = createAction("ACTION_SPEAKER", if (isSpeaker) "Earpiece" else "Speaker")
        val endAction = createAction("ACTION_END_CALL", "End Call")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Active Call")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, muteAction.title, muteAction.actionIntent)
            .addAction(0, speakerAction.title, speakerAction.actionIntent)
            .addAction(0, endAction.title, endAction.actionIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createAction(action: String, title: String): NotificationCompat.Action {
        val intent = Intent(this, CallNotificationReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(0, title, pendingIntent).build()
    }

    fun toggleMute(mute: Boolean) {
        setMuted(mute)
    }

    fun toggleSpeaker(speaker: Boolean) {
        val route = if (speaker) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        setAudioRoute(route)
    }
}
