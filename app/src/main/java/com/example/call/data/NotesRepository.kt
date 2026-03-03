package com.example.call.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Note(
    val id: Long,
    val content: String,
    val date: Long,
    val isPinned: Boolean = false
)

class NotesRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getNotes(): List<Note> {
        val json = prefs.getString("notes_list", null) ?: return emptyList()
        val type = object : TypeToken<List<Note>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveNotes(notes: List<Note>) {
        val json = gson.toJson(notes)
        prefs.edit().putString("notes_list", json).apply()
    }
}
