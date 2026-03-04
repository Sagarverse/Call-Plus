package com.example.call.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.theme.*
import java.io.File

@Composable
fun SettingsScreen(
    shakeEnabled: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    themePreference: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp)
        ) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))
        Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                SettingsItem("Privacy Policy", Icons.Default.Lock, onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://example.com/privacy"))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                SettingsItem("App Version", Icons.Default.Info, "1.0.0")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                SettingsItem("Rate the App", Icons.Default.Star, onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=${context.packageName}"))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                })
            }
        }

        Text("Display & Experience", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                ThemeSelectionRow(
                    title = "System Default",
                    isSelected = themePreference == "System",
                    onClick = { onThemeChange("System") }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ThemeSelectionRow(
                    title = "Light",
                    isSelected = themePreference == "Light",
                    onClick = { onThemeChange("Light") }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ThemeSelectionRow(
                    title = "Dark",
                    isSelected = themePreference == "Dark",
                    onClick = { onThemeChange("Dark") }
                )
            }
        }

        Text("Call Features", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = IOSBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Shake to Answer/End", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Switch(checked = shakeEnabled, onCheckedChange = onShakeToggle)
                }
                
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                
                SettingsItem(
                    title = "Call Recordings",
                    icon = Icons.Default.FiberManualRecord,
                    onClick = {
                        // Open the app-specific CallRecordings directory
                        val dir = context.getExternalFilesDir("CallRecordings")
                        if (dir != null && !dir.exists()) dir.mkdirs()
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    dir ?: context.filesDir
                                ),
                                "resource/folder"
                            )
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Recordings saved to: ${context.getExternalFilesDir("CallRecordings")?.absolutePath}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "This application uses system-level permissions to manage calls and contacts. All data is processed locally on-device.",
            fontSize = 12.sp,
            color = IOSGray,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )
        }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, value: String? = null, onClick: () -> Unit = {}) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, contentDescription = null, tint = IOSBlue) },
        trailingContent = {
            if (value != null) {
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
            } else {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun ThemeSelectionRow(title: String, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface) },
        leadingContent = { 
            Icon(
                if (title == "Light") Icons.Default.LightMode 
                else if (title == "Dark") Icons.Default.DarkMode
                else Icons.Default.Settings, 
                contentDescription = null, 
                tint = if (isSelected) IOSBlue else IOSGray
            ) 
        },
        trailingContent = {
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = IOSBlue)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
