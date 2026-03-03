package com.example.call.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Contact
import com.example.call.data.findContactFlexible
import com.example.call.ui.theme.*
import com.example.call.ui.components.CameraScannerView
import com.example.call.ui.components.SagarCallBanner
import java.util.Locale

@Composable
fun KeypadScreen(contacts: List<Contact>, onCall: (String) -> Unit, onCallClick: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isScanning = true
        }
    }

    if (isScanning) {
        CameraScannerView(
            onNumberDetected = { scannedNumber ->
                phoneNumber = scannedNumber
                isScanning = false
            },
            onCancel = { isScanning = false }
        )
        return
    }

    // Voice recognition setup
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            spokenText?.let { text ->
                if (text.lowercase().startsWith("call ")) {
                    val nameToSearch = text.substring(5).trim()
                    val contact = contacts.find { it.name.equals(nameToSearch, ignoreCase = true) }
                    contact?.let { onCall(it.number) }
                }
            }
        }
    }

    // Optimized T9 Search
    val suggestions = remember(phoneNumber, contacts) {
        if (phoneNumber.isEmpty()) emptyList()
        else {
            contacts.filter { contact ->
                val cleanNumber = contact.number.replace(" ", "")
                cleanNumber.contains(phoneNumber) || contact.t9Name.contains(phoneNumber)
            }.take(10)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 80.dp), // Reduced height of bottom bar
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // iOS Style Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SagarCallBanner(
                    modifier = Modifier.padding(start = 8.dp).clickable { onCallClick() },
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                IconButton(
                    onClick = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan Number", tint = IOSBlue, modifier = Modifier.size(28.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Contact Suggestions - Optimized height
            Box(modifier = Modifier.height(80.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(suggestions) { contact ->
                        Column(
                            modifier = Modifier
                                .width(70.dp)
                                .clickable { onCall(contact.number) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(50.dp).clip(CircleShape).background(IOSGray.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(contact.name.take(1), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                contact.name, 
                                fontSize = 11.sp, 
                                maxLines = 1, 
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            val numberContact = remember(phoneNumber, contacts) { contacts.findContactFlexible(phoneNumber) }
            val name = if (phoneNumber.isNotEmpty()) (numberContact?.name ?: "") else ""

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 2.dp).height(20.dp)
            )
            Text(
                phoneNumber,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 4.dp).height(40.dp),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (phoneNumber.isNotEmpty() && name.isEmpty()) {
                Text("Add Number", color = IOSBlue, modifier = Modifier.clickable { 
                    val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                    }
                    context.startActivity(intent)
                })
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Keys Section - Moved lower by natural weight or padding if needed
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val keys = listOf(
                listOf("1" to "", "2" to "A B C", "3" to "D E F"),
                listOf("4" to "G H I", "5" to "J K L", "6" to "M N O"),
                listOf("7" to "P Q R S", "8" to "T U V", "9" to "W X Y Z"),
                listOf("*" to "", "0" to "+", "#" to "")
            )

            keys.forEach { row ->
                Row {
                    row.forEach { (digit, letters) ->
                        KeypadButton(
                            digit = digit, 
                            letters = letters, 
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                phoneNumber += digit
                            },
                            onLongClick = {
                                if (digit == "1") {
                                    onCall("*86")
                                } else if (digit == "0") {
                                    phoneNumber += "+"
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Call Button and Delete
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), 
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { if (phoneNumber.isNotEmpty()) onCall(phoneNumber) },
                                onLongPress = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Call [Name]'")
                                    }
                                    try {
                                        speechLauncher.launch(speechIntent)
                                    } catch (e: Exception) {}
                                }
                            )
                        },
                    shape = CircleShape,
                    color = IOSGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                
                if (phoneNumber.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 160.dp) // Adjusted for smaller call button
                            .size(72.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { phoneNumber = phoneNumber.dropLast(1) },
                                    onLongPress = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        phoneNumber = "" 
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Delete", tint = IOSGray, modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier
            .padding(6.dp)
            .size(75.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = CircleShape,
        color = if (isDark) IOSButtonDark else IOSButtonLight
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(digit, fontSize = 34.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
            if (letters.isNotEmpty()) {
                Text(letters, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), letterSpacing = 2.sp)
            }
        }
    }
}
