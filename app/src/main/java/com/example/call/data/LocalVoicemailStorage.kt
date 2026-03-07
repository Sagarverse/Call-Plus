package com.example.call.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple SharedPreferences-backed local store for in-app voicemail records.
 * Each record stores caller info, the audio file path, transcript text, and metadata.
 * These are voicemails recorded by OUR app (silent-answer mode) — completely independent
 * of the carrier / Android VoicemailContract.
 */
class LocalVoicemailStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dispatcher = kotlinx.coroutines.Dispatchers.IO

    suspend fun saveVoicemail(record: LocalVoicemailRecord) = kotlinx.coroutines.withContext(dispatcher) {
        val all = loadAll().toMutableList()
        // Replace if same id, otherwise prepend (newest first)
        val idx = all.indexOfFirst { it.id == record.id }
        if (idx >= 0) all[idx] = record else all.add(0, record)
        prefs.edit { putString(KEY_LIST, serializeAll(all)) }
    }

    suspend fun loadAll(): List<LocalVoicemailRecord> = kotlinx.coroutines.withContext(dispatcher) {
        val json = prefs.getString(KEY_LIST, null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { deserialize(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteById(id: Long) = kotlinx.coroutines.withContext(dispatcher) {
        val updated = loadAll().filter { it.id != id }
        prefs.edit { putString(KEY_LIST, serializeAll(updated)) }
    }

    suspend fun updateTranscript(id: Long, transcript: String) = kotlinx.coroutines.withContext(dispatcher) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(transcript = transcript)
            prefs.edit { putString(KEY_LIST, serializeAll(all)) }
        }
    }

    // ─── Serialization ────────────────────────────────────────────────────────

    private fun serializeAll(list: List<LocalVoicemailRecord>): String {
        val arr = JSONArray()
        list.forEach { arr.put(serialize(it)) }
        return arr.toString()
    }

    private fun serialize(r: LocalVoicemailRecord) = JSONObject().apply {
        put("id", r.id)
        put("number", r.number)
        put("name", r.name)
        put("date", r.date)
        put("duration", r.duration)
        put("filePath", r.filePath)
        put("transcript", r.transcript)
        put("isRead", r.isRead)
    }

    private fun deserialize(o: JSONObject) = LocalVoicemailRecord(
        id = o.getLong("id"),
        number = o.getString("number"),
        name = o.optString("name", "Unknown"),
        date = o.getLong("date"),
        duration = o.optInt("duration", 0),
        filePath = o.optString("filePath", ""),
        transcript = o.optString("transcript", ""),
        isRead = o.optBoolean("isRead", false)
    )

    companion object {
        private const val PREFS_NAME = "local_voicemails"
        private const val KEY_LIST = "voicemail_list"
    }
}

data class LocalVoicemailRecord(
    val id: Long,
    val number: String,
    val name: String,
    val date: Long,
    val duration: Int,           // seconds
    val filePath: String,        // absolute path to the .m4a file
    val transcript: String,      // speech-to-text result
    val isRead: Boolean = false
)

/** Convert LocalVoicemailRecord → VoicemailRecord (for display in existing VoicemailScreen) */
fun LocalVoicemailRecord.toVoicemailRecord() = VoicemailRecord(
    id = id,
    number = number,
    name = name,
    date = date,
    duration = duration,
    uri = if (filePath.isNotEmpty()) Uri.parse("file://$filePath") else null,
    transcript = transcript,
    isLocal = true
)
