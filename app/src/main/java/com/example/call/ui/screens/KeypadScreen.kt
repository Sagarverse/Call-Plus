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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.google.mlkit.vision.digitalink.*
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
        onDispose {
            recognizer?.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 24.dp), // Minimal bottom padding, pill has its own
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom // Push keypad down
    ) {
        // Top Section (Flexible space)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SagarCallBanner(
                    modifier = Modifier.clickable { onCallClick() },
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Scan Number", tint = VisionPrimary, modifier = Modifier.size(26.dp))
                    }
                }
            }
            
            // Suggestions
            Box(modifier = Modifier.height(80.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(suggestions) { contact ->
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { 
                                    keyboardController?.hide()
                                    phoneNumber = contact.number
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(contact.name.take(1), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                contact.name, 
                                fontSize = 11.sp, 
                                maxLines = 1, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val numberContact = remember(phoneNumber, contacts) { contacts.findContactFlexible(phoneNumber) }
            val name = if (phoneNumber.isNotEmpty()) (numberContact?.name ?: "") else ""

            Spacer(modifier = Modifier.weight(1f)) // Push the number down just above the keypad
                // Backspace
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (phoneNumber.isNotEmpty()) {
                        IconButton(
                            onClick = { phoneNumber = phoneNumber.dropLast(1) },
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        phoneNumber = ""
                                    },
                                    onTap = { phoneNumber = phoneNumber.dropLast(1) }
                                )
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // Call Button
                Surface(
                    modifier = Modifier
                        .size(56.dp),
                    shape = CircleShape,
                    color = com.example.call.ui.theme.IOSGreen,
                    onClick = {
                        if (phoneNumber.isNotEmpty()) {
                            keyboardController?.hide()
                            CallManager.makeCall(context, phoneNumber, selectedSim)
                        }
                    }
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(24.dp))
                }

                // Video Call Button
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(start = 8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        if (phoneNumber.isNotEmpty()) {
                            keyboardController?.hide()
                            CallManager.makeCall(context, phoneNumber, selectedSim, isVideo = true)
                        }
                    }
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .pointerInput(recognizer) {
                        if (recognizer == null || !modelDownloaded) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val position = event.changes.first().position
                                
                                when (event.type) {
                                    androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                        strokeBuilder = Ink.Stroke.builder()
                                        strokeBuilder.addPoint(Ink.Point.create(position.x, position.y))
                                        currentStrokePoints = listOf(position)
                                    }
                                    androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                        strokeBuilder.addPoint(Ink.Point.create(position.x, position.y))
                                        currentStrokePoints = currentStrokePoints + position
                                    }
                                    androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                        // Ignore single taps (they will be handled by KeypadButtons)
                                        if (currentStrokePoints.size > 2) {
                                            inkBuilder.addStroke(strokeBuilder.build())
                                            currentStrokes = currentStrokes + listOf(currentStrokePoints)
                                            currentStrokePoints = emptyList()
                                            val ink = inkBuilder.build()
                                            recognizer?.recognize(ink)
                                                ?.addOnSuccessListener { result ->
                                                    val topResult = result.candidates.firstOrNull()?.text
                                                    if (topResult != null) {
                                                        val recognizedChar = topResult.firstOrNull { it.isDigit() || it == '*' || it == '#' || it.lowercaseChar() == 'o' }
                                                        if (recognizedChar != null) {
                                                            val digitToAdd = if (recognizedChar.lowercaseChar() == 'o') '0' else recognizedChar
                                                            phoneNumber += digitToAdd
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                    inkBuilder = Ink.builder()
                                                    currentStrokes = emptyList()
                                                }
                                                ?.addOnFailureListener {
                                                    inkBuilder = Ink.builder()
                                                    currentStrokes = emptyList()
                                                }
                                        } else {
                                            currentStrokePoints = emptyList()
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                }

                // Hybrid Drawing Overlay: Always active over the keypad
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                        // Draw completed strokes
                        currentStrokes.forEach { stroke ->
                            if (stroke.size > 1) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(stroke[0].x, stroke[0].y)
                                    for (i in 1 until stroke.size) {
                                        lineTo(stroke[i].x, stroke[i].y)
                                    }
                                }
                                drawPath(path, color = VisionPrimary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
                            }
                        }
                        // Draw current stroke
                        if (currentStrokePoints.size > 1) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                                for (i in 1 until currentStrokePoints.size) {
                                    lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                                }
                            }
                            drawPath(path, color = VisionPrimary, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
                        }
                    }
                }

            Spacer(modifier = Modifier.height(12.dp))

            // SIM Selector
            if (simCards.size > 1) {
                Surface(
                    onClick = { selectedSimIndex = (selectedSimIndex + 1) % simCards.size },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = VisionPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (selectedSim?.id ?: "SIM ${selectedSimIndex + 1}").uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Call Button and Delete
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, top = 8.dp), 
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(80.dp))
                
                Surface(
                    modifier = Modifier
                        .size(80.dp),
                    shape = CircleShape,
                    color = com.example.call.ui.theme.IOSGreen,
                    onClick = {
                        if (phoneNumber.isNotEmpty()) {
                            keyboardController?.hide()
                            CallManager.makeCall(context, phoneNumber, selectedSim)
                        }
                    }
                ) {       Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (phoneNumber.isNotEmpty()) {
                        IconButton(
                            onClick = { phoneNumber = phoneNumber.dropLast(1) },
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        phoneNumber = "" 
                                    },
                                    onTap = { phoneNumber = phoneNumber.dropLast(1) }
                                )
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        }
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
