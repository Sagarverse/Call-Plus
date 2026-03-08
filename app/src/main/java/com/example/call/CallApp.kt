package com.example.call

import androidx.compose.animation.*
import androidx.compose.animation.core.*

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.call.ui.theme.*
import com.example.call.data.*
import com.example.call.ui.screens.*
import com.example.call.ui.components.*
import com.example.call.util.*
import androidx.compose.ui.draw.scale

sealed class OverlayType {
    object None : OverlayType()
    data class Contact(val contact: com.example.call.data.Contact) : OverlayType()
    object Summary : OverlayType()
    object Settings : OverlayType()
    object Search : OverlayType()
}

@Composable
fun CallApp(
    themePreference: String = "System", 
    onThemeChange: (String) -> Unit = {},
    isPipMode: Boolean = false
) {
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

    if (isPipMode && activeCall != null) {
        PipCallUI(activeCall!!, contacts)
        return
    }
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

    var showSearchDashboard by remember { mutableStateOf(false) }
    var lastCallNumber by remember { mutableStateOf<String?>(null) }
    var lastCallContact by remember { mutableStateOf<Contact?>(null) }
    var suggestedAction by remember { mutableStateOf<String?>(null) }

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
            
            // Refresh repositories when call ends
            if (activeCall == null) {
                repository.refreshCallLogs()
                voicemailRepository.refresh()
            }
        } else {
            // Store details for post-call use
            lastCallNumber = activeCall!!.details.handle.schemeSpecificPart
            lastCallContact = contacts.findContactFlexible(lastCallNumber ?: "")
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

    BackHandler(enabled = selectedContact != null || isCallMinimized || showSummary || showSettings || showNotes || showSearchDashboard) {
        when {
            showSearchDashboard -> showSearchDashboard = false
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
            // Main content based on tab - Swipeable Pager
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = selectedTab,
                pageCount = { 5 }
            )
            
            // Sync Pager -> Tab (only when scrolling has settled)
            LaunchedEffect(pagerState.settledPage) {
                selectedTab = pagerState.settledPage
            }
            
            // Sync Tab -> Pager (when clicking bottom bar)
            LaunchedEffect(selectedTab) {
                if (pagerState.currentPage != selectedTab && !pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(selectedTab)
                }
            }

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                // We don't disable userScrollEnabled here; 
                // nested swipeable items like contact rows will consume their own drag first.
            ) { page ->
                when (page) {
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
            
            // Search Trigger (Floating) - Only on Keypad (Dial) Page
            if (activeCall == null && !showSearchDashboard && !showSummary && !showSettings && selectedContact == null && selectedTab == 3) {
                FloatingActionButton(
                    onClick = { showSearchDashboard = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 24.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = VisionPrimary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }

            // Overlays with AnimatedContent for smooth transitions
            val overlayState = when {
                selectedContact != null -> OverlayType.Contact(selectedContact!!)
                showSummary -> OverlayType.Summary
                showSettings -> OverlayType.Settings
                showSearchDashboard -> OverlayType.Search
                else -> OverlayType.None
            }

            AnimatedContent(
                targetState = overlayState,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(400, easing = EaseInOutQuart)) + 
                     scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = EaseOutQuart)))
                    .togetherWith(fadeOut(animationSpec = tween(300, easing = EaseInOutQuart)))
                },
                label = "overlay_transition"
            ) { targetOverlay ->
                when (targetOverlay) {
                    is OverlayType.Contact -> {
                        val contact = targetOverlay.contact
                        var isBlocked by remember(contact) {
                            mutableStateOf(blacklistRepository.isBlocked(contact.number))
                        }
                        ContactDetailScreen(
                            contact = contact,
                            isFavorite = favorites.any { it.number == contact.number },
                            onToggleFavorite = { toggleFavorite(contact) },
                            onBack = { selectedContact = null },
                            onCall = { CallManager.makeCall(context, it) },
                            onMessage = { 
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$it"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            isBlocked = isBlocked,
                            onToggleBlock = {
                                if (isBlocked) blacklistRepository.unblockNumber(contact.number)
                                else blacklistRepository.blockNumber(contact.number)
                                isBlocked = !isBlocked
                            },
                            notes = notes.filter { it.contactNumber == contact.number || (it.contactNumber != null && contact.number.endsWith(it.contactNumber!!)) },
                            onAddNote = { content ->
                                coroutineScope.launch {
                                    notesRepository.addNoteForContact(contact.number, content)
                                }
                            },
                            allCallLogs = callLogs
                        )
                    }
                    OverlayType.Summary -> SummaryScreen(callLogs, notes, contacts, onBack = { showSummary = false })
                    OverlayType.Settings -> SettingsScreen(
                        shakeEnabled = shakeEnabled, 
                        onShakeToggle = {
                            shakeEnabled = it
                            prefs.edit().putBoolean("shake_enabled", it).apply()
                        },
                        themePreference = themePreference,
                        onThemeChange = onThemeChange
                    )
                    OverlayType.Search -> GlobalSearchDashboard(
                        contacts = contacts,
                        callLogs = callLogs,
                        notes = notes,
                        onContactClick = { 
                            selectedContact = it
                            showSearchDashboard = false
                        },
                        onCallClick = { 
                            CallManager.makeCall(context, it)
                            showSearchDashboard = false
                        },
                        onClose = { showSearchDashboard = false }
                    )
                    OverlayType.None -> {}
                }
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
                    ActiveCallMinimizedBar(call, contacts) { isCallMinimized = false }
                }
            }


        }
    }
}

@Composable
fun ActiveCallMinimizedBar(call: Call, contacts: List<Contact>, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
    val contact = remember(number, contacts) { contacts.findContactFlexible(number) }
    val contactGradient = remember(number, contact, context) { getGradientForContact(number, contact?.photoUri, context) }
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

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surfaceColor = if (isDark) GlassmorphismDark else GlassmorphismLight
    val borderColor = if (isDark) GlassmorphismOutline else Color.Black.copy(alpha = 0.05f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 24.dp), 
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 400.dp) // Force pill shape even on wide screens
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(40.dp))
                .background(surfaceColor)
        ) {
            // Premium Blur Background
            Surface(
                color = Color.Transparent,
                modifier = Modifier.blur(40.dp),
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
                                borderColor,
                                Color.Transparent,
                                borderColor.copy(alpha = borderColor.alpha / 2)
                            )
                        ),
                        shape = RoundedCornerShape(40.dp)
                    )
            )

            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (label, icon, index) ->
                    val isSelected = selectedTab == index
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        label = "tab_bg"
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f) // Ensure equal width for all tabs
                            .clip(RoundedCornerShape(32.dp))
                            .background(backgroundColor)
                            .clickable { 
                                HapticUtils.playClick(context)
                                onTabSelected(index) 
                            }
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

@Composable
fun PipCallUI(call: Call, contacts: List<Contact>) {
    val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
    val contact = remember(number, contacts) { contacts.findContactFlexible(number) }
    val name = contact?.name ?: number
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val contactGradient = remember(number, contact, context) { 
        getGradientForContact(number, contact?.photoUri, context) 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(contactGradient.first()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(
                    onClick = { call.disconnect() },
                    modifier = Modifier.size(36.dp).background(Color.Red.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
