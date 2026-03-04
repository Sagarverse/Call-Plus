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
    val uri: Uri?,
    val transcript: String = "",
    val isLocal: Boolean = false   // true = recorded by our app, false = carrier
)

class VoicemailRepository(private val context: Context) {

    private val localStorage = LocalVoicemailStorage(context)

    fun getVoicemails(): List<VoicemailRecord> {
        // Merge: our local recordings (always available) + carrier VVM (if supported)
        val local = localStorage.loadAll().map { it.toVoicemailRecord() }
        val carrier = getCarrierVoicemails()
        // Deduplicate by id — local takes priority; sort newest first
        return (local + carrier)
            .distinctBy { it.id }
            .sortedByDescending { it.date }
    }

    fun deleteLocalVoicemail(id: Long) {
        localStorage.deleteById(id)
    }

    private fun getCarrierVoicemails(): List<VoicemailRecord> {
        val voicemails = mutableListOf<VoicemailRecord>()
        val uri = VoicemailContract.Voicemails.CONTENT_URI
        val projection = arrayOf(
            VoicemailContract.Voicemails._ID,
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
            VoicemailContract.Voicemails.DURATION
        )
        try {
            val cursor = context.contentResolver.query(
                uri, projection, null, null,
                "${VoicemailContract.Voicemails.DATE} DESC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(VoicemailContract.Voicemails._ID)
                val numIdx = it.getColumnIndex(VoicemailContract.Voicemails.NUMBER)
                val dateIdx = it.getColumnIndex(VoicemailContract.Voicemails.DATE)
                val durIdx = it.getColumnIndex(VoicemailContract.Voicemails.DURATION)
                while (it.moveToNext()) {
                    voicemails.add(
                        VoicemailRecord(
                            id = it.getLong(idIdx),
                            number = it.getString(numIdx) ?: "Unknown",
                            name = "Voicemail",
                            date = it.getLong(dateIdx),
                            duration = it.getInt(durIdx),
                            uri = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("VoicemailRepository", "Carrier query failed: ${e.message}")
        }
        return voicemails
    }
}
