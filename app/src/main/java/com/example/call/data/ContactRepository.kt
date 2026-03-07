package com.example.call.data

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

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
    val timestamp: Long = 0L,
    val simId: String? = null,
    val simLabel: String? = null
)

class ContactRepository(private val context: Context) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _callLogs = MutableStateFlow<List<CallRecord>>(emptyList())
    val callLogs: StateFlow<List<CallRecord>> = _callLogs.asStateFlow()

    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager

    suspend fun refreshContacts() = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<Contact>()
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
                        contactsList.add(Contact(name, number, id, lookup, generateT9Name(name)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _contacts.value = contactsList.distinctBy { it.name }
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

    suspend fun refreshCallLogs() = withContext(Dispatchers.IO) {
        val records = mutableListOf<CallRecord>()
        val simMap = mutableMapOf<String, String>()
        
        try {
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                activeSubscriptions?.forEach { info ->
                    val id = info.subscriptionId.toString()
                    val label = info.displayName.toString()
                    simMap[id] = label
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
                val accountIdIndex = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                
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
                        val simId = if (accountIdIndex != -1) it.getString(accountIdIndex) else null
                        val simLabel = if (simId != null) simMap[simId] ?: "SIM $simId" else null

                        records.add(CallRecord(id, name, number, type, formatTime(date), duration, date, simId, simLabel))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _callLogs.value = records
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    suspend fun deleteCallLog(id: Long) = withContext(Dispatchers.IO) {
        try {
            val selection = "${CallLog.Calls._ID} = ?"
            val selectionArgs = arrayOf(id.toString())
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
            refreshCallLogs()
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
            refreshCallLogs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
