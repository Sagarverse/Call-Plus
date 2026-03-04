package com.example.call.ui.screens

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
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
    var filterType by remember { mutableIntStateOf(0) } // 0: All, 1: Month, 2: Today

    val filteredLogs = remember(callLogs, filterType) {
        val now = Calendar.getInstance()
        when (filterType) {
            1 -> { // This Month
                callLogs.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
            }
            2 -> { // Today
                callLogs.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                    cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
            }
            else -> callLogs
        }
    }

    var analytics by remember { mutableStateOf<AnalyticsData?>(null) }
 
    LaunchedEffect(filteredLogs) {
        withContext(Dispatchers.Default) {
            val totalCalls = filteredLogs.size
            val incoming = filteredLogs.count { it.type == "Incoming" }
            val outgoing = filteredLogs.count { it.type == "Outgoing" }
            val missed = filteredLogs.count { it.type == "Missed" }
            val totalDuration = filteredLogs.sumOf { it.duration }
            val topContact = if (filteredLogs.isNotEmpty()) {
                filteredLogs.groupBy { it.number }.maxByOrNull { it.value.size }?.value?.first()?.name ?: "N/A"
            } else "N/A"
            
            analytics = AnalyticsData(
                total = totalCalls,
                inc = incoming,
                out = outgoing,
                mis = missed,
                dur = totalDuration.toLong(),
                top = topContact
            )
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
            Text("Analytics", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = filterType == 0,
                onClick = { filterType = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) { Text("All") }
            SegmentedButton(
                selected = filterType == 1,
                onClick = { filterType = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) { Text("Month") }
            SegmentedButton(
                selected = filterType == 2,
                onClick = { filterType = 2 },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) { Text("Today") }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        analytics?.let { stats ->
            AnalyticsCard("Total Calls", stats.total.toString(), Icons.Default.Call, IOSBlue)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    AnalyticsCard("Incoming", stats.inc.toString(), Icons.Default.ArrowForward, IOSGreen, compact = true)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    AnalyticsCard("Outgoing", stats.out.toString(), Icons.Default.ArrowBack, IOSBlue, compact = true)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            AnalyticsCard("Missed Calls", stats.mis.toString(), Icons.Default.Warning, IOSRed)
            
            Spacer(modifier = Modifier.height(16.dp))
            AnalyticsCard("Total Duration", "${stats.dur / 60} mins", Icons.Default.Settings, IOSGray)
            
            Spacer(modifier = Modifier.height(16.dp))
            AnalyticsCard("Top Caller", stats.top, Icons.Default.Person, Color(0xFFFFCC00))
        } ?: Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IOSBlue)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

data class AnalyticsData(
    val total: Int,
    val inc: Int,
    val out: Int,
    val mis: Int,
    val dur: Long,
    val top: String
)
