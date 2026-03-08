package com.example.call.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Contact
import com.example.call.data.Note
import com.example.call.ui.components.DetailTextItem
import com.example.call.ui.theme.*
import com.example.call.util.QRCodeGenerator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.graphics.Bitmap

data class TimelineItem(
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val type: TimelineType,
    val icon: ImageVector,
    val color: Color,
    val content: String = ""
)

enum class TimelineType { CALL, NOTE, REMINDER }

@Composable
fun ContactDetailScreen(
    contact: Contact,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEdit: () -> Unit = {},
    isBlocked: Boolean = false,
    onToggleBlock: () -> Unit = {},
    notes: List<Note> = emptyList(),
    onAddNote: (String) -> Unit = {},
    allCallLogs: List<com.example.call.data.CallRecord> = emptyList()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showQRDialog by remember { mutableStateOf(false) }

    val contactLogs = remember(allCallLogs, contact.number) {
        allCallLogs.filter { it.number == contact.number || contact.number.endsWith(it.number) || it.number.endsWith(contact.number) }
    }
    
    val timelineItems = remember(contactLogs, notes) {
        val items = mutableListOf<TimelineItem>()
        
        // Add Call Logs
        contactLogs.forEach { log ->
            val icon = when (log.type) {
                "Incoming" -> Icons.Default.CallReceived
                "Outgoing" -> Icons.Default.CallMade
                "Missed" -> Icons.Default.CallMissed
                else -> Icons.Default.Call
            }
            val color = if (log.type == "Missed") IOSRed else IOSGreen
            items.add(TimelineItem(
                title = log.type,
                subtitle = log.time,
                timestamp = log.timestamp,
                type = TimelineType.CALL,
                icon = icon,
                color = color
            ))
        }
        
        // Add Notes
        notes.forEach { note ->
            items.add(TimelineItem(
                title = "Note",
                subtitle = android.text.format.DateFormat.format("MMM d, h:mm a", note.date).toString(),
                timestamp = note.date,
                type = TimelineType.NOTE,
                icon = Icons.Default.Notes,
                color = IOSBlue,
                content = note.content
            ))
        }
        
        items.sortedByDescending { it.timestamp }
    }
    
    val intel = remember(contactLogs) {
        val total = contactLogs.size
        val avgDur = if (total > 0) contactLogs.sumOf { it.duration } / total else 0
        val lastCall = contactLogs.maxByOrNull { it.timestamp }?.timestamp
        val health = when {
            total > 10 -> "Highly Connected"
            total > 3 -> "Frequent Follow-up"
            total > 0 -> "Occasional"
            else -> "New Connection"
        }
        val healthColor = when {
            total > 10 -> IOSGreen
            total > 3 -> IOSBlue
            total > 0 -> Color(0xFFFF9500)
            else -> IOSGray
        }
        InteractionIntel(total, avgDur.toInt(), lastCall, health, healthColor)
    }

    val qrBitmap = remember(contact.number, showQRDialog) {
        if (showQRDialog) {
            QRCodeGenerator.generateQRCode("tel:${contact.number}")
        } else null
    }

    val backgroundGradient = remember(contact.number, contact.photoUri, context) { getGradientForContact(contact.number, contact.photoUri, context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Immersive Heroic Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(backgroundGradient[0], backgroundGradient[1], MaterialTheme.colorScheme.background)
                    ))
            ) {
                // Top Navigation row inside Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = innerPadding.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.2f))) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    IconButton(onClick = {
                        val lookupUri = android.net.Uri.withAppendedPath(
                            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            android.net.Uri.encode(contact.number)
                        )
                        val cursor = context.contentResolver.query(
                            lookupUri, arrayOf(android.provider.ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null
                        )
                        val lookupKey = cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
                        val editIntent = if (lookupKey != null) {
                            android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                                data = android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
                                putExtra("finishActivityOnSaveCompleted", true)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        } else {
                            android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).apply {
                                type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, contact.number)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        try { context.startActivity(editIntent) } catch (_: Exception) {}
                    }, modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.2f))) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                }

                // Profile Info
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(contact.name.take(1), fontSize = 48.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(contact.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(contact.number, fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
            
            // Floating Action Buttons Row (Overlapping the header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-28).dp) // Pull up into gradient
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FloatingActionItem(Icons.Default.Message, "Message", onClick = { onMessage(contact.number) })
                FloatingActionItem(Icons.Default.Call, "Call", isPrimary = true, onClick = { onCall(contact.number) })
                FloatingActionItem(Icons.Default.Videocam, "Video", onClick = { com.example.call.CallManager.makeCall(context, contact.number, isVideo = true) })
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Intelligence Section
            com.example.call.ui.components.GlassmorphicContainer(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("intelligence", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(intel.healthColor.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(intel.health, color = intel.healthColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IntelStat("Total Calls", intel.totalCalls.toString())
                        IntelStat("Avg. Duration", "${intel.avgDuration}s")
                        IntelStat("Last interaction", if (intel.lastCall != null) {
                            android.text.format.DateFormat.format("MMM d", intel.lastCall).toString()
                         } else "Never")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Phone Number Section
            com.example.call.ui.components.GlassmorphicContainer(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("phone", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(contact.number, fontSize = 17.sp, color = IOSBlue)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Interaction Timeline Section
            Text(
                "Activity History", 
                fontSize = 17.sp, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            )

            com.example.call.ui.components.GlassmorphicContainer(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("timeline", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "Add Note", 
                            tint = IOSBlue,
                            modifier = Modifier.size(20.dp).clickable { 
                                noteText = ""
                                showAddNoteDialog = true 
                            }
                        )
                    }
                    
                    if (timelineItems.isEmpty()) {
                        Text("No recent activity", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f), modifier = Modifier.padding(top = 8.dp))
                    } else {
                        timelineItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Timeline Line and Icon
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(item.color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(16.dp))
                                    }
                                    if (index < timelineItems.size - 1) {
                                        Box(
                                            modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                // Content
                                Column {
                                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    if (item.content.isNotEmpty()) {
                                        Text(item.content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    Text(item.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            com.example.call.ui.components.GlassmorphicContainer(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column {
                    DetailTextItem("Send Message", onClick = { onMessage(contact.number) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DetailTextItem("Share Contact")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DetailTextItem("Share as QR", onClick = { showQRDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DetailTextItem(
                        if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        onClick = onToggleFavorite
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            com.example.call.ui.components.GlassmorphicContainer(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                DetailTextItem(
                    if (isBlocked) "Unblock this Caller" else "Block this Caller",
                    color = IOSRed,
                    onClick = onToggleBlock
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("New Note for ${contact.name}") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Add some details...") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotEmpty()) {
                        onAddNote(noteText)
                        showAddNoteDialog = false
                    }
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

    if (showQRDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQRDialog = false },
            title = { Text("Contact QR Code", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(contact.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(contact.number, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Contact QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQRDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun FloatingActionItem(icon: ImageVector, label: String, isPrimary: Boolean = false, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (isPrimary) 64.dp else 56.dp)
                .clip(CircleShape)
                .background(if (isPrimary) VisionPrimary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() }
                .padding(if (isPrimary) 12.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (isPrimary) 32.dp else 24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

data class InteractionIntel(
    val totalCalls: Int,
    val avgDuration: Int,
    val lastCall: Long?,
    val health: String,
    val healthColor: Color
)

@Composable
fun IntelStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
