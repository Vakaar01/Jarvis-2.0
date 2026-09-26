package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceTelemetry
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurfaceVariant
import com.example.ui.theme.NeonGreen

@Composable
fun TelemetryBar(
    telemetry: DeviceTelemetry,
    isServiceRunning: Boolean,
    onFlashlightToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0C1423),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header status line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isServiceRunning) NeonGreen else AmberAccent,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "SYSTEM: ALWAYS-ON PERSISTENT" else "SYSTEM: STANDBY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) NeonGreen else AmberAccent,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "MARK LIII // OS LINKED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CyanPrimary.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryCard(
                    icon = Icons.Default.Bolt,
                    label = "POWER",
                    value = "${telemetry.batteryPercent}%" + if (telemetry.isCharging) " ⚡" else "",
                    valueColor = if (telemetry.batteryPercent < 20) AmberAccent else CyanPrimary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    icon = Icons.Default.Memory,
                    label = "RAM FREE",
                    value = "${telemetry.freeRamMb}MB",
                    valueColor = CyanPrimary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryCard(
                    icon = Icons.Default.Wifi,
                    label = "NET LINK",
                    value = if (telemetry.networkStatus.contains("WiFi")) "WIFI" else "CELL",
                    valueColor = NeonGreen,
                    modifier = Modifier.weight(1f)
                )

                // Flashlight interactive chip
                Surface(
                    color = if (telemetry.isFlashlightOn) CyanPrimary.copy(alpha = 0.2f) else HudSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (telemetry.isFlashlightOn) CyanPrimary else HudBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onFlashlightToggle)
                        .testTag("telemetry_flashlight_chip")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashlightOn,
                            contentDescription = "Flashlight Toggle",
                            tint = if (telemetry.isFlashlightOn) CyanPrimary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (telemetry.isFlashlightOn) "TORCH ON" else "TORCH OFF",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (telemetry.isFlashlightOn) CyanPrimary else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HudSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HudBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = valueColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}
