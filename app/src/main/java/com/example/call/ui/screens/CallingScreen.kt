package com.example.call.ui.screens

import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.*
import com.example.call.data.ContactRepository
import com.example.call.data.findContactFlexible
import com.example.call.ui.components.CallActionButton
import com.example.call.ui.components.adaptiveTextColor
import com.example.call.ui.theme.*
import kotlinx.coroutines.delay

fun getGradientForContact(number: String): List<Color> {
    val hash = number.hashCode()
    return when (kotlin.math.abs(hash) % 4) {
        0 -> listOf(Color(0xFF007AFF), Color(0xFF5856D6)) // Blue to Purple
        1 -> listOf(Color(0xFF34C759), Color(0xFF007AFF)) // Green to Blue
        2 -> listOf(Color(0xFFFF2D55), Color(0xFFFF9500)) // Red to Orange
        else -> listOf(Color(0xFF5856D6), Color(0xFFFF2D55)) // Purple to Pink
    }
}

@Composable
fun CallingScreen(call: Call, onMinimize: () -> Unit = {}) {
    val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
    var callState by remember(call) { mutableIntStateOf(call.state) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var showInCallKeypad by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val voicemailManager = remember { VoicemailManager(context) }
    val callRecorder = remember { CallRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    val transcript by voicemailManager.transcript.collectAsState()
    var isVoicemailRecording by remember { mutableStateOf(false) }

    // Contact name resolution
    val repository = remember { ContactRepository(context) }
    var contactName by remember { mutableStateOf(number) }
    
    LaunchedEffect(number) {
        val contacts = repository.getContacts()
        contactName = contacts.findContactFlexible(number)?.name ?: number
    }

    // Timer logic
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000)
                durationSeconds++
            }
        }
    }
    
    val formattedDuration = remember(durationSeconds) {
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call?, state: Int) {
                callState = state
            }
        }
        call.registerCallback(callback)
        onDispose {
            call.unregisterCallback(callback)
            if (isRecording) callRecorder.stopRecording()
            if (isVoicemailRecording) {
                voicemailManager.stopRecording()
                isVoicemailRecording = false
            }
        }
    }
    
    // Background overlay to hide dialer/previous screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) {} // Consume clicks
            .background(Color.Black) // iOS call background is traditionally dark
    ) {
        val audioState by CallManager.audioState.collectAsState()
        val isMuted = audioState?.isMuted ?: false
        val isSpeakerOn = (audioState?.route ?: 0) == CallAudioState.ROUTE_SPEAKER
        val isHeld = callState == Call.STATE_HOLDING

        val contactGradient = remember(number) { getGradientForContact(number) }
        val backgroundColors = if (callState == Call.STATE_RINGING) 
                            contactGradient
                        else 
                            contactGradient.map { it.copy(alpha = 0.8f) }
        
        val contentColor = adaptiveTextColor(backgroundColors.first())

        // The colorful gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = backgroundColors))
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(IOSGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = contentColor, modifier = Modifier.size(60.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(contactName, color = contentColor, fontSize = 32.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contactName != number) {
                    Text(number, color = contentColor.copy(alpha = 0.6f), fontSize = 16.sp)
                }
                Text(
                    when(callState) {
                        Call.STATE_RINGING -> "Incoming Call..."
                        Call.STATE_DIALING -> "Calling..."
                        Call.STATE_HOLDING -> "On Hold"
                        Call.STATE_ACTIVE -> formattedDuration
                        Call.STATE_DISCONNECTED -> "Call Ended"
                        else -> "Connecting..."
                    }, 
                    color = contentColor.copy(alpha = 0.8f), 
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Live Voicemail Transcript Overlay
            if (isVoicemailRecording && transcript.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Live Transcript", color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(transcript, color = contentColor, fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Notes Section
            val notesRepository = remember { com.example.call.data.NotesRepository(context) }
            var callerNote by remember { mutableStateOf<com.example.call.data.Note?>(null) }
            
            LaunchedEffect(number) {
                val allNotes = notesRepository.getNotes()
                callerNote = allNotes.find { it.content.contains(number.takeLast(10)) }
            }

            if (callerNote != null && callState == Call.STATE_ACTIVE) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Latest Note", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(callerNote!!.content, color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Bottom Section: Actions (Non-scrollable)
            Column(modifier = Modifier.padding(bottom = 80.dp)) {
                if (callState == Call.STATE_RINGING) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { /* showQuickMessages */ }) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
                                Text("Message", color = contentColor, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
                                call.answer(0)
                                voicemailManager.startRecording("voicemail_${System.currentTimeMillis()}")
                                isVoicemailRecording = true
                            }) {
                                Icon(Icons.Default.KeyboardVoice, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
                                Text("Voicemail", color = contentColor, fontSize = 12.sp)
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            IconButton(
                                onClick = { call.disconnect() },
                                modifier = Modifier.size(75.dp).clip(CircleShape).background(IOSRed)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                            IconButton(
                                onClick = { call.answer(0) },
                                modifier = Modifier.size(75.dp).clip(CircleShape).background(IOSGreen)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                } else {
                    val calls by CallManager.calls.collectAsState()
                    val canMerge = calls.size >= 2
                    
                    val actions = listOf(
                        listOf(
                            Triple(Icons.Default.Mic, "mute", isMuted), 
                            Triple(Icons.Default.Dialpad, "keypad", false), 
                            Triple(Icons.Default.VolumeUp, "speaker", isSpeakerOn)
                        ),
                        listOf(
                            Triple(Icons.Default.Add, if (canMerge) "merge" else "add call", false),
                            Triple(Icons.Default.Refresh, "swap", false), 
                            Triple(Icons.Default.Pause, "hold", isHeld)
                        ),
                        listOf(
                            Triple(if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord, "record", isRecording),
                            Triple(Icons.Default.Person, "contacts", false),
                            Triple(Icons.Default.MoreHoriz, "more", false)
                        )
                    )

                    actions.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            row.forEach { (icon, label, active) ->
                                CallActionButton(
                                    icon = icon,
                                    label = label,
                                    isActive = active,
                                    textColor = contentColor,
                                    onClick = {
                                        when (label) {
                                            "mute" -> CustomInCallService.instance?.toggleMute(!isMuted)
                                            "speaker" -> CustomInCallService.instance?.toggleSpeaker(!isSpeakerOn)
                                            "keypad" -> showInCallKeypad = true
                                            "merge" -> if (calls.size >= 2) calls[0].conference(calls[1])
                                            "add call" -> onMinimize()
                                            "swap" -> {
                                                if (calls.size >= 2) {
                                                    val otherCall = calls.find { it != call }
                                                    otherCall?.unhold()
                                                    call.hold()
                                                    CallManager.updateCall(otherCall)
                                                }
                                            }
                                            "hold" -> if (isHeld) call.unhold() else call.hold()
                                            "contacts" -> onMinimize()
                                            "record" -> {
                                                if (isRecording) {
                                                    callRecorder.stopRecording()
                                                    isRecording = false
                                                } else {
                                                    callRecorder.startRecording("call_${number}_${System.currentTimeMillis()}")
                                                    isRecording = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(60.dp))

                    IconButton(
                        onClick = { call.disconnect() },
                        modifier = Modifier
                            .size(75.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(CircleShape)
                            .background(IOSRed)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}
