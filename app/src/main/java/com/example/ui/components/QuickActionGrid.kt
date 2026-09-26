package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurfaceVariant

@Composable
fun QuickActionGrid(
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0A101C),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, HudBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MARK LIII // BUNDLED ACTIONS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "FAST DISPATCH",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard(
                    icon = Icons.Default.PlayArrow,
                    label = "Play Song",
                    cmd = "video chalao latest trending song",
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.Language,
                    label = "Quiz Me",
                    cmd = "Mujhe ek general knowledge quiz sawal pucho",
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.FlashlightOn,
                    label = "Torch",
                    cmd = "torch jalao",
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard(
                    icon = Icons.Default.Assessment,
                    label = "Scroll Down",
                    cmd = "niche scroll karo",
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Vol 80%",
                    cmd = "set volume to 80%",
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = Icons.Default.CameraAlt,
                    label = "Diagnostics",
                    cmd = "system diagnostics report do",
                    onClick = onActionClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    label: String,
    cmd: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HudSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HudBorder),
        modifier = modifier
            .testTag("action_card_${label.lowercase()}")
            .clickable { onClick(cmd) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CyanPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
