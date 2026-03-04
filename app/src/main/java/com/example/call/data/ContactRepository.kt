package com.example.call.data

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val name: String, 
    val number: String, 
    val id: Long? = null, 
    val lookupKey: String? = null,
    val t9Name: String = ""
)

fun List<Contact>.findContactFlexible(number: String): Contact? {
    val cleanSearch = number.replace(Regex("[^0-9+]"), "")
    return this.find { contact ->
        val cleanContact = contact.number.replace(Regex("[^0-9+]"), "")
        if (cleanSearch.length >= 7 && cleanContact.length >= 7) {
            cleanContact.endsWith(cleanSearch) || cleanSearch.endsWith(cleanContact)
        } else {
            cleanContact == cleanSearch
        }
    }
}

data class CallRecord(
    val id: Long,
    val name: String, 
    val number: String, 
    val type: String, 
    val time: String, 
    val duration: Int = 0, 
    val timestamp: Long = 0L
)

class ContactRepository(private val context: Context) {

    suspend fun getContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<Contact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                if (nameIndex != -1 && numberIndex != -1) {
                    while (it.moveToNext()) {
                        val id = if (idIndex != -1) it.getLong(idIndex) else null
                        val lookup = if (lookupIndex != -1) it.getString(lookupIndex) else null
                        val name = it.getString(nameIndex) ?: "Unknown"
                        val number = it.getString(numberIndex) ?: ""
                        contacts.add(Contact(name, number, id, lookup, generateT9Name(name)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        contacts.distinctBy { it.name }
    }

    private fun generateT9Name(name: String): String {
        return name.lowercase().map { char ->
            when (char) {
                'a', 'b', 'c' -> '2'
                'd', 'e', 'f' -> '3'
                'g', 'h', 'i' -> '4'
                'j', 'k', 'l' -> '5'
                'm', 'n', 'o' -> '6'
                'p', 'q', 'r', 's' -> '7'
                't', 'u', 'v' -> '8'
                'w', 'x', 'y', 'z' -> '9'
                else -> null
            }
        }.filterNotNull().joinToString("")
    }

    suspend fun getCallLogs(): List<CallRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<CallRecord>()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC"
            )
            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                
                if (idIndex != -1 && numberIndex != -1 && dateIndex != -1 && typeIndex != -1) {
                    while (it.moveToNext()) {
                        val id = it.getLong(idIndex)
                        val name = if (nameIndex != -1) it.getString(nameIndex) ?: it.getString(numberIndex) ?: "Unknown" else it.getString(numberIndex) ?: "Unknown"
                        val number = it.getString(numberIndex) ?: ""
                        val date = it.getLong(dateIndex)
                        val type = when (it.getInt(typeIndex)) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            CallLog.Calls.MISSED_TYPE -> "Missed"
                            else -> "Other"
                        }
                        val duration = if (durationIndex != -1) it.getInt(durationIndex) else 0
                        records.add(CallRecord(id, name, number, type, formatTime(date), duration, date))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        records // Removed the .take(20) limit to show all call history
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    suspend fun deleteCallLog(id: Long) = withContext(Dispatchers.IO) {
        try {
            val selection = "${CallLog.Calls._ID} = ?"
            val selectionArgs = arrayOf(id.toString())
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteCallLogs(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val selection = "${CallLog.Calls._ID} IN ($placeholders)"
            val selectionArgs = ids.map { it.toString() }.toTypedArray()
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
