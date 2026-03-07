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
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.onSurface)
        Text("Favorites", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
        if (favorites.isEmpty()) {
            CenterText("No Favorites")
        } else {
            val context = LocalContext.current
            LazyColumn {
                items(favorites) { contact ->
                    com.example.call.ui.components.GlassmorphicContainer(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    ) {
                        SwipeableActionItem(
                            modifier = Modifier.fillMaxWidth(),
                            onRightSwipe = { onCall(contact.number) },
                            onLeftSwipe = { 
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contact.number}"))
                                context.startActivity(intent)
                            },
                            content = {
                                ListItem(
                                    modifier = Modifier.clickable { onCall(contact.number) },
                                    headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp) },
                                    supportingContent = { Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(MaterialTheme.colorScheme.surface), 
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(contact.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = VisionPrimary, fontSize = 18.sp)
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { onInfoClick(contact) }) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = VisionPrimary.copy(alpha = 0.6f))
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(140.dp)) }
            }
        }
        }
    }
}
