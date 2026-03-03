package com.example.call.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
fun FavoritesScreen(
    favorites: List<Contact>, 
    onCall: (String) -> Unit,
    onInfoClick: (Contact) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))
        Text("Favorites", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(16.dp))
        if (favorites.isEmpty()) {
            CenterText("No Favorites")
        } else {
            val context = LocalContext.current
            LazyColumn {
                items(favorites) { contact ->
                    SwipeableActionItem(
                        onRightSwipe = { onCall(contact.number) },
                        onLeftSwipe = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contact.number}"))
                            context.startActivity(intent)
                        },
                        content = {
                            ListItem(
                                modifier = Modifier.clickable { onCall(contact.number) },
                                headlineContent = { Text(contact.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) },
                                supportingContent = { Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                leadingContent = {
                                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(IOSGray.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        Text(contact.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = { onInfoClick(contact) }) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                }
            }
        }
        }
    }
}
