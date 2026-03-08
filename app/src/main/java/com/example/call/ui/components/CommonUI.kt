package com.example.call.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
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
fun GlassmorphicContainer(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(32.dp),
    containerColor: Color? = null,
    borderAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val surfaceColor = containerColor ?: if (isDark) GlassmorphismDark else GlassmorphismLight
    val borderColor = if (isDark) GlassmorphismOutline else Color.Black.copy(alpha = 0.05f)
    val finalBorderAlpha = borderAlpha ?: borderColor.alpha

    Box(modifier = modifier.clip(shape)) {
        // High Blur Layer
        Surface(
            color = surfaceColor,
            modifier = Modifier
                .matchParentSize()
                .blur(40.dp),
        ) {}
        
        // Edge Highlight / Border
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            borderColor.copy(alpha = finalBorderAlpha),
                            Color.Transparent,
                            borderColor.copy(alpha = finalBorderAlpha / 2)
                        )
                    )
                )
        )
        
        // Internal Content Padding and rendering
        Box(modifier = Modifier.padding(1.dp)) {
            content()
        }
    }
}

@Composable
fun VisionToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .height(32.dp)
            .clip(CircleShape)
            .background(if (checked) VisionPrimary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun VisionGauge(value: Float, color: Color = VisionPrimary) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
        CircularProgressIndicator(
            progress = value,
            modifier = Modifier.size(80.dp),
            color = color,
            strokeWidth = 8.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${(value * 100).toInt()}%",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
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
    GlassmorphicContainer {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
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
    modifier: Modifier = Modifier,
    onRightSwipe: () -> Unit,
    onLeftSwipe: () -> Unit,
    onDeleteSwipe: (() -> Unit)? = null, // Optional delete action
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxOffset = 300f
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                when {
                    offsetX > 50 -> IOSGreen
                    offsetX < -150 && onDeleteSwipe != null -> IOSRed
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
                    else if (offsetX < -200 && onDeleteSwipe != null) onDeleteSwipe()
                    else if (offsetX < -100) onLeftSwipe()
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
            } else if (offsetX < -150 && onDeleteSwipe != null) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

@Composable
fun SwipeToAnswer(
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxOffset = 260f
    val threshold = 200f
    
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Box(
        modifier = modifier
            .width(300.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.1f),
                        IOSGreen.copy(alpha = glowAlpha),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(40.dp)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track text
        Text(
            "Slide to answer",
            modifier = Modifier.fillMaxWidth().offset(x = 40.dp),
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        
        // Sliding Pill
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(6.dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(IOSGreen, Color(0xFF2ECC71))
                    )
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(0f, maxOffset)
                    },
                    onDragStopped = {
                        if (offsetX >= threshold) {
                            onAnswer()
                        }
                        offsetX = 0f
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

