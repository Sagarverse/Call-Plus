package com.example.call

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.compose.animation.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.example.call.ui.theme.*
import com.example.call.data.*
import com.example.call.ui.screens.*
import com.example.call.ui.components.*
import androidx.compose.ui.draw.scale

@Composable
fun CallApp(themePreference: String = "System", onThemeChange: (String) -> Unit = {}) {
    val context = LocalContext.current
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    
    // Repositories
    val repository = remember { ContactRepository(context) }
    val voicemailRepository = remember { VoicemailRepository(context) }
    val blacklistRepository = remember { BlacklistRepository(context) }
    val notesRepository = remember { NotesRepository(context) }
    val reminderRepository = remember { ReminderRepository(context) }
    val prefs = remember { context.getSharedPreferences("call_prefs", Context.MODE_PRIVATE) }

    // State
    var selectedTab by remember { mutableIntStateOf(3) } // Default to Keypad
    val activeCall by CallManager.activeCall.collectAsState()
    
    // Sync current tab to MainActivity for volume button shortcuts
    LaunchedEffect(selectedTab) {
        MainActivity.currentTab = selectedTab
    }

    // Reactive data collection
    val contacts by repository.contacts.collectAsState()
    val callLogs by repository.callLogs.collectAsState()
    val voicemails by voicemailRepository.voicemails.collectAsState()
    val notes by notesRepository.notes.collectAsState()
    
    var favoritesUpdateTrigger by remember { mutableIntStateOf(0) }
    val favorites = remember(contacts, favoritesUpdateTrigger) {
        val savedFavs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        contacts.filter { savedFavs.contains(it.number) }
    }
    
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var isCallMinimized by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var shakeEnabled by remember { mutableStateOf(prefs.getBoolean("shake_enabled", false)) }
    val coroutineScope = rememberCoroutineScope()

    // Initial Data Load
    LaunchedEffect(Unit) {
        repository.refreshContacts()
        repository.refreshCallLogs()
        voicemailRepository.refresh()
        notesRepository.refreshNotes()
    }

    // Refresh voicemails + call log when a call ends
    LaunchedEffect(activeCall) {
        if (activeCall == null) {
            repository.refreshCallLogs()
            voicemailRepository.refresh()
        }
    }

    val toggleFavorite: (Contact) -> Unit = { contact ->
        val currentFavs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        val newFavs = if (currentFavs.contains(contact.number)) {
            currentFavs.filter { it != contact.number }.toSet()
        } else {
            currentFavs + contact.number
        }
        prefs.edit().putStringSet("favorites", newFavs).apply()
        favoritesUpdateTrigger++
    }

    BackHandler(enabled = selectedContact != null || isCallMinimized || showSummary || showSettings || showNotes) {
        when {
            selectedContact != null -> selectedContact = null
            showSummary -> showSummary = false
            showSettings -> showSettings = false
            showNotes -> showNotes = false
            isCallMinimized -> isCallMinimized = false
        }
    }

    Scaffold(
        bottomBar = {
            if (activeCall == null || isCallMinimized) {
                GlassmorphicBottomBar(selectedTab) { 
                    selectedTab = it
                    // Clear overlays when switching tabs
                    selectedContact = null
                    showSummary = false
                    showSettings = false
                    showNotes = false
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Main content based on tab
            Crossfade(targetState = selectedTab, label = "") { tab ->
                when (tab) {
                    0 -> FavoritesScreen(favorites, onCall = { CallManager.makeCall(context, it) }, onInfoClick = { selectedContact = it })
                    1 -> RecentsScreen(
                        records = callLogs, 
                        contacts = contacts,
                        onCall = { CallManager.makeCall(context, it) },
                        onDelete = { id ->
                            coroutineScope.launch {
                                repository.deleteCallLog(id)
                            }
                        },
                        onDeleteMultiple = { ids ->
                            coroutineScope.launch {
                                repository.deleteCallLogs(ids)
                            }
                        },
                        onInfoClick = { num -> 
                            selectedContact = contacts.findContactFlexible(num) ?: Contact("Unknown", num)
                        }
                    )
                    2 -> ContactsScreen(
                        contacts = contacts, 
                        favorites = favorites,
                        onSettingsClick = { showSettings = true },
                        onCall = { CallManager.makeCall(context, it) },
                        onContactClick = { selectedContact = it },
                        onToggleFavorite = toggleFavorite
                    )
                    3 -> KeypadScreen(contacts, 
                        onCall = { CallManager.makeCall(context, it) }, 
                        onCallClick = { showSummary = true }
                    )
                    4 -> VoicemailScreen(
                        voicemails = voicemails,
                        notes = notes,
                        contacts = contacts,
                        onCall = { CallManager.makeCall(context, it) },
                        onDeleteVoicemail = { id ->
                            coroutineScope.launch {
                                voicemailRepository.deleteLocalVoicemail(id)
                            }
                        },
                        onSaveNotes = { 
                            coroutineScope.launch {
                                notesRepository.saveNotes(it)
                            }
                        }
                    )
                }
            }

            // Overlays
            if (selectedContact != null) {
                var isBlocked by remember(selectedContact) {
                    mutableStateOf(blacklistRepository.isBlocked(selectedContact!!.number))
                }
                ContactDetailScreen(
                    contact = selectedContact!!,
                    isFavorite = favorites.any { it.number == selectedContact!!.number },
                    onToggleFavorite = { toggleFavorite(selectedContact!!) },
                    onBack = { selectedContact = null },
                    onCall = { CallManager.makeCall(context, it) },
                    onMessage = { 
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$it"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    isBlocked = isBlocked,
                    onToggleBlock = {
                        if (isBlocked) blacklistRepository.unblockNumber(selectedContact!!.number)
                        else blacklistRepository.blockNumber(selectedContact!!.number)
                        isBlocked = !isBlocked
                    },
                    notes = notes.filter { it.contactNumber == selectedContact!!.number || (it.contactNumber != null && selectedContact!!.number.endsWith(it.contactNumber!!)) },
                    onAddNote = { content ->
                        coroutineScope.launch {
                            notesRepository.addNoteForContact(selectedContact!!.number, content)
                        }
                    },
                    allCallLogs = callLogs
                )
            }

            if (showSummary) {
                SummaryScreen(callLogs, onBack = { showSummary = false })
            }

            if (showSettings) {
                SettingsScreen(
                    shakeEnabled = shakeEnabled, 
                    onShakeToggle = {
                        shakeEnabled = it
                        prefs.edit().putBoolean("shake_enabled", it).apply()
                    },
                    themePreference = themePreference,
                    onThemeChange = onThemeChange
                )
            }

            activeCall?.let { call ->
                if (!isCallMinimized) {
                    CallingScreen(
                        call = call,
                        repository = repository,
                        notesRepository = notesRepository,
                        reminderRepository = reminderRepository,
                        onMinimize = { isCallMinimized = true }
                    )
                } else {
                    ActiveCallMinimizedBar(call) { isCallMinimized = false }
                }
            }
        }
    }
}

@Composable
fun ActiveCallMinimizedBar(call: Call, onClick: () -> Unit) {
    val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
    val contactGradient = remember(number) { getGradientForContact(number) }
    val contentColor = adaptiveTextColor(contactGradient.first())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onClick() },
        color = contactGradient.first().copy(alpha = 0.9f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Call, contentDescription = null, tint = contentColor)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Return to Call: $number", color = contentColor, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { call.disconnect() }) {
                Icon(Icons.Default.Clear, contentDescription = "End", tint = IOSRed)
            }
        }
    }
}

@Composable
fun GlassmorphicBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Triple("Favorites", Icons.Default.Star, 0),
        Triple("Recents", Icons.Default.Refresh, 1),
        Triple("Contacts", Icons.Default.Person, 2),
        Triple("Keypad", Icons.Default.Call, 3),
        Triple("Voicemail", Icons.Default.Voicemail, 4) // Fixed icon
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 20.dp), // Reduced horizontal padding to prevent clipping
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Take most of the width but keep it pill-like
                .clip(RoundedCornerShape(40.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
        ) {
            // Premium Blur Background
            Surface(
                color = Color.Transparent,
                modifier = Modifier.blur(60.dp),
                shape = RoundedCornerShape(40.dp)
            ) {}
            
            // Edge Highlight
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.2f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(40.dp)
                    )
            )

            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, // Better distribution for 5 items
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (label, icon, index) ->
                    val isSelected = selectedTab == index
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        label = "tab_bg"
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f) // Ensure equal width for all tabs
                            .clip(RoundedCornerShape(32.dp))
                            .background(backgroundColor)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
