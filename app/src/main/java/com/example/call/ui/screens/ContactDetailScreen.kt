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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.Contact
import com.example.call.ui.components.DetailTextItem
import com.example.call.ui.theme.*

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
    onToggleBlock: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).background(MaterialTheme.colorScheme.surface),
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
                    modifier = Modifier.clickable { onEdit() }
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
                    .background(IOSGray.copy(alpha = 0.3f)),
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
                ContactActionItem(Icons.Default.Face, "video", enabled = false)
                ContactActionItem(Icons.Default.Email, "mail", enabled = false)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("phone", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(contact.number, fontSize = 17.sp, color = IOSBlue)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    DetailTextItem("Send Message", onClick = { onMessage(contact.number) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DetailTextItem("Share Contact")
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    DetailTextItem(
                        if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        onClick = onToggleFavorite
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
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
}

@Composable
fun ContactActionItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Surface(
        onClick = if (enabled) onClick else ({}),
        modifier = Modifier.width(80.dp).height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
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
