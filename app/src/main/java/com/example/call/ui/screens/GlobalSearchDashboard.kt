package com.example.call.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Contact
import com.example.call.data.CallRecord
import com.example.call.data.Note
import com.example.call.ui.theme.*
import com.example.call.ui.components.*

@Composable
fun GlobalSearchDashboard(
    contacts: List<Contact>,
    callLogs: List<CallRecord>,
    notes: List<Note>,
    onContactClick: (Contact) -> Unit,
    onCallClick: (String) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) emptyList()
        else contacts.filter { it.name.contains(searchQuery, true) || it.number.contains(searchQuery) }
    }
    
    val filteredNotes = remember(searchQuery, notes) {
        if (searchQuery.isBlank()) emptyList()
        else notes.filter { it.content.contains(searchQuery, true) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Mesh Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color.Black)
                        )
                    )
            )
            
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Spacer(modifier = Modifier.height(60.dp))
                
                GlassmorphicContainer(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    borderAlpha = 0.2f
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = VisionPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search your world...", color = IOSGray, fontSize = 16.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = VisionPrimary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = IOSGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    if (filteredContacts.isNotEmpty()) {
                        item { SectionHeader("Contacts") }
                        items(filteredContacts) { contact ->
                            SearchItem(
                                title = contact.name,
                                subtitle = contact.number,
                                icon = Icons.Default.Person,
                                onClick = { onContactClick(contact) }
                            )
                        }
                    }
                    
                    if (filteredNotes.isNotEmpty()) {
                        item { SectionHeader("Intelligence Notes") }
                        items(filteredNotes) { note ->
                            val contactName = contacts.find { it.number == note.contactNumber }?.name ?: note.contactNumber ?: "Unknown"
                            SearchItem(
                                title = note.content,
                                subtitle = "Related to $contactName",
                                icon = Icons.Default.AutoAwesome,
                                onClick = { onCallClick(note.contactNumber ?: "") }
                            )
                        }
                    }
                    
                    if (searchQuery.isNotEmpty() && filteredContacts.isEmpty() && filteredNotes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, tint = IOSGray.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No discoveries for '$searchQuery'", color = IOSGray, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            // Floating Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = VisionPrimary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SearchItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = VisionPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
