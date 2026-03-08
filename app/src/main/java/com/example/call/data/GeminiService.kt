package com.example.call.data

import android.content.Context
import com.example.call.data.Contact
import com.example.call.data.Note
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    suspend fun generateCallSummary(contact: Contact, logs: List<CallRecord>, notes: List<Note>): String? {
        val generativeModel = model ?: return null
        
        val prompt = """
            Analyze the following call history and notes for contact '${contact.name}' (${contact.number}).
            Call History: ${logs.take(5).joinToString { "${it.type} on ${it.time}" }}
            Notes: ${notes.joinToString { it.content }}
            
            Provide a concise, 2-sentence summary of the relationship and recent interactions. 
            Format: "Relationship status. Recent highlight."
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text
        } catch (e: Exception) {
            null
        }
    }

    suspend fun suggestSmartAction(noteContent: String): String? {
        val generativeModel = model ?: return null

        val prompt = """
            Given this note content: "$noteContent"
            If it sounds like an appointment, task, or reminder, suggest a short action (max 4 words).
            Example: "Set 10 AM Reminder", "Email tomorrow", "Schedule meeting".
            If no clear action, return "No action".
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            val text = response.text?.trim()
            if (text?.contains("No action", ignoreCase = true) == true) null else text
        } catch (e: Exception) {
            null
        }
    }

    suspend fun researchCaller(number: String): String? {
        val generativeModel = model ?: return null

        val prompt = """
            Research or analyze the caller metadata for the number: "$number".
            Based on common patterns or known data for this region, determines if this is likely a:
            - Safe/Personal Number
            - Telemarketer
            - Fraud/Scam
            - Business
            
            Return a short label and a one-sentence reason.
            Example: "Likely Telemarketer - Known robocall pattern in this area code."
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            null
        }
    }
}
