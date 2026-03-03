package com.example.call

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class VoicemailManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private var currentTranscriptUri: Uri? = null

    fun startRecording(fileName: String) {
        _transcript.value = ""
        try {
            val resolver = context.contentResolver
            val audioValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Voicemail")
                }
            }

            val audioUri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, audioValues)
                ?: resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioValues)

            if (audioUri == null) {
                Log.e("VoicemailManager", "Failed to create MediaStore entry for audio")
                return
            }

            val pfd = resolver.openFileDescriptor(audioUri, "w")
            if (pfd == null) {
                Log.e("VoicemailManager", "Failed to open FileDescriptor for audio")
                return
            }

            // Prepare transcript file in MediaStore
            val txtValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Voicemail")
                }
            }
            currentTranscriptUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, txtValues)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(pfd.fileDescriptor)
                prepare()
                start()
            }
            Log.d("VoicemailManager", "Started recording: $audioUri")
            
            Handler(Looper.getMainLooper()).post {
                startTranscription()
            }
        } catch (e: Exception) {
            Log.e("VoicemailManager", "Failed to start recording", e)
        }
    }

    private fun startTranscription() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e("VoicemailManager", "Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    Log.e("VoicemailManager", "Transcription error: $error")
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _transcript.value += matches[0] + " "
                        saveTranscript()
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _transcript.value = _transcript.value.substringBeforeLast(". ") + matches[0]
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoicemailManager", "Failed to start speech recognizer", e)
        }
    }

    private fun saveTranscript() {
        currentTranscriptUri?.let { uri ->
            try {
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(_transcript.value.trim().toByteArray())
                }
            } catch (e: Exception) {
                Log.e("VoicemailManager", "Failed to save transcript", e)
            }
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e("VoicemailManager", "Failed to stop recording", e)
        }

        Handler(Looper.getMainLooper()).post {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            saveTranscript()
        }
    }
}
