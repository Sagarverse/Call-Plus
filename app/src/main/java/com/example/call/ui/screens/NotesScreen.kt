package com.example.call.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Note
import com.example.call.ui.components.CenterText
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.theme.*

@Composable
fun NotesScreen(
    notes: List<Note>,
    onSaveNotes: (List<Note>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingNote = null
                    noteText = ""
                    showAddDialog = true 
                },
                containerColor = IOSBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SagarCallBanner(modifier = Modifier.align(Alignment.End))
                Text(
                    "Notes", 
                    fontSize = 34.sp, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (notes.isEmpty()) {
                    CenterText("No Notes Yet")
                } else {
                    val sortedNotes = remember(notes) {
                        notes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.date })
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(sortedNotes) { note ->
                            NoteItem(
                                note = note,
                                onEdit = { 
                                    editingNote = it
                                    noteText = it.content
                                    showAddDialog = true
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
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
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
                    showAddDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    onEdit: (Note) -> Unit,
    onDelete: (Long) -> Unit,
    onPinToggle: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit(note) },
                onDoubleClick = { onPinToggle(note.id) },
                onLongClick = { 
                    if (!note.isPinned) {
                        onDelete(note.id)
                    }
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    note.content,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    android.text.format.DateFormat.format("MMM dd, h:mm a", note.date).toString(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (note.isPinned) {
                Icon(
                    Icons.Default.Build, 
                    contentDescription = "Pinned",
                    tint = IOSBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
