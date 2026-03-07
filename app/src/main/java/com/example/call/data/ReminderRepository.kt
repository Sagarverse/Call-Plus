package com.example.call.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class Reminder(
    val id: Long,
    val number: String,
    val name: String,
    val time: Long,
    val scheduledTime: Long
)

class ReminderRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val json = prefs.getString("reminders", "[]")
        val type = object : TypeToken<List<Reminder>>() {}.type
        val list: List<Reminder> = gson.fromJson(json, type)
        _reminders.value = list
    }

    suspend fun addReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        val currentReminders = _reminders.value.toMutableList()
        currentReminders.add(reminder)
        save(currentReminders)
    }

    suspend fun removeReminder(id: Long) = withContext(Dispatchers.IO) {
        val currentReminders = _reminders.value.filter { it.id != id }
        save(currentReminders)
    }

    private fun save(list: List<Reminder>) {
        val json = gson.toJson(list)
        prefs.edit().putString("reminders", json).apply()
        _reminders.value = list
    }
}
