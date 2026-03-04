package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.util.Log

class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = CustomInCallService.instance
        val activeCall = CallManager.activeCall.value

        Log.d("CallNotificationReceiver", "Action: ${intent.action}")

        when (intent.action) {
            "ACTION_MUTE" -> {
                val audioState = CallManager.audioState.value
                val currentlyMuted = audioState?.isMuted ?: false
                service?.toggleMute(!currentlyMuted)
            }
            "ACTION_SPEAKER" -> {
                val audioState = CallManager.audioState.value
                val currentlyOnSpeaker = (audioState?.route ?: 0) ==
                        android.telecom.CallAudioState.ROUTE_SPEAKER
                service?.toggleSpeaker(!currentlyOnSpeaker)
            }
            "ACTION_END_CALL" -> {
                activeCall?.disconnect()
            }
            "ACTION_ANSWER" -> {
                // Find the ringing call and answer it
                val ringingCall = CallManager.calls.value.firstOrNull { it.state == Call.STATE_RINGING }
                ringingCall?.answer(0)
            }
        }
    }
}
