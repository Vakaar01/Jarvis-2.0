package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.JarvisPermissionStatus
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurfaceVariant
import com.example.ui.theme.NeonGreen

@Composable
fun PermissionsHub(
    status: JarvisPermissionStatus,
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0D1726),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (status.isAllGranted) NeonGreen.copy(alpha = 0.4f) else AmberAccent.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (status.isAllGranted) Icons.Default.Security else Icons.Default.Warning,
                        contentDescription = "Security Status",
                        tint = if (status.isAllGranted) NeonGreen else AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERMISSIONS & OS ACCESS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = if (status.isAllGranted) "UNRESTRICTED" else "ACTION NEEDED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (status.isAllGranted) NeonGreen else AmberAccent
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grid of permissions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PermissionBadge(
                    label = "MIC",
                    granted = status.hasRecordAudio,
                    icon = Icons.Default.Mic,
                    modifier = Modifier.weight(1f)
                )
                PermissionBadge(
                    label = "NOTIFS",
                    granted = status.hasPostNotifications,
                    icon = Icons.Default.Notifications,
                    modifier = Modifier.weight(1f)
                )
                PermissionBadge(
                    label = "CAMERA",
                    granted = status.hasCamera,
                    icon = Icons.Default.Camera,
                    modifier = Modifier.weight(1f)
                )
                PermissionBadge(
                    label = "ALWAYS-ON",
                    granted = status.isBatteryOptimizationIgnored,
                    icon = Icons.Default.BatteryAlert,
                    modifier = Modifier.weight(1f)
                )
                PermissionBadge(
                    label = "GESTURES",
                    granted = status.hasAccessibility,
                    icon = Icons.Default.Security,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!status.isAllGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!status.hasRecordAudio || !status.hasPostNotifications || !status.hasCamera) {
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = Color(0xFF041E28)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grant_permissions_button")
                        ) {
                            Text(
                                text = "PERMISSIONS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!status.hasAccessibility) {
                        Button(
                            onClick = onRequestBatteryOptimization, // will wire properly or provide separate callback
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberAccent,
                                contentColor = Color(0xFF041E28)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("accessibility_btn")
                        ) {
                            Text(
                                text = "GESTURE ACCESS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBadge(
    label: String,
    granted: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HudSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (granted) NeonGreen.copy(alpha = 0.5f) else HudBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else icon,
                contentDescription = label,
                tint = if (granted) NeonGreen else Color.Gray,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) NeonGreen else Color(0xFF94A3B8)
            )
        }
    }
}
