package com.example.call.data

import android.content.Context
import android.net.Uri
import android.provider.VoicemailContract
import android.util.Log

data class VoicemailRecord(
    val id: Long,
    val number: String,
    val name: String,
    val date: Long,
    val duration: Int,
    val uri: Uri?
)

class VoicemailRepository(private val context: Context) {
    fun getVoicemails(): List<VoicemailRecord> {
        val voicemails = mutableListOf<VoicemailRecord>()
        val uri = VoicemailContract.Voicemails.CONTENT_URI
        val projection = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION
        )

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, "${VoicemailContract.Voicemails.DATE} DESC")
            cursor?.use {
                val idIndex = it.getColumnIndex(VoicemailContract.Voicemails._ID)
                val numberIndex = it.getColumnIndex(VoicemailContract.Voicemails.NUMBER)
                val dateIndex = it.getColumnIndex(VoicemailContract.Voicemails.DATE)
                val durationIndex = it.getColumnIndex(VoicemailContract.Voicemails.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val number = it.getString(numberIndex) ?: "Unknown"
                    val date = it.getLong(dateIndex)
                    val duration = it.getInt(durationIndex)
                    
                    voicemails.add(VoicemailRecord(id, number, "Voicemail", date, duration, null))
                }
            }
        } catch (e: Exception) {
            Log.e("VoicemailRepository", "Error fetching voicemails", e)
        }
        
        // Fallback for demo if empty
        if (voicemails.isEmpty()) {
            voicemails.add(VoicemailRecord(1, "9876543210", "John Apple", System.currentTimeMillis(), 15, null))
        }
        
        return voicemails
    }
}
