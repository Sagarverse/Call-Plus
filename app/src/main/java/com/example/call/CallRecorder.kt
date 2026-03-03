package com.example.call

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

class CallRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null

    fun startRecording(fileName: String) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CallRecord")
                }
            }

            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri == null) {
                Log.e("CallRecorder", "Failed to create MediaStore entry")
                return
            }

            val pfd = resolver.openFileDescriptor(uri, "w")
            if (pfd == null) {
                Log.e("CallRecorder", "Failed to open FileDescriptor")
                return
            }

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
            Log.d("CallRecorder", "Started recording call to MediaStore: $uri")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Failed to start call recording", e)
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d("CallRecorder", "Stopped call recording")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Failed to stop call recording", e)
        }
    }

    fun isRecording(): Boolean = mediaRecorder != null
}
