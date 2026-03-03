package com.example.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallManager {
    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    fun addCall(call: Call) {
        Log.d("CallManager", "Adding call: ${call.details.handle}")
        _calls.value = _calls.value + call
        _activeCall.value = call
    }

    fun removeCall(call: Call) {
        Log.d("CallManager", "Removing call")
        val currentCalls = _calls.value.filter { it != call }
        _calls.value = currentCalls
        if (_activeCall.value == call) {
            _activeCall.value = currentCalls.lastOrNull()
        }
    }

    fun updateCall(call: Call?) {
        _activeCall.value = call
    }

    fun updateAudioState(audioState: CallAudioState) {
        _audioState.value = audioState
    }

    fun makeCall(context: Context, number: String) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val isDefaultDialer = context.packageName == telecomManager.defaultDialerPackage
        
        // Remove any non-numeric characters except +
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        val uri = Uri.fromParts("tel", cleanNumber, null)
        
        Log.d("CallManager", "Attempting call to $cleanNumber. Default Dialer: $isDefaultDialer")

        try {
            if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (isDefaultDialer) {
                    // Direct placement if we are the default app
                    telecomManager.placeCall(uri, Bundle())
                } else {
                    // Standard intent if we aren't default yet
                    val intent = Intent(Intent.ACTION_CALL, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } else {
                Toast.makeText(context, "Call permission denied", Toast.LENGTH_SHORT).show()
                val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("CallManager", "Error making call", e)
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
