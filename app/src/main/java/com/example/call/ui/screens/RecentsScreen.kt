package com.example.call.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.CallRecord
import com.example.call.ui.components.CenterText
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.components.SwipeableActionItem
import com.example.call.ui.components.GlassmorphicContainer
import com.example.call.ui.theme.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    records: List<CallRecord>, 
    contacts: List<com.example.call.data.Contact>,
    onCall: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteMultiple: (List<Long>) -> Unit,
    onInfoClick: (String) -> Unit = {}
) {
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Exit selection mode if records become empty
    LaunchedEffect(records.size) {
        if (records.isEmpty()) {
            isSelectionMode = false
            selectedIds = emptySet()
        }
    }

    // Grouping Logic
    val groupedRecords = remember(records) {
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = today - 86400000L

        records.groupBy { record ->
            when {
                record.timestamp >= today -> "Today"
                record.timestamp >= yesterday -> "Yesterday"
                else -> "Older"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Banner
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
                SagarCallBanner(modifier = Modifier.align(Alignment.TopEnd), color = MaterialTheme.colorScheme.onSurface)
                if (isSelectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("${selectedIds.size} Selected", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Row {
                            TextButton(onClick = {
                                if (selectedIds.size == records.size) selectedIds = emptySet()
                                else selectedIds = records.map { it.id }.toSet()
                            }) {
                                Text(if (selectedIds.size == records.size) "Deselect All" else "Select All", color = VisionPrimary)
                            }
                            if (selectedIds.isNotEmpty()) {
                                IconButton(onClick = {
                                    onDeleteMultiple(selectedIds.toList())
                                    isSelectionMode = false
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Recents", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        if (records.isNotEmpty()) {
                            TextButton(onClick = { isSelectionMode = true }) {
                                Text("Edit", color = VisionPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                CenterText("No Recent Calls")
            } else {
                val context = LocalContext.current
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val groups = listOf("Today", "Yesterday", "Older")
                    groups.forEach { groupName ->
                        val groupItems = groupedRecords[groupName]
                        if (!groupItems.isNullOrEmpty()) {
                            stickyHeader {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = groupName.uppercase(),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VisionPrimary,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            items(groupItems.size) { index ->
                                val record = groupItems[index]
                                
                                SwipeableActionItem(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    onRightSwipe = { onCall(record.number) },
                                    onLeftSwipe = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${record.number}"))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try { context.startActivity(intent) } catch (_: Exception) {}
                                    },
                                    onDeleteSwipe = { onDelete(record.id) },
                                    content = {
                                        val contactName = remember(record.number, contacts) {
                                            val foundName = contacts.find { it.number.replace(Regex("[^0-9+]"), "").endsWith(record.number.replace(Regex("[^0-9+]"), "").takeLast(7)) }?.name
                                            if (!foundName.isNullOrBlank()) foundName
                                            else if (record.name.isNotBlank()) record.name
                                            else record.number
                                        }
                                        Column {
                                            ListItem(
                                                modifier = Modifier.combinedClickable(
                                                    onClick = { 
                                                        if (isSelectionMode) {
                                                            selectedIds = if (selectedIds.contains(record.id)) selectedIds - record.id else selectedIds + record.id
                                                        } else {
                                                            onCall(record.number)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!isSelectionMode) {
                                                            isSelectionMode = true
                                                            selectedIds = setOf(record.id)
                                                        }
                                                    }
                                                ),
                                                headlineContent = { 
                                                    Text(
                                                        text = contactName, 
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 17.sp,
                                                        color = if (record.type == "Missed") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                                                    ) 
                                                },
                                                supportingContent = { 
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = record.time, 
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ) 
                                                        record.simLabel?.let { label ->
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Surface(
                                                                color = VisionPrimary.copy(alpha = 0.1f),
                                                                shape = RoundedCornerShape(4.dp),
                                                            ) {
                                                                Text(
                                                                    text = label.uppercase(),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = VisionPrimary,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                leadingContent = {
                                                    if (isSelectionMode) {
                                                        Checkbox(
                                                            checked = selectedIds.contains(record.id),
                                                            onCheckedChange = { checked -> 
                                                                selectedIds = if (checked) selectedIds + record.id else selectedIds - record.id
                                                            },
                                                            colors = CheckboxDefaults.colors(checkedColor = VisionPrimary)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(44.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.surface),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = if (record.type == "Incoming") Icons.Default.Call else Icons.Default.ArrowForward,
                                                                contentDescription = null,
                                                                tint = if (record.type == "Missed") Color(0xFFEF4444) else VisionPrimary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                trailingContent = {
                                                    if (!isSelectionMode) {
                                                        IconButton(
                                                            onClick = { onInfoClick(record.number) },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Info, 
                                                                contentDescription = "Info", 
                                                                tint = VisionPrimary.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                colors = ListItemDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    headlineColor = MaterialTheme.colorScheme.onSurface,
                                                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            
                                            if (index < groupItems.size - 1) {
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
}
