package com.example.call.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Contact
import com.example.call.ui.components.CenterText
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.components.SwipeableActionItem
import com.example.call.ui.theme.*

@Composable
fun ContactsScreen(
    contacts: List<Contact>, 
    favorites: List<Contact> = emptyList(),
    onSettingsClick: () -> Unit = {},
    onCall: (String) -> Unit = {},
    onContactClick: (Contact) -> Unit = {},
    onToggleFavorite: (Contact) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isEmpty()) contacts
        else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Contacts", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // iOS Style Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Clear", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.size(20.dp).clickable { searchQuery = "" }
                    )
                }
            }
        }

        if (filteredContacts.isEmpty()) {
            CenterText(if (searchQuery.isEmpty()) "No Contacts Found" else "No Results for '$searchQuery'")
        } else {
            val context = LocalContext.current
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredContacts) { contact ->
                    val isFavorite = favorites.any { it.number == contact.number }
                    SwipeableActionItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        onRightSwipe = { onCall(contact.number) },
                        onLeftSwipe = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contact.number}"))
                            context.startActivity(intent)
                        },
                        content = {
                            ListItem(
                                modifier = Modifier.clickable { onContactClick(contact) },
                                headlineContent = { 
                                    Text(
                                        text = contact.name, 
                                        color = MaterialTheme.colorScheme.onSurface, 
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ) 
                                },
                                supportingContent = { 
                                    Text(
                                        text = contact.number, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp
                                    ) 
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = contact.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onToggleFavorite(contact) }) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Favorite",
                                            tint = if (isFavorite) Color(0xFFFFCC00) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    headlineColor = MaterialTheme.colorScheme.onSurface,
                                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(100.dp)) } // Edge-to-edge padding
            }
        }
        }
    }
}
