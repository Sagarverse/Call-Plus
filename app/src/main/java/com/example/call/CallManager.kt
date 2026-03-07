package com.example.call

import android.content.Context
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

    /**
     * Make a call using this app only — never redirects to the system or Google dialer.
     *
     * If the app is not yet the default dialer, we still attempt placeCall() which requires
     * CALL_PHONE permission. The user is expected to grant default-dialer role for full control.
     * We never fall back to ACTION_DIAL/ACTION_CALL which would open external dialers.
     */
    fun makeCall(context: Context, number: String, phoneAccountHandle: android.telecom.PhoneAccountHandle? = null, isVideo: Boolean = false) {
        if (number.isBlank()) {
            Toast.makeText(context, "Enter a number to call", Toast.LENGTH_SHORT).show()
            return
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        if (context.packageName != telecomManager.defaultDialerPackage) {
            Toast.makeText(context, "Set as Default Dialer to use custom calling UI", Toast.LENGTH_LONG).show()
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    if (roleManager != null && !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                } else {
                    val intent = android.content.Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e("CallManager", "Error requesting default dialer", e)
            }
            return
        }

        // Strip non-numeric except leading +
        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Phone permission required to make calls", Toast.LENGTH_LONG).show()
            Log.w("CallManager", "CALL_PHONE permission not granted")
            return
        }

        val uri = android.net.Uri.fromParts("tel", cleanNumber, null)
        Log.d("CallManager", "Placing call to $cleanNumber using ${phoneAccountHandle?.id ?: "Default"}")

        try {
            val extras = Bundle()
            if (phoneAccountHandle != null) {
                // Ensure the handle is actually valid/available before adding it
                val accounts = telecomManager.callCapablePhoneAccounts
                if (accounts.contains(phoneAccountHandle)) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                } else {
                    Log.w("CallManager", "Requested SIM handle not available, falling back to default")
                }
            }
            if (isVideo) {
                extras.putInt("android.telecom.extra.START_CALL_WITH_VIDEO_STATE", 3) // VIDEO_STATE_BIDIRECTIONAL
            }
            // Log final attempt
            Log.i("CallManager", "Finalizing placeCall for: $cleanNumber, video: $isVideo")
            telecomManager.placeCall(uri, extras)
        } catch (e: SecurityException) {
            Log.e("CallManager", "SecurityException placing call — CALL_PHONE denied at runtime", e)
            Toast.makeText(context, "Call permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("CallManager", "Critical error placing call", e)
            Toast.makeText(context, "Failed to place call: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
