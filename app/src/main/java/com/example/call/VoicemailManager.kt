package com.example.call

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.CallAudioState
import android.util.Log
import com.example.call.data.ContactRepository
import com.example.call.data.LocalVoicemailRecord
import com.example.call.data.LocalVoicemailStorage
import com.example.call.data.findContactFlexible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * VoicemailManager — handles the full silent voicemail flow:
 *
 * 1. App answers incoming call silently (no audible greeting)
 * 2. Switches to speakerphone so caller's voice comes through the speaker
 * 3. Records using MIC audio source (picks up caller's voice via speaker-to-mic path)
 * 4. Runs real-time speech recognition to build a transcript
 * 5. On call end: saves the recording + transcript to LocalVoicemailStorage
 * 6. The Voicemail tab reads from LocalVoicemailStorage → shows the entry
 *
 * Why MIC instead of VOICE_COMMUNICATION?
 *  - VOICE_COMMUNICATION applies echo cancellation which suppresses speaker audio
 *  - MIC with speakerphone catches the caller's voice more reliably
 */
class VoicemailManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val localStorage = LocalVoicemailStorage(context)

    // Live transcript exposed to CallingScreen UI
    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    // Recording state
    private var currentFilePath: String = ""
    private var currentNumber: String = ""
    private var recordingStartTime: Long = 0L
    private var recordId: Long = 0L

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Call this when user taps "Voicemail" on the incoming call screen.
     * The call must already be answered before calling this.
     *
     * @param callerNumber  The caller's phone number
     * @param fileName      Base filename for the audio file (no extension)
     */
    fun startRecording(callerNumber: String, fileName: String) {
        if (mediaRecorder != null) {
            Log.w(TAG, "Already recording — ignoring startRecording call")
            return
        }

        _transcript.value = ""
        currentNumber = callerNumber
        recordingStartTime = System.currentTimeMillis()
        recordId = recordingStartTime // use timestamp as unique id

        // Build output file in app-private external dir — no permission needed
        val dir = context.getExternalFilesDir("Voicemail") ?: context.filesDir
        dir.mkdirs()
        val file = File(dir, "$fileName.m4a")
        currentFilePath = file.absolutePath

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(context)
            else
                @Suppress("DEPRECATION") MediaRecorder()

            recorder.apply {
                // MIC source: picks up speaker output when speakerphone is ON
                // No echo cancellation → caller's voice from speaker leaks into mic
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(currentFilePath)
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Recorder error: what=$what extra=$extra")
                }
                prepare()
                start()
            }
            mediaRecorder = recorder
            Log.d(TAG, "Voicemail recording started: $currentFilePath")

            // Create a placeholder record immediately (so it shows in the tab ASAP)
            saveRecord(durationSeconds = 0, transcript = "Recording...")

            // Start transcription on main thread (SpeechRecognizer requirement)
            mainHandler.post { startTranscription() }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voicemail recording", e)
        }
    }

    /**
     * Call this when the call disconnects or user ends it.
     * Finalizes the audio file and saves the complete record.
     */
    fun stopRecording() {
        val recorder = mediaRecorder ?: return

        val durationMs = System.currentTimeMillis() - recordingStartTime
        val durationSec = (durationMs / 1000).toInt()

        try {
            recorder.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Recorder stop error", e)
        } finally {
            recorder.release()
            mediaRecorder = null
        }

        // Stop speech recognizer
        mainHandler.post {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null

            // Save finalized record
            saveRecord(durationSec, _transcript.value.trimEnd())
            Log.d(TAG, "Voicemail saved — duration=${durationSec}s path=$currentFilePath")
        }
    }

    fun isRecording(): Boolean = mediaRecorder != null

    // ─── Private: Speech Recognition ──────────────────────────────────────────

    private fun startTranscription() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { restartListening() }

                override fun onError(error: Int) {
                    Log.d(TAG, "Recognition error $error — restarting")
                    // Auto-restart unless we're done recording
                    if (mediaRecorder != null) restartListening()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _transcript.value = (_transcript.value.trimEnd() + " " + matches[0]).trim()
                        // Update stored transcript in real-time
                        localStorage.updateTranscript(recordId, _transcript.value)
                    }
                    if (mediaRecorder != null) restartListening()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        // Show partial results live (overwrite last partial, keep confirmed)
                        val confirmed = _transcript.value
                        _transcript.value = if (confirmed.isEmpty()) matches[0]
                                           else "$confirmed ${matches[0]}"
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        listenOnce()
    }

    private fun listenOnce() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
        }
    }

    private fun restartListening() {
        mainHandler.postDelayed({ listenOnce() }, 300)
    }

    // ─── Private: Storage ─────────────────────────────────────────────────────

    private fun saveRecord(durationSeconds: Int, transcript: String) {
        // Try to resolve contact name from number
        val contactName: String = try {
            val repo = ContactRepository(context)
            // Run synchronously on calling thread (storage op, fast)
            val contacts = runCatching { 
                android.database.MatrixCursor(arrayOf("name")).let { currentNumber }
            }.getOrDefault(currentNumber)
            contacts
        } catch (e: Exception) { currentNumber }

        // Look up contact name properly
        val resolvedName = resolveContactName(currentNumber)

        val record = LocalVoicemailRecord(
            id = recordId,
            number = currentNumber,
            name = resolvedName,
            date = recordingStartTime,
            duration = durationSeconds,
            filePath = currentFilePath,
            transcript = transcript.ifEmpty { "(No speech detected)" }
        )
        localStorage.saveVoicemail(record)
    }

    private fun resolveContactName(number: String): String {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else number
            } ?: number
        } catch (e: Exception) { number }
    }

    companion object {
        private const val TAG = "VoicemailManager"
    }
}
