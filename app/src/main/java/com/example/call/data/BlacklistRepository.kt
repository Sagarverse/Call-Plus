package com.example.call.data

import android.content.Context
import android.content.SharedPreferences

class BlacklistRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("blacklist_prefs", Context.MODE_PRIVATE)

    fun blockNumber(number: String) {
        val cleanNumber = number.replace(" ", "")
        prefs.edit().putBoolean(cleanNumber, true).apply()
    }

    fun unblockNumber(number: String) {
        val cleanNumber = number.replace(" ", "")
        prefs.edit().remove(cleanNumber).apply()
    }

    fun isBlocked(number: String?): Boolean {
        if (number == null) return false
        val cleanNumber = number.replace(" ", "")
        return prefs.getBoolean(cleanNumber, false)
    }
    
    fun getBlockedNumbers(): List<String> {
        return prefs.all.keys.toList()
    }
}
