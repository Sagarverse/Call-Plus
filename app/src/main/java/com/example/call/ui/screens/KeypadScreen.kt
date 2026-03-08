package com.example.call.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.delay
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Contact
import com.example.call.data.findContactFlexible
import com.example.call.CallManager
import com.example.call.ui.theme.*
import com.example.call.ui.components.CameraScannerView
import com.example.call.ui.components.SagarCallBanner
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import java.util.Locale


@Composable
fun KeypadScreen(contacts: List<Contact>, onCall: (String) -> Unit, onCallClick: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isScanning = true
        }
    }

    // SIM Selection Logic - Moved up to be available to speech launcher
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager }
    val simCards = remember {
        try {
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                telecomManager.callCapablePhoneAccounts
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
    
    var selectedSimIndex by remember { mutableIntStateOf(0) }
    val selectedSim = simCards.getOrNull(selectedSimIndex)

    // Voice recognition setup
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            spokenText?.let { text ->
                if (text.lowercase().startsWith("call ")) {
                    val nameToSearch = text.substring(5).trim()
                    val contact = contacts.find { 
                        it.name.contains(nameToSearch, ignoreCase = true) || nameToSearch.contains(it.name, ignoreCase = true) 
                    }
                    if (contact != null) {
                        keyboardController?.hide()
                        CallManager.makeCall(context, contact.number, selectedSim)
                    } else {
                        android.widget.Toast.makeText(context, "'$nameToSearch' not found", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (isScanning) {
        CameraScannerView(
            onResult = { scannedNumber ->
                phoneNumber = scannedNumber
                isScanning = false
            },
            onCancel = { isScanning = false }
        )
        return
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

    var modelDownloaded by remember { mutableStateOf(false) }

    var inkBuilder by remember { mutableStateOf(Ink.builder()) }
    var strokeBuilder = remember { Ink.Stroke.builder() }
    var currentStrokes by remember { mutableStateOf(listOf<List<androidx.compose.ui.geometry.Offset>>()) }
    var currentStrokePoints by remember { mutableStateOf(listOf<androidx.compose.ui.geometry.Offset>()) }
    var lastStrokeTime by remember { mutableLongStateOf(0L) }
    
    val model = remember {
        val options = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
        options?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    val recognizer = remember(modelDownloaded) {
        if (modelDownloaded && model != null) {
            DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        } else null
    }

    LaunchedEffect(model) {
        if (model != null && !modelDownloaded) {
            val modelManager = RemoteModelManager.getInstance()
            modelManager.isModelDownloaded(model).addOnSuccessListener { downloaded: Boolean ->
                if (downloaded) {
                    modelDownloaded = true
                } else {
                    android.widget.Toast.makeText(context, "Downloading handwriting model...", android.widget.Toast.LENGTH_SHORT).show()
                    modelManager.download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            modelDownloaded = true
                            android.widget.Toast.makeText(context, "Handwriting ready!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            android.widget.Toast.makeText(context, "Failed to download model", android.widget.Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    LaunchedEffect(lastStrokeTime) {
        if (lastStrokeTime > 0) {
            delay(250) // exceedingly fast 250ms recognition
            if (recognizer != null && currentStrokes.isNotEmpty()) {
                val inkToProcess = inkBuilder.build()
                // Clear immediately so user can draw next digit without waiting for model
                inkBuilder = Ink.builder()
                currentStrokes = emptyList()
                currentStrokePoints = emptyList()

                recognizer.recognize(inkToProcess).addOnSuccessListener { result ->
                    val recognizedText = result.candidates.firstOrNull()?.text ?: ""
                    val digits = recognizedText.replace(Regex("[^0-9*#+]"), "")
                    if (digits.isNotEmpty()) {
                        phoneNumber += digits
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            } else if (recognizer == null) {
                // If model isn't downloaded, just clear
                inkBuilder = Ink.builder()
                currentStrokes = emptyList()
                currentStrokePoints = emptyList()
            }
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
                if (!modelDownloaded) "Downloading handwriting model..." else name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 2.dp).height(20.dp)
            )
            // Horizontal scroll for long phone numbers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
                    .height(45.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    phoneNumber,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
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

        // Keys Section
        if (true) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                strokeBuilder = Ink.Stroke.builder()
                                strokeBuilder.addPoint(Ink.Point.create(offset.x, offset.y))
                                currentStrokePoints = listOf(offset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                strokeBuilder.addPoint(Ink.Point.create(change.position.x, change.position.y))
                                currentStrokePoints = currentStrokePoints + change.position
                            },
                            onDragEnd = {
                                inkBuilder.addStroke(strokeBuilder.build())
                                currentStrokes = currentStrokes + listOf(currentStrokePoints)
                                currentStrokePoints = emptyList()
                                lastStrokeTime = System.currentTimeMillis()
                            }
                        )
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
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
                
                // Canvas layer overlaid on top for handwriting
                val brightBlue = Color(0xFF00A8FF)
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val paintMode = androidx.compose.ui.graphics.StrokeCap.Round
                    // Draw previous strokes
                    for (stroke in currentStrokes) {
                        for (i in 0 until stroke.size - 1) {
                            drawLine(color = brightBlue, start = stroke[i], end = stroke[i + 1], strokeWidth = 20f, cap = paintMode)
                        }
                    }
                    // Draw current active stroke
                    for (i in 0 until currentStrokePoints.size - 1) {
                        drawLine(color = brightBlue, start = currentStrokePoints[i], end = currentStrokePoints[i + 1], strokeWidth = 20f, cap = paintMode)
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(digit: String, letters: String, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    com.example.call.ui.components.GlassmorphicContainer(
        modifier = Modifier
            .padding(6.dp)
            .size(72.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                digit, 
                fontSize = 32.sp, 
                fontWeight = FontWeight.Light, 
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.SansSerif
            )
            if (letters.isNotEmpty()) {
                Text(
                    letters, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
