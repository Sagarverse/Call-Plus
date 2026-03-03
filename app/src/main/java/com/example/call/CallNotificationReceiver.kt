package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val service = CustomInCallService.instance ?: return

        when (action) {
            "ACTION_MUTE" -> {
                val audioState = CallManager.audioState.value
                val isMuted = audioState?.isMuted ?: false
                service.toggleMute(!isMuted)
            }
            "ACTION_SPEAKER" -> {
                val audioState = CallManager.audioState.value
                val isSpeakerOn = (audioState?.route ?: 0) == android.telecom.CallAudioState.ROUTE_SPEAKER
                service.toggleSpeaker(!isSpeakerOn)
            }
            "ACTION_END_CALL" -> {
                CallManager.activeCall.value?.disconnect()
            }
        }
    }
}
