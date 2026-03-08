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
import androidx.compose.foundation.lazy.grid.items
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
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(favorites) { contact ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onCall(contact.number) }
                    ) {
                        // Large Avatar Box
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = com.example.call.ui.screens.getGradientForContact(contact.number, contact.photoUri, context)
                                )),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 32.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Name and optional Subtext
                        Text(
                            text = contact.name.substringBefore(" "),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "mobile",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        }
    }
}
