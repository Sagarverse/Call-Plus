package com.example.call.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.call.*
import com.example.call.data.ContactRepository
import com.example.call.data.findContactFlexible
import com.example.call.ui.components.CallActionButton
import com.example.call.ui.components.adaptiveTextColor
import com.example.call.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.telecom.VideoProfile
import androidx.compose.ui.viewinterop.AndroidView

fun getGradientForContact(number: String): List<Color> {
    val hash = number.hashCode()
    return when (kotlin.math.abs(hash) % 4) {
        0 -> listOf(Color(0xFF007AFF), Color(0xFF5856D6))
        1 -> listOf(Color(0xFF34C759), Color(0xFF007AFF))
        2 -> listOf(Color(0xFFFF2D55), Color(0xFFFF9500))
        else -> listOf(Color(0xFF5856D6), Color(0xFFFF2D55))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallingScreen(
    call: Call, 
    repository: ContactRepository,
    notesRepository: com.example.call.data.NotesRepository,
    reminderRepository: com.example.call.data.ReminderRepository,
    onMinimize: () -> Unit = {}
) {
    val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
    var callState by remember(call) { mutableIntStateOf(call.state) }
    var durationSeconds by remember { mutableIntStateOf(0) }
    var showInCallKeypad by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val voicemailManager = remember { VoicemailManager(context) }
    val screeningManager = remember { ScreeningManager(context, voicemailManager) }
    val callRecorder = remember { CallRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    val transcript by voicemailManager.transcript.collectAsState()
    var isVoicemailRecording by remember { mutableStateOf(false) }
    val isScreening by screeningManager.isScreening.collectAsState()
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showDeclineOptions by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher for RECORD_AUDIO (needed for call recording)
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = callRecorder.startRecording("call_${number}_${System.currentTimeMillis()}")
            if (started) {
                isRecording = true
                Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Recording failed to start", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for recording", Toast.LENGTH_LONG).show()
        }
    }

    // Contact name resolution - Memoized to avoid flashes
    val contacts by repository.contacts.collectAsState()
    val contactName = remember(number, contacts) {
        contacts.findContactFlexible(number)?.name ?: number
    }

    // Timer logic - More robust
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            val startTime = System.currentTimeMillis() - (durationSeconds * 1000L)
            while (true) {
                durationSeconds = ((System.currentTimeMillis() - startTime) / 1000L).toInt()
                delay(500) // Poll faster for smooth UI but based on system time
            }
        }
    }

    val formattedDuration = remember(durationSeconds) {
        val m = durationSeconds / 60
        val s = durationSeconds % 60
        "%02d:%02d".format(m, s)
    }

    DisposableEffect(call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call?, state: Int) {
                callState = state
            }
        }
        call.registerCallback(callback)
        onDispose {
            call.unregisterCallback(callback)
            if (isRecording) {
                callRecorder.stopRecording()
            }
            if (isVoicemailRecording) {
                voicemailManager.stopRecording()
                isVoicemailRecording = false
                // Turn off speakerphone and unmute once voicemail recording ends
                CustomInCallService.instance?.toggleSpeaker(false)
                CustomInCallService.instance?.toggleMute(false)
            }
        }
    }

    // High-End Premium Mesh Gradients
    val infiniteTransition = rememberInfiniteTransition()
    val phaseX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val phaseY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )


    val isVideoCall = remember(callState) {
        val vs = call.details.videoState
        VideoProfile.isTransmissionEnabled(vs) || VideoProfile.isReceptionEnabled(vs)
    }

    val isIncoming = callState == Call.STATE_RINGING
    val primaryColor = if (isIncoming) CallGradientIncomingStart else CallGradientStart
    val secondaryColor = if (isIncoming) CallGradientIncomingEnd else CallGradientMid
    val tertiaryColor = if (isIncoming) Color(0xFFC31432) else CallGradientEnd
    val contentColor = Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) {}
            .background(Color.Black)
    ) {
        val audioState by CallManager.audioState.collectAsState()
        val isMuted = audioState?.isMuted ?: false
        val isSpeakerOn = (audioState?.route ?: 0) == CallAudioState.ROUTE_SPEAKER
        val isHeld = callState == Call.STATE_HOLDING


        // Video Call Background
        if (isVideoCall) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Remote video takes full screen
                VideoCallSurface(call = call, isLocal = false, modifier = Modifier.fillMaxSize())
                
                // Local video is a pip
                Box(modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 20.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                ) {
                    VideoCallSurface(call = call, isLocal = true, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
        // Background Mesh Gradients
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)))
            Box(
                modifier = Modifier.fillMaxSize().background(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.6f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(x = 1000f * phaseX, y = 500f + 500f * phaseY),
                        radius = 900f
                    )
                )
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryColor.copy(alpha = 0.5f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(x = 1000f * (1f - phaseX), y = 1500f - 800f * phaseY),
                        radius = 1100f
                    )
                )
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiaryColor.copy(alpha = if (isIncoming) 0.5f else 0.3f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(x = 500f + 300f * phaseY, y = 500f + 1000f * phaseX),
                        radius = 800f
                    )
                )
            )
        }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 100.dp)) {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(IOSGray.copy(alpha = 0.3f)),
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
                    when (callState) {
                        Call.STATE_RINGING -> "Incoming Call..."
                        Call.STATE_DIALING -> "Calling..."
                        Call.STATE_HOLDING -> "On Hold"
                        Call.STATE_ACTIVE -> formattedDuration
                        Call.STATE_DISCONNECTED -> "Call Ended"
                        else -> "Connecting..."
                    },
                    color = contentColor.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.Medium
                )
                if (isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recording", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if ((isVoicemailRecording || isScreening) && transcript.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isScreening) "AI Screening..." else "Live Transcript", color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(transcript, color = contentColor, fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        if (isScreening) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    screeningManager.stopScreening()
                                    CustomInCallService.instance?.toggleMute(false)
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = IOSGreen)
                            ) { Text("Join Call", color = Color.White) }
                        }
                    }
                }
            }

            val notes by notesRepository.notes.collectAsState()
            val callerNote = remember(number, notes) {
                notes.find { it.contactNumber == number || (it.contactNumber != null && number.endsWith(it.contactNumber!!)) }
            }
            if (callerNote != null && callState == Call.STATE_ACTIVE) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Note", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(callerNote!!.content, color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Column(modifier = Modifier.padding(bottom = 60.dp)) {
                if (callState == Call.STATE_RINGING) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showReminderMenu = true }) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
                                Text("Remind Me", color = contentColor, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showDeclineOptions = true }) {
                                Icon(Icons.Default.Message, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
                                Text("Message", color = contentColor, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                call.answer(0)
                                CustomInCallService.instance?.toggleSpeaker(true)
                                CustomInCallService.instance?.toggleMute(true)
                                screeningManager.startScreening(number)
                            }) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp))
                                Text("Screen", color = contentColor, fontSize = 12.sp)
                            }
                        }
                        
                        if (showDeclineOptions) {
                            Surface(modifier = Modifier.padding(bottom = 16.dp), color = Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    listOf("I'll call you back.", "Can't talk now. What's up?", "I'm in a meeting.", "On my way!").forEach { msg ->
                                        Text(text = msg, modifier = Modifier.fillMaxWidth().clickable {
                                            val smsUri = Uri.parse("smsto:$number")
                                            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply { putExtra("sms_body", msg); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                            try { context.startActivity(intent) } catch (_: Exception) {}
                                            call.disconnect()
                                            showDeclineOptions = false
                                        }.padding(12.dp), color = Color.Black, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        if (showReminderMenu) {
                            Surface(modifier = Modifier.padding(bottom = 16.dp), color = Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    listOf("In 1 minute" to 1L, "In 1 hour" to 60L, "Tomorrow morning" to 1440L).forEach { (label, mins) ->
                                        Text(text = label, modifier = Modifier.fillMaxWidth().clickable {
                                            val time = System.currentTimeMillis() + (mins * 60 * 1000)
                                            coroutineScope.launch {
                                                val reminder = com.example.call.data.Reminder(id = System.currentTimeMillis(), number = number, name = contactName, time = time, scheduledTime = System.currentTimeMillis())
                                                reminderRepository.addReminder(reminder)
                                                com.example.call.util.ReminderWorker.schedule(context, reminder)
                                                showReminderMenu = false; call.disconnect(); Toast.makeText(context, "Reminder set: $label", Toast.LENGTH_SHORT).show()
                                            }
                                        }.padding(12.dp), color = Color.Black, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            IconButton(onClick = { call.disconnect() }, modifier = Modifier.size(75.dp).clip(CircleShape).background(IOSRed)) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                            IconButton(onClick = { call.answer(0) }, modifier = Modifier.size(75.dp).clip(CircleShape).background(IOSGreen)) {
                                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                } else {
                    val calls by CallManager.calls.collectAsState()
                    val canMerge = calls.size >= 2
                    val actions = listOf(
                        listOf(Triple(Icons.Default.Mic, "mute", isMuted), Triple(Icons.Default.Dialpad, "keypad", false), Triple(Icons.Default.VolumeUp, "speaker", isSpeakerOn)),
                        listOf(Triple(Icons.Default.Add, if (canMerge) "merge" else "add call", false), Triple(Icons.Default.Videocam, "video", isVideoCall), Triple(Icons.Default.Pause, "hold", isHeld)),
                        listOf(Triple(if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord, "record", isRecording), Triple(Icons.Default.EditNote, "notes", false), Triple(Icons.Default.MoreHoriz, "more", false))
                    )

                    actions.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            row.forEach { (icon, label, active) ->
                                CallActionButton(icon = icon, label = label, isActive = active, textColor = contentColor, onClick = {
                                    when (label) {
                                        "mute" -> CustomInCallService.instance?.toggleMute(!isMuted)
                                        "speaker" -> CustomInCallService.instance?.toggleSpeaker(!isSpeakerOn)
                                        "keypad" -> showInCallKeypad = true
                                        "video" -> {
                                            if (isVideoCall) {
                                                call.videoCall?.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_AUDIO_ONLY))
                                            } else {
                                                call.videoCall?.sendSessionModifyRequest(VideoProfile(VideoProfile.STATE_BIDIRECTIONAL))
                                            }
                                        }
                                        "merge" -> if (calls.size >= 2) calls[0].conference(calls[1])
                                        "add call" -> onMinimize()
                                        "swap" -> { if (calls.size >= 2) { val other = calls.find { it != call }; other?.unhold(); call.hold(); CallManager.updateCall(other) } }
                                        "hold" -> if (isHeld) call.unhold() else call.hold()
                                        "notes" -> { noteText = ""; showNoteDialog = true }
                                        "more" -> showMoreSheet = true
                                        "record" -> {
                                            if (isRecording) {
                                                val stopped = callRecorder.stopRecording(); isRecording = false
                                                if (stopped) Toast.makeText(context, "Recording saved", Toast.LENGTH_LONG).show()
                                            } else {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    if (callRecorder.startRecording("call_${number}_${System.currentTimeMillis()}")) { isRecording = true; Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show() }
                                                } else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                    IconButton(onClick = { call.disconnect() }, modifier = Modifier.size(75.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(IOSRed)) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }

        if (showInCallKeypad) {
            InCallKeypadOverlay(
                onDismiss = { showInCallKeypad = false },
                onKey = { digit -> call.playDtmfTone(digit[0]); call.stopDtmfTone() },
                contentColor = adaptiveTextColor(primaryColor),
                backgroundColors = listOf(primaryColor, secondaryColor)
            )
        }

        if (showMoreSheet) {
            ModalBottomSheet(onDismissRequest = { showMoreSheet = false }, containerColor = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("More Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                    HorizontalDivider()
                    MoreSheetItem(Icons.Default.PersonAdd, "Add to Contacts") {
                        showMoreSheet = false; val intent = Intent(Intent.ACTION_INSERT).apply { type = "vnd.android.cursor.dir/contact"; putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                    MoreSheetItem(Icons.Default.Message, "Send SMS") {
                        showMoreSheet = false; val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number")); smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { context.startActivity(smsIntent) } catch (_: Exception) {}
                    }
                    MoreSheetItem(Icons.Default.Block, "Block Number") {
                        showMoreSheet = false; com.example.call.data.BlacklistRepository(context).blockNumber(number); call.disconnect(); Toast.makeText(context, "$number blocked", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        if (showNoteDialog) {
            AlertDialog(
                onDismissRequest = { showNoteDialog = false },
                title = { Text("Call Note for $number") },
                text = { OutlinedTextField(value = noteText, onValueChange = { noteText = it }, modifier = Modifier.fillMaxWidth().height(120.dp), placeholder = { Text("Write a note about this call...") }) },
                confirmButton = { TextButton(onClick = { if (noteText.isNotEmpty()) { coroutineScope.launch { notesRepository.addNoteForContact(number, noteText); showNoteDialog = false; Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show() } } }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun MoreSheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun InCallKeypadOverlay(onDismiss: () -> Unit, onKey: (String) -> Unit, contentColor: Color, backgroundColors: List<Color>) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(enabled = false) {}) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Keypad", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#")).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { onKey(key) }, contentAlignment = Alignment.Center) {
                            Text(key, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
fun VideoCallSurface(call: Call, isLocal: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        val surface = Surface(st)
                        if (isLocal) {
                            call.videoCall?.setPreviewSurface(surface)
                            call.videoCall?.setCamera("1") // Front camera
                        } else {
                            call.videoCall?.setDisplaySurface(surface)
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        if (isLocal) {
                            call.videoCall?.setPreviewSurface(null)
                        } else {
                            call.videoCall?.setDisplaySurface(null)
                        }
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}
