package com.example.call.data

import android.content.Context
import com.example.call.data.Contact
import com.example.call.data.Note
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

object GeminiService {
    private var model: GenerativeModel? = null
    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        val modelName = prefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"

        if (apiKey.isNotBlank()) {
            model = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )
            _isReady.value = true
        } else {
            model = null
            _isReady.value = false
        }
    }

    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 4000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T? {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                Log.e("GeminiService", "Error in AI operation, retrying...", e)
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return try {
            block()
        } catch (e: Exception) {
            Log.e("GeminiService", "Final AI operation failed", e)
            null
        }
    }

    suspend fun generateCallSummary(contact: Contact, logs: List<CallRecord>, notes: List<Note>): String? {
        val generativeModel = model ?: return null
        if (logs.isEmpty() && notes.isEmpty()) return "New contact. No interactions recorded yet."
        
        val prompt = """
            Analyze the following call history and notes for contact '${contact.name}' (${contact.number}).
            Call History: ${logs.take(5).joinToString { "${it.type} on ${it.time}" }}
            Notes: ${notes.joinToString { it.content }}
            
            Provide a concise, 2-sentence summary of the relationship and recent interactions. 
            Format: "Relationship status. Recent highlight."
            If no historical data, mention they are a new contact.
        """.trimIndent()

        return retryIO {
            val response = generativeModel.generateContent(prompt)
            response.text
        }
    }

    suspend fun suggestSmartAction(noteContent: String): String? {
        val generativeModel = model ?: return null
        if (noteContent.isBlank()) return null

        val prompt = """
            Given this note content: "$noteContent"
            If it sounds like an appointment, task, or reminder, suggest a short action (max 4 words).
            Example: "Set 10 AM Reminder", "Email tomorrow", "Schedule meeting".
            If no clear action, return "No action".
        """.trimIndent()

        return retryIO {
            val response = generativeModel.generateContent(prompt)
            val text = response.text?.trim()
            if (text?.contains("No action", ignoreCase = true) == true) null else text
        }
    }

    suspend fun researchCaller(number: String): String? {
        val generativeModel = model ?: return null
        if (number.isBlank()) return null

        val prompt = """
            Research or analyze the caller metadata for the number: "$number".
            Based on common patterns or known data for this region (likely India/US based on number format), determines if this is likely a:
            - Safe/Personal Number
            - Telemarketer
            - Fraud/Scam
            - Business
            
            Return a short label and a one-sentence reason.
            Example: "Likely Telemarketer - Known robocall pattern in this area code."
        """.trimIndent()

        return retryIO {
            val response = generativeModel.generateContent(prompt)
            response.text?.trim()
        }
    }
}
