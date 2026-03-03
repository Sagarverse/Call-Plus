package com.example.call.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.data.CallRecord
import com.example.call.ui.components.AnalyticsCard
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.theme.*

@Composable
fun SummaryScreen(callLogs: List<CallRecord>, onBack: () -> Unit) {
    val analytics = remember(callLogs) {
        val totalCalls = callLogs.size
        val incoming = callLogs.count { it.type == "Incoming" }
        val outgoing = callLogs.count { it.type == "Outgoing" }
        val missed = callLogs.count { it.type == "Missed" }
        val totalDuration = callLogs.sumOf { it.duration }
        val topContact = if (callLogs.isNotEmpty()) {
            callLogs.groupBy { it.number }.maxByOrNull { it.value.size }?.value?.first()?.name ?: "N/A"
        } else "N/A"
        
        object {
            val total = totalCalls
            val inc = incoming
            val out = outgoing
            val mis = missed
            val dur = totalDuration
            val top = topContact
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SagarCallBanner(modifier = Modifier.align(Alignment.End))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text("Call Analytics", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        AnalyticsCard("Total Calls", analytics.total.toString(), Icons.Default.Call, IOSBlue)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                AnalyticsCard("Incoming", analytics.inc.toString(), Icons.Default.ArrowForward, IOSGreen, compact = true)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                AnalyticsCard("Outgoing", analytics.out.toString(), Icons.Default.ArrowBack, IOSBlue, compact = true)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        AnalyticsCard("Missed Calls", analytics.mis.toString(), Icons.Default.Warning, IOSRed)
        
        Spacer(modifier = Modifier.height(16.dp))
        AnalyticsCard("Total Duration", "${analytics.dur / 60} mins", Icons.Default.Settings, IOSGray)
        
        Spacer(modifier = Modifier.height(16.dp))
        AnalyticsCard("Top Caller", analytics.top, Icons.Default.Person, Color(0xFFFFCC00))
        
        Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
