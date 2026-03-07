package com.example.call.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.VoicemailRecord
import com.example.call.data.Note
import com.example.call.data.Contact
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.combinedClickable

@Composable
fun VoicemailScreen(
    voicemails: List<VoicemailRecord>,
    notes: List<Note>,
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    onDeleteVoicemail: (Long) -> Unit,
    onSaveNotes: (List<Note>) -> Unit
) {
    val context = LocalContext.current
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var currentPlayingId by remember { mutableStateOf<Long?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isEditing) "Done" else "Edit",
                    color = IOSBlue,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { isEditing = !isEditing }
                )
                Text(
                    "Dual View",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(48.dp)) // Placeholder for balance
            }

            // ── Dual View Container ──────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                // Top half: Voicemail
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        Text(
                            "Voicemail",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        if (voicemails.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No voicemails", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn {
                                items(voicemails, key = { it.id }) { entry ->
                                    VoicemailItem(
                                        entry = entry, 
                                        isActive = currentPlayingId == entry.id,
                                        isEditing = isEditing,
                                        onPlayStateChange = { playing ->
                                            currentPlayingId = if (playing) entry.id else null
                                        },
                                        onCall = onCall, 
                                        onDeleteVoicemail = onDeleteVoicemail
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Bottom half: Notes
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Notes",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { 
                                editingNote = null
                                noteText = ""
                                showAddNoteDialog = true 
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Note", tint = IOSBlue)
                            }
                        }
                        if (notes.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No notes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val sortedNotes = remember(notes) {
                                notes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.date })
                            }
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                items(sortedNotes) { note ->
                                    NoteItem(
                                        note = note,
                                        contacts = contacts,
                                        onEdit = { 
                                            editingNote = it
                                            noteText = it.content
                                            showAddNoteDialog = true
                                        },
                                        onDelete = { id ->
                                            onSaveNotes(notes.filter { it.id != id })
                                        },
                                        onPinToggle = { id ->
                                            onSaveNotes(notes.map { if (it.id == id) it.copy(isPinned = !it.isPinned) else it })
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text(if (editingNote != null) "Edit Note" else "New Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    placeholder = { Text("What's on your mind?") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotEmpty()) {
                        val newNotes = if (editingNote != null) {
                            notes.map { if (it.id == editingNote!!.id) it.copy(content = noteText) else it }
                        } else {
                            notes + Note(System.currentTimeMillis(), noteText, System.currentTimeMillis())
                        }
                        onSaveNotes(newNotes)
                    }
                    showAddNoteDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VoicemailItem(
    entry: VoicemailRecord,
    isActive: Boolean,
    isEditing: Boolean = false,
    onPlayStateChange: (Boolean) -> Unit,
    onCall: (String) -> Unit,
    onDeleteVoicemail: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // MediaPlayer state
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var playerError by remember { mutableStateOf(false) }

    val formattedDate = remember(entry.date) {
        android.text.format.DateFormat.format("MMM d, h:mm a", entry.date).toString()
    }

    val formattedDuration = remember(entry.duration) {
        if (entry.duration > 0) "%d:%02d".format(entry.duration / 60, entry.duration % 60)
        else "--:--"
    }

    // Progress polling while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    currentPosition = mp.currentPosition
                    duration = mp.duration.coerceAtLeast(1)
                }
            } catch (_: Exception) {}
            delay(200)
        }
    }

    // Release MediaPlayer when item leaves composition or becomes inactive
    LaunchedEffect(isActive) {
        if (!isActive) {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    DisposableEffect(entry.id) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    val bgColor by animateColorAsState(
        if (entry.isLocal && !expanded)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        else Color.Transparent,
        label = "bgColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isEditing) {
                IconButton(onClick = { onDeleteVoicemail(entry.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.RemoveCircle, contentDescription = "Delete", tint = IOSRed)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(IOSBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.name.firstOrNull()?.uppercase() ?: "?",
                    color = IOSBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    entry.number,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formattedDate, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formattedDuration, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.isLocal) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = IOSGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        "Local",
                        color = IOSGreen,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ── Expanded content ─────────────────────────────────────────────────
        if (expanded) {
            Spacer(modifier = Modifier.height(16.dp))

            // Transcript section
            if (entry.transcript.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = null,
                                tint = IOSBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Transcript",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IOSBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            entry.transcript,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Audio playback — only shown if we have a local recording file
            if (entry.uri != null) {
                if (playerError) {
                    Text(
                        "Cannot play this recording",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Play/Pause button
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    mediaPlayer?.pause()
                                    isPlaying = false
                                    onPlayStateChange(false)
                                } else {
                                    onPlayStateChange(true)
                                    if (mediaPlayer == null) {
                                        // Initialize player
                                        val mp = MediaPlayer()
                                        try {
                                            mp.setDataSource(context, entry.uri)
                                            mp.prepare()
                                            duration = mp.duration.coerceAtLeast(1)
                                            mp.setOnCompletionListener {
                                                isPlaying = false
                                                currentPosition = 0
                                                onPlayStateChange(false)
                                            }
                                            mediaPlayer = mp
                                        } catch (e: Exception) {
                                            mp.release()
                                            playerError = true
                                            return@IconButton
                                        }
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        try {
                                            mediaPlayer?.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                                        } catch (_: Exception) {}
                                    }
                                    mediaPlayer?.start()
                                    isPlaying = true
                                }
                            }
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Progress slider
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { fraction ->
                                val seek = (fraction * duration).toInt()
                                currentPosition = seek
                                try { mediaPlayer?.seekTo(seek) } catch (_: Exception) {}
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            colors = SliderDefaults.colors(thumbColor = IOSBlue, activeTrackColor = IOSBlue)
                        )

                        // Speed toggle
                        TextButton(onClick = {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    mediaPlayer?.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                                } catch (_: Exception) {}
                            }
                        }) {
                            Text("${playbackSpeed}x", color = IOSBlue, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Text(
                    "No audio file (carrier voicemail)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                VoicemailActionButton(Icons.Default.Call, "Call Back") { onCall(entry.number) }
                VoicemailActionButton(
                    Icons.Default.Message, "SMS"
                ) {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("sms:${entry.number}")
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
                VoicemailActionButton(Icons.Default.Delete, "Delete") {
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    onPlayStateChange(false)
                                    onDeleteVoicemail(entry.id)
                                }
            }
        }
    }
}

@Composable
fun VoicemailActionButton(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(icon, contentDescription = label, tint = IOSBlue, modifier = Modifier.size(24.dp))
        Text(label, color = IOSBlue, fontSize = 11.sp)
    }
}


