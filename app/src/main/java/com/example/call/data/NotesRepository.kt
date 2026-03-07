package com.example.call.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class Note(
    val id: Long,
    val content: String,
    val date: Long,
    val isPinned: Boolean = false,
    val contactNumber: String? = null
)

class NotesRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    suspend fun refreshNotes() = withContext(Dispatchers.IO) {
        val json = prefs.getString("notes_list", null) ?: return@withContext
        val type = object : TypeToken<List<Note>>() {}.type
        val list: List<Note> = gson.fromJson(json, type) ?: emptyList()
        _notes.value = list
    }

    suspend fun saveNotes(notesList: List<Note>) = withContext(Dispatchers.IO) {
        val json = gson.toJson(notesList)
        prefs.edit().putString("notes_list", json).apply()
        _notes.value = notesList
    }

    suspend fun addNoteForContact(number: String, content: String) = withContext(Dispatchers.IO) {
        val currentNotes = _notes.value.toMutableList()
        val newNote = Note(
            id = System.currentTimeMillis(),
            content = content,
            date = System.currentTimeMillis(),
            contactNumber = number
        )
        currentNotes.add(newNote)
        saveNotes(currentNotes)
    }
}
