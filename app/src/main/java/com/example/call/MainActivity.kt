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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.example.call.ui.theme.CallTheme
import android.util.Log
import android.view.KeyEvent
import com.example.call.data.ContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {
    private var lastVolumeUpTime = 0L
    private var lastVolumeDownTime = 0L
    private val DOUBLE_PRESS_INTERVAL = 500L
    
    // Modern Activity Result API
    private val dialerRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d(TAG, "Dialer role request result: ${result.resultCode}")
    }
    companion object {
        var currentTab = 3 // Default to Keypad
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestDefaultDialer()

        setContent {
            val prefs = remember { getSharedPreferences("call_prefs", Context.MODE_PRIVATE) }
            var themePreference by remember { mutableStateOf(prefs.getString("theme_preference", "System") ?: "System") }

            CallTheme(themePreference = themePreference) {
                @android.annotation.SuppressLint("InlinedApi")
                val permissions = mutableListOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.WRITE_CALL_LOG,
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

                // READ_EXTERNAL_STORAGE only needed before Android 10 for call recordings
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    Log.d(TAG, "Permissions result: $results")
                }

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

    /**
     * Called when the app is already running and a new Intent arrives (singleTop launch mode).
     * This is how we receive the signal from CustomInCallService when an incoming call arrives
     * while the app is already open / in the background.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d(TAG, "onNewIntent: ${intent.action}")
        // The app's Compose state (activeCall) is driven by CallManager flows,
        // so nothing extra is needed here — the incoming call notification from
        // CustomInCallService will have already updated CallManager before we get called.
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (currentTab == 3 && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (now - lastVolumeUpTime < DOUBLE_PRESS_INTERVAL) {
                        Log.d(TAG, "Double press: Volume UP -> call last outgoing")
                        handleVolumeAction(isUp = true)
                        lastVolumeUpTime = 0
                        return true
                    }
                    lastVolumeUpTime = now
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (now - lastVolumeDownTime < DOUBLE_PRESS_INTERVAL) {
                        Log.d(TAG, "Double press: Volume DOWN -> call last missed")
                        handleVolumeAction(isUp = false)
                        lastVolumeDownTime = 0
                        return true
                    }
                    lastVolumeDownTime = now
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleVolumeAction(isUp: Boolean) {
        val repository = ContactRepository(this)
        lifecycleScope.launch {
            try {
                // Ensure logs are fresh
                repository.refreshCallLogs()
                val logs = repository.callLogs.value
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
                Log.e(TAG, "Failed to get logs for shortcut", e)
            }
        }
    }

    private fun requestDefaultDialer() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    dialerRoleLauncher.launch(intent)
                }
            } else {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (packageName != telecomManager.defaultDialerPackage) {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting default dialer", e)
        }
    }
}
