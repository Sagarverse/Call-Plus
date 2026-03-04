package com.example.call

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * CallRecorder — records both sides of a phone call to the device's Downloads/CallRecordings folder.
 *
 * Key design decisions:
 *  - On Android Q+ (API 29) we write into MediaStore via a ParcelFileDescriptor (scoped storage).
 *  - On older devices we write directly to getExternalFilesDir which needs no extra permission.
 *  - AudioSource.VOICE_CALL captures both microphone AND earpiece/speaker paths for real phone calls.
 *  - AudioSource.VOICE_COMMUNICATION is for VoIP only and will record nothing useful on GSM calls.
 *  - setOnErrorListener + setOnInfoListener prevent silent RecordingService crashes.
 *  - IS_PENDING flag used on Q+ so the file is invisible to other apps until recording is complete.
 */
class CallRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var pendingUri: Uri? = null       // MediaStore URI (Q+)
    private var legacyFile: File? = null       // File path (pre-Q)

    /** The human-readable path shown in the UI after recording stops. */
    var lastSavedPath: String = ""
        private set

    /** True if a recording is currently in progress. */
    fun isRecording(): Boolean = mediaRecorder != null

    /**
     * Start recording a call.
     * @param fileName  Base name (no extension) for the output file.
     * @return true if recording started successfully, false otherwise.
     */
    fun startRecording(fileName: String): Boolean {
        if (mediaRecorder != null) {
            Log.w(TAG, "startRecording called while already recording — ignoring")
            return false
        }

        return try {
            val recorder = createRecorder()
            configureRecorder(recorder, fileName)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            Log.d(TAG, "Recording started: $lastSavedPath")
            true
        } catch (e: IOException) {
            Log.e(TAG, "prepare() failed", e)
            releaseRecorder()
            false
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            releaseRecorder()
            false
        }
    }

    /**
     * Stop the current recording, finalize the file, and clear pending MediaStore flags.
     * @return true if stopped cleanly, false if there was nothing to stop.
     */
    fun stopRecording(): Boolean {
        val recorder = mediaRecorder ?: return false
        return try {
            recorder.stop()
            Log.d(TAG, "Recording stopped: $lastSavedPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "stop() failed", e)
            false
        } finally {
            releaseRecorder()
            finalizePendingUri()
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()

    private fun configureRecorder(recorder: MediaRecorder, fileName: String) {
        // VOICE_CALL records both ends on GSM/CDMA calls (requires RECORD_AUDIO permission).
        // On some low-end OEMs this source may be restricted — we catch the error upstream.
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(44100)
        recorder.setAudioEncodingBitRate(128000)
        recorder.setMaxDuration(3600_000) // 1-hour safety cap

        recorder.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaRecorder error: what=$what extra=$extra")
            releaseRecorder()
        }
        recorder.setOnInfoListener { _, what, extra ->
            Log.d(TAG, "MediaRecorder info: what=$what extra=$extra")
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                stopRecording()
            }
        }

        setOutputFile(recorder, "$fileName.m4a")
    }

    private fun setOutputFile(recorder: MediaRecorder, displayName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage path — no WRITE_EXTERNAL_STORAGE needed
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CallRecordings")
                put(MediaStore.Audio.Media.IS_PENDING, 1) // hidden until we finalize
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                throw IOException("Failed to create MediaStore entry for $displayName")
            }
            pendingUri = uri
            lastSavedPath = "Downloads/CallRecordings/$displayName"

            val pfd = context.contentResolver.openFileDescriptor(uri, "w")
                ?: throw IOException("Failed to open file descriptor for $uri")
            recorder.setOutputFile(pfd.fileDescriptor)
            pfd.close()
        } else {
            // Pre-Q: write to app-specific external dir (no permission needed)
            val dir = context.getExternalFilesDir("CallRecordings")
                ?: context.filesDir
            dir.mkdirs()
            val file = File(dir, displayName)
            legacyFile = file
            lastSavedPath = file.absolutePath
            recorder.setOutputFile(file.absolutePath)
        }
    }

    /** Clears IS_PENDING on API 29+ so the file becomes visible to other apps (Files app, etc.). */
    private fun finalizePendingUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingUri?.let { uri ->
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "MediaStore entry finalized: $uri")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to finalize MediaStore entry", e)
                } finally {
                    pendingUri = null
                }
            }
        }
        legacyFile = null
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    companion object {
        private const val TAG = "CallRecorder"
    }
}
