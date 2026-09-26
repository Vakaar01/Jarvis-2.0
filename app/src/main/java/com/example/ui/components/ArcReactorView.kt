package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorView(
    isListening: Boolean,
    isSpeaking: Boolean,
    audioRms: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor_animation")

    // Continuous clockwise rotation
    val rotationFast by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 4000 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationFast"
    )

    // Counter-clockwise outer ring rotation
    val rotationSlow by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationSlow"
    )

    // Breathing pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening || isSpeaking) 800 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val activeGlowColor = when {
        isListening -> AmberAccent
        isSpeaking -> CyanGlow
        else -> CyanPrimary
    }

    Box(
        modifier = modifier
            .size(220.dp)
            .testTag("arc_reactor_touch_target")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.85f
            val dynamicRadius = baseRadius * pulseScale + (audioRms * 12f)

            // 1. Ambient Background Core Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeGlowColor.copy(alpha = if (isListening || isSpeaking) 0.45f else 0.22f),
                        activeGlowColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = dynamicRadius * 1.25f
                ),
                radius = dynamicRadius * 1.25f,
                center = center
            )

            // 2. Outer Triangular / Hexagonal Tech Bracket Ring
            rotate(rotationSlow, pivot = center) {
                val segments = 12
                val angleStep = 360f / segments
                for (i in 0 until segments) {
                    val startAngle = i * angleStep
                    drawArc(
                        color = activeGlowColor.copy(alpha = 0.35f),
                        startAngle = startAngle + 4f,
                        sweepAngle = angleStep - 8f,
                        useCenter = false,
                        topLeft = Offset(center.x - dynamicRadius, center.y - dynamicRadius),
                        size = androidx.compose.ui.geometry.Size(dynamicRadius * 2, dynamicRadius * 2),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Middle Circuit Ring (Clockwise)
            val midRadius = dynamicRadius * 0.76f
            rotate(rotationFast, pivot = center) {
                val teeth = 8
                val toothAngle = 360f / teeth
                for (i in 0 until teeth) {
                    val angle = i * toothAngle
                    drawArc(
                        color = activeGlowColor.copy(alpha = 0.8f),
                        startAngle = angle,
                        sweepAngle = toothAngle * 0.5f,
                        useCenter = false,
                        topLeft = Offset(center.x - midRadius, center.y - midRadius),
                        size = androidx.compose.ui.geometry.Size(midRadius * 2, midRadius * 2),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Butt)
                    )
                }
            }

            // 4. Audio Frequency Waveform Rays
            val innerRadius = dynamicRadius * 0.55f
            val rayCount = 16
            for (i in 0 until rayCount) {
                val rad = Math.toRadians((i * (360.0 / rayCount) + rotationFast).toDouble())
                val rayLength = 8.dp.toPx() + (audioRms * 24.dp.toPx())
                val p1 = Offset(
                    center.x + (innerRadius * cos(rad)).toFloat(),
                    center.y + (innerRadius * sin(rad)).toFloat()
                )
                val p2 = Offset(
                    center.x + ((innerRadius + rayLength) * cos(rad)).toFloat(),
                    center.y + ((innerRadius + rayLength) * sin(rad)).toFloat()
                )
                drawLine(
                    color = activeGlowColor,
                    start = p1,
                    end = p2,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Core Reactor Center Circle
            val coreRadius = dynamicRadius * 0.38f
            drawCircle(
                color = Color(0xFF07111E),
                radius = coreRadius,
                center = center
            )
            drawCircle(
                color = activeGlowColor,
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawCircle(
                color = activeGlowColor.copy(alpha = if (isListening || isSpeaking) 0.6f else 0.3f),
                radius = coreRadius * 0.65f,
                center = center
            )
        }

        // Center VAKAAR Holographic Core Indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "VAKAAR",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                color = when {
                    isListening -> AmberAccent
                    isSpeaking -> CyanGlow
                    else -> CyanPrimary
                },
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.size(2.dp))
            if (isListening) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microphone Active",
                    tint = AmberAccent,
                    modifier = Modifier.size(20.dp)
                )
            } else if (isSpeaking) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Vakaar Speaking",
                    tint = CyanGlow,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "MK-LIII",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = CyanPrimary.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
