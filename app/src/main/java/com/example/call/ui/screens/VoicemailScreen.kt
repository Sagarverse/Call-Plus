package com.example.call.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.VoicemailRecord
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun VoicemailScreen(voicemails: List<VoicemailRecord>, onCall: (String) -> Unit, onDelete: (Long) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit", color = IOSBlue, fontSize = 18.sp, modifier = Modifier.clickable {  })
                Text("Voicemail", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(40.dp))
            }
            
            Text("Voicemail", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            
            LazyColumn {
                items(voicemails) { entry ->
                    VoicemailItem(entry, onCall, onDelete)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Greeting", color = IOSBlue, fontSize = 18.sp, modifier = Modifier.clickable {  })
            }
        }
    }
}

@Composable
fun VoicemailItem(entry: VoicemailRecord, onCall: (String) -> Unit, onDelete: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(100) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    
    val formattedTime = remember(entry.date) {
        android.text.format.DateFormat.format("h:mm a", entry.date).toString()
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPosition = mediaPlayer.currentPosition
            } catch (e: Exception) {}
            delay(500)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(entry.number, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formattedTime, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
        }
        
        if (expanded) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = {
                    if (isPlaying) {
                        mediaPlayer.pause()
                    } else {
                        try {
                            // Dummy start if no source is set
                            // mediaPlayer.start()
                        } catch (e: Exception) {
                            mediaPlayer.reset()
                        }
                    }
                    isPlaying = !isPlaying
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                        contentDescription = "Play/Pause", 
                        tint = MaterialTheme.colorScheme.onSurface, 
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { 
                        currentPosition = it.toInt()
                        try { mediaPlayer.seekTo(it.toInt()) } catch(e: Exception) {}
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                TextButton(onClick = {
                    playbackSpeed = if (playbackSpeed == 1.5f) 1.0f else 1.5f
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                        } catch(e: Exception) {}
                    }
                }) {
                    Text("${playbackSpeed}x", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                VoicemailActionButton(Icons.Default.VolumeUp, "Speaker")
                VoicemailActionButton(Icons.Default.Call, "Call Back", onClick = { onCall(entry.number) })
                VoicemailActionButton(Icons.Default.Delete, "Delete", onClick = { onDelete(entry.id) })
            }
        }
    }
}

@Composable
fun VoicemailActionButton(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(icon, contentDescription = label, tint = IOSBlue, modifier = Modifier.size(24.dp))
        Text(label, color = IOSBlue, fontSize = 10.sp)
    }
}
