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
import com.example.call.ui.theme.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(
    records: List<CallRecord>, 
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
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))
        if (isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                        Text(if (selectedIds.size == records.size) "Deselect All" else "Select All", color = IOSBlue)
                    }
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { 
                            onDeleteMultiple(selectedIds.toList())
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = IOSRed)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recents", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (records.isEmpty()) {
            CenterText("No Recent Calls")
        } else {
            val context = LocalContext.current
            LazyColumn {
                items(records) { record ->
                    SwipeableActionItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        onRightSwipe = { onCall(record.number) },
                        onLeftSwipe = { 
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${record.number}"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        onDeleteSwipe = { onDelete(record.id) },
                        content = {
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
                                        text = record.name, 
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = if (record.type == "Missed") IOSRed else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                supportingContent = { 
                                    Text(
                                        text = "${record.number} • ${record.time}", 
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) 
                                },
                                leadingContent = {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = selectedIds.contains(record.id),
                                            onCheckedChange = { checked -> 
                                                selectedIds = if (checked) selectedIds + record.id else selectedIds - record.id
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = IOSBlue)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (record.type == "Missed") IOSRed.copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (record.type == "Incoming") Icons.Default.Call else Icons.Default.ArrowForward,
                                                contentDescription = null,
                                                tint = if (record.type == "Missed") IOSRed else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
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
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
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
