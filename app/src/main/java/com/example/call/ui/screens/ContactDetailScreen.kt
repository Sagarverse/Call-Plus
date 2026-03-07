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

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color.Transparent),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Contacts", 
                    color = IOSBlue, 
                    fontSize = 17.sp, 
                    modifier = Modifier.clickable { onBack() }
                )
                Text(
                    "Edit",
                    color = IOSBlue,
                    fontSize = 17.sp,
                    modifier = Modifier.clickable {
                        // Open system contact editor
                        val lookupUri = android.net.Uri.withAppendedPath(
                            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            android.net.Uri.encode(contact.number)
                        )
                        val cursor = context.contentResolver.query(
                            lookupUri,
                            arrayOf(android.provider.ContactsContract.PhoneLookup.LOOKUP_KEY),
                            null, null, null
                        )
                        val lookupKey = cursor?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                        val editIntent = if (lookupKey != null) {
                            android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                                data = android.net.Uri.withAppendedPath(
                                    android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey
                                )
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
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(VisionPrimary, IOSBlue))),
                contentAlignment = Alignment.Center
            ) {
                Text(contact.name.take(1), fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(contact.name, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ContactActionItem(Icons.Default.Email, "message", onClick = { onMessage(contact.number) })
                ContactActionItem(Icons.Default.Call, "call", onClick = { onCall(contact.number) })
                ContactActionItem(Icons.Default.Videocam, "video", onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://meet.google.com/"))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                })
                ContactActionItem(Icons.Default.Email, "mail", onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO,
                        android.net.Uri.parse("mailto:"))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(android.content.Intent.createChooser(intent, "Send Email")) } catch (_: Exception) {}
                })
                var showReminderMenu by remember { mutableStateOf(false) }
                ContactActionItem(Icons.Default.Notifications, "remind", onClick = { showReminderMenu = true })
                
                if (showReminderMenu) {
                    AlertDialog(
                        onDismissRequest = { showReminderMenu = false },
                        title = { Text("Set Reminder") },
                        text = {
                            Column {
                                listOf("In 1 hour" to 60L, "Tomorrow morning" to 1440L).forEach { (label, mins) ->
                                    Text(
                                        text = label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                android.widget.Toast.makeText(context, "Reminder feature coming to details soon", android.widget.Toast.LENGTH_SHORT).show()
                                                showReminderMenu = false
                                            }
                                            .padding(12.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

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

            // Notes Section
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
                        Text("notes", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "Add Note", 
                            tint = IOSBlue,
                            modifier = Modifier.size(18.dp).clickable { 
                                noteText = ""
                                showAddNoteDialog = true 
                            }
                        )
                    }
                    
                    if (notes.isEmpty()) {
                        Text("No notes for this contact", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f), modifier = Modifier.padding(top = 8.dp))
                    } else {
                        notes.reversed().forEach { note ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(note.content, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                android.text.format.DateFormat.format("MMM d, yyyy", note.date).toString(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f)
                            )
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
fun ContactActionItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    com.example.call.ui.components.GlassmorphicContainer(
        modifier = Modifier.width(80.dp).height(60.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                icon, 
                contentDescription = label, 
                tint = if (enabled) IOSBlue else IOSGray.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Text(
                label, 
                fontSize = 11.sp, 
                color = if (enabled) IOSBlue else IOSGray.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
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
