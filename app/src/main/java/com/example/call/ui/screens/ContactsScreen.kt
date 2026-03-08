package com.example.call.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable
fun ContactsScreen(
    contacts: List<Contact>, 
    favorites: List<Contact> = emptyList(),
    onSettingsClick: () -> Unit = {},
    onCall: (String) -> Unit = {},
    onContactClick: (Contact) -> Unit = {},
    onToggleFavorite: (Contact) -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredContacts = remember(searchQuery, contacts) {
        if (searchQuery.isEmpty()) contacts
        else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Contacts", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = VisionPrimary)
                }
            }

        // Vision Search Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text("SEARCH CONTACTS", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth(),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(VisionPrimary)
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
            
            val groupedContacts = remember(filteredContacts) {
                filteredContacts.groupBy { contact ->
                    val firstChar = contact.name.firstOrNull()?.uppercase()
                    if (firstChar != null && firstChar[0].isLetter()) firstChar else "#"
                }.toSortedMap()
            }

            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
            ) {
                groupedContacts.forEach { (initial, sublist) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f) // Glassy OLED backing
                        ) {
                            Text(
                                text = initial,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = VisionPrimary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    items(sublist.size) { index ->
                        val contact = sublist[index]
                        val isFavorite = favorites.any { it.number == contact.number }
                        
                        SwipeableActionItem(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            onRightSwipe = {
                                keyboardController?.hide()
                                onCall(contact.number)
                            },
                            onLeftSwipe = {
                                keyboardController?.hide()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contact.number}"))
                                context.startActivity(intent)
                            },
                            content = {
                                Column {
                                    ListItem(
                                        modifier = Modifier.clickable {
                                            keyboardController?.hide()
                                            onContactClick(contact)
                                        },
                                        headlineContent = {
                                            Text(
                                                text = contact.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
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
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = contact.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                    color = VisionPrimary
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            if (isFavorite) {
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = "Favorite",
                                                    tint = VisionPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = Color.Transparent,
                                            headlineColor = MaterialTheme.colorScheme.onSurface,
                                            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    
                                    if (index < sublist.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }
}
