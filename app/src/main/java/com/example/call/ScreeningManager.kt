package com.example.call

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * ScreeningManager handles the AI Call Screening flow:
 * 1. Plays a TTS greeting to the caller.
 * 2. Uses transcription logic (via VoicemailManager) to show what they say.
 */
class ScreeningManager(private val context: Context, private val voicemailManager: VoicemailManager) {
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _isScreening = MutableStateFlow(false)
    val isScreening: StateFlow<Boolean> = _isScreening

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }
    }

    fun startScreening(number: String) {
        _isScreening.value = true
        
        // Start transcription
        voicemailManager.startRecording(number, "screening_${System.currentTimeMillis()}")
        
        // Play greeting after a short delay to ensure call is connected and speaker is on
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isTtsReady) {
                Log.d("ScreeningManager", "Playing AI greeting to $number")
                tts?.speak(
                    "Hi, I'm an AI assistant. Who is calling and what is this regarding?",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "screening_greeting"
                )
            }
        }, 1500)
    }

    fun stopScreening() {
        _isScreening.value = false
        voicemailManager.stopRecording()
    }

    fun cleanup() {
        tts?.stop()
        tts?.shutdown()
    }
}
