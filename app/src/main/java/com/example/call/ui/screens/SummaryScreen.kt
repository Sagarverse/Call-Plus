package com.example.call.ui.screens

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Brush
import com.example.call.ui.components.AnalyticsCard
import com.example.call.ui.components.SagarCallBanner
import com.example.call.ui.theme.*

@Composable
fun SummaryScreen(callLogs: List<CallRecord>, onBack: () -> Unit) {
    var filterType by remember { mutableIntStateOf(0) }

    val filteredLogs = remember(callLogs, filterType) {
        val now = Calendar.getInstance()
        when (filterType) {
            1 -> callLogs.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            }
            2 -> callLogs.filter {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
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
            
            val hourlyDist = filteredLogs.groupBy { 
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.HOUR_OF_DAY)
            }.mapValues { it.value.size }
            
            val peakHour = hourlyDist.maxByOrNull { it.value }?.key ?: -1
            
            analytics = AnalyticsData(
                total = totalCalls,
                inc = incoming,
                out = outgoing,
                mis = missed,
                dur = totalDuration.toLong(),
                top = topContact,
                peakHour = peakHour,
                hourlyDistribution = hourlyDist
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(60.dp))
            SagarCallBanner(modifier = Modifier.align(Alignment.End), color = MaterialTheme.colorScheme.onSurface)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VisionPrimary)
                }
                Text("Intelligence", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("All", "Month", "Today").forEachIndexed { index, label ->
                    val isSelected = filterType == index
                    com.example.call.ui.components.GlassmorphicContainer(
                        modifier = Modifier.height(44.dp).weight(1f).clickable { filterType = index },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = if (isSelected) VisionPrimary.copy(alpha = 0.2f) else Color.Transparent,
                        borderAlpha = if (isSelected) 0.4f else 0.1f
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(label, color = if (isSelected) VisionPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            analytics?.let { stats ->
                // Vision Chart Module
                com.example.call.ui.components.GlassmorphicContainer(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = VisionPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CALL DURATION TRENDS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        DurationTrendChart(filteredLogs)
                    }
                }

                // Grid stats
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnalyticsModule("TOTAL", stats.total.toString(), Icons.Default.Call, VisionPrimary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        AnalyticsModule("TOP", stats.top, Icons.Default.Person, Color(0xFFFFCC00))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1.2f)) {
                        Column {
                            AnalyticsModule("INCOMING", stats.inc.toString(), Icons.Default.ArrowDownward, IOSGreen, compact = true)
                            Spacer(modifier = Modifier.height(16.dp))
                            AnalyticsModule("OUTGOING", stats.out.toString(), Icons.Default.ArrowUpward, VisionPrimary, compact = true)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        com.example.call.ui.components.GlassmorphicContainer {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("SUCCESS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                                val successRate = if (stats.total > 0) (stats.total - stats.mis).toFloat() / stats.total else 1f
                                com.example.call.ui.components.VisionGauge(successRate)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnalyticsModule("MISSED", stats.mis.toString(), Icons.Default.Warning, MaterialTheme.colorScheme.error, compact = true)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val peakLabel = if (stats.peakHour != -1) "${stats.peakHour}:00" else "N/A"
                        AnalyticsModule("PEAK", peakLabel, Icons.Default.Schedule, Color(0xFFFF9500), compact = true)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                AnalyticsModule("TOTAL DURATION", "${stats.dur / 60}m ${stats.dur % 60}s", Icons.Default.History, MaterialTheme.colorScheme.primary)
                
            } ?: run {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(120.dp)) // Bottom padding for Vision Pill
        }
    }
}

@Composable
fun AnalyticsModule(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, compact: Boolean = false) {
    com.example.call.ui.components.GlassmorphicContainer {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
            Text(value, fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DurationTrendChart(logs: List<CallRecord>) {
    val dayLabels = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL") // Example mapping
    val now = Calendar.getInstance()
    
    val dailyDurations = (0..6).map { dayOffset ->
        val target = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -dayOffset) }
        val duration = logs.filter {
            val logCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            logCal.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) &&
            logCal.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        }.sumOf { it.duration }
        duration
    }.reversed()

    val maxDur = dailyDurations.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dailyDurations.forEachIndexed { index, dur ->
            val heightFactor = dur.toFloat() / maxDur.toFloat()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (heightFactor > 0.05f) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(heightFactor)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dayLabels[index], 
                    fontSize = 10.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class AnalyticsData(
    val total: Int,
    val inc: Int,
    val out: Int,
    val mis: Int,
    val dur: Long,
    val top: String,
    val peakHour: Int,
    val hourlyDistribution: Map<Int, Int>
)
