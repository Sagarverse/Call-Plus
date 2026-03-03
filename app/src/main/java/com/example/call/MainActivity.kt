package com.example.call

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import android.hardware.SensorManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.call.ui.theme.CallTheme
import com.example.call.util.ShakeDetector
import android.util.Log
import android.view.KeyEvent
import com.example.call.data.ContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var lastVolumeUpTime = 0L
    private var lastVolumeDownTime = 0L
    private val DOUBLE_PRESS_INTERVAL = 500L
    
    companion object {
        var currentTab = 3 // Default to Keypad
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestDefaultDialer()

        setContent {
            val prefs = remember { getSharedPreferences("call_prefs", Context.MODE_PRIVATE) }
            var themePreference by remember { mutableStateOf(prefs.getString("theme_preference", "System") ?: "System") }

            CallTheme(themePreference = themePreference) {
                val permissions = mutableListOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.MANAGE_OWN_CALLS,
                    Manifest.permission.SEND_SMS
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    permissions.add("com.android.voicemail.permission.READ_VOICEMAIL")
                    permissions.add("com.android.voicemail.permission.ADD_VOICEMAIL")
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    val permissionsToRequest = permissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (permissionsToRequest.isNotEmpty()) {
                        launcher.launch(permissionsToRequest.toTypedArray())
                    }
                }

                CallApp(themePreference = themePreference, onThemeChange = {
                    themePreference = it
                    prefs.edit().putString("theme_preference", it).apply()
                })
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (currentTab == 3 && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (now - lastVolumeUpTime < DOUBLE_PRESS_INTERVAL) {
                        Log.d("MainActivity", "Double press detected: Volume UP")
                        handleVolumeAction(isUp = true)
                        lastVolumeUpTime = 0
                        return true // Consume event
                    }
                    lastVolumeUpTime = now
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (now - lastVolumeDownTime < DOUBLE_PRESS_INTERVAL) {
                        Log.d("MainActivity", "Double press detected: Volume DOWN")
                        handleVolumeAction(isUp = false)
                        lastVolumeDownTime = 0
                        return true // Consume event
                    }
                    lastVolumeDownTime = now
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleVolumeAction(isUp: Boolean) {
        val repository = ContactRepository(this)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val logs = repository.getCallLogs()
                if (isUp) {
                    logs.find { it.type == "Outgoing" }?.let {
                        CallManager.makeCall(this@MainActivity, it.number)
                    }
                } else {
                    logs.find { it.type == "Missed" }?.let {
                        CallManager.makeCall(this@MainActivity, it.number)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to get logs for shortcut", e)
            }
        }
    }
    

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, 123)
            }
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (packageName != telecomManager.defaultDialerPackage) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        }
    }
}
