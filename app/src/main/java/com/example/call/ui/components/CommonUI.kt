package com.example.call.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun adaptiveTextColor(backgroundColor: Color): Color {
    return if (backgroundColor.red + backgroundColor.green + backgroundColor.blue < 1.5f) {
        Color.White
    } else {
        Color.Black
    }
}

@Composable
fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 18.sp, color = IOSGray)
    }
}

@Composable
fun DetailTextItem(text: String, color: Color = IOSBlue, onClick: () -> Unit = {}) {
    Text(
        text,
        color = color,
        fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    )
}

@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    textColor: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = if (isActive) Color.White else Color(0xFF3A3A3C)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            label,
            color = textColor,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun AnalyticsCard(label: String, value: String, icon: ImageVector, color: Color, compact: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(label, fontSize = if (compact) 12.sp else 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun SwipeableActionItem(
    onRightSwipe: () -> Unit,
    onLeftSwipe: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxOffset = 300f
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    offsetX > 50 -> IOSGreen
                    offsetX < -50 -> IOSBlue
                    else -> Color.Transparent
                }
            )
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    offsetX = (offsetX + delta).coerceIn(-maxOffset, maxOffset)
                },
                onDragStopped = {
                    if (offsetX > 200) onRightSwipe()
                    else if (offsetX < -200) onLeftSwipe()
                    offsetX = 0f
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = if (offsetX > 0) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (offsetX > 80) {
                Text("Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            } else if (offsetX < -80) {
                Text("Message", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}

@Composable
fun SagarCallBanner(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = "Sagar call",
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontFamily = androidx.compose.ui.text.font.FontFamily.Cursive,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = color.copy(alpha = 0.7f)
    )
}
