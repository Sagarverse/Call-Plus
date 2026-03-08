package com.example.call.data

import android.content.Context

class SpamRepository(context: Context) {
    private val spamPatterns = listOf(
        "^\\+1\\(800\\).*", 
        "^\\+1\\(888\\).*",
        "^\\+1\\(877\\).*",
        "0000000000"
    )

    fun checkIfSpam(number: String): Boolean {
        // Mock spam patterns (e.g., 800 numbers)
        val normalized = number.replace(" ", "").replace("-", "")
        return spamPatterns.any { pattern ->
            Regex(pattern).containsMatchIn(normalized)
        }
    }

    suspend fun researchUnknownCaller(number: String): String? {
        // Only research if not already caught by local patterns or contacts
        return GeminiService.researchCaller(number)
    }

    fun getSpamLabel(number: String): String {
        return "Likely Spam (Telemarketer)"
    }
}
