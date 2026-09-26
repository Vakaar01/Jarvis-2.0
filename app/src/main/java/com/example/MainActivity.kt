package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.live.LiveSessionState
import com.example.ui.JarvisViewModel
import com.example.ui.components.ArcReactorView
import com.example.ui.components.CommandTerminal
import com.example.ui.components.PermissionsHub
import com.example.ui.components.QuickActionGrid
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TelemetryBar
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.HudBackground
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonGreen

class MainActivity : ComponentActivity() {

    private val viewModel: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                JarvisApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }
}

@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    val telemetry by viewModel.telemetry.collectAsState()
    val permissions by viewModel.permissionStatus.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val audioRms by viewModel.audioRms.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val currentStep by viewModel.currentExecutionStep.collectAsState()

    val liveState by viewModel.liveState.collectAsState()
    val sessionTimerFormatted by viewModel.sessionTimerFormatted.collectAsState()
    val inactivitySilenceSeconds by viewModel.inactivitySilenceSeconds.collectAsState()
    val liveAudioRms by viewModel.liveAudioRms.collectAsState()
    val isLiveAiSpeaking by viewModel.isLiveAiSpeaking.collectAsState()

    val effectiveRms = if (liveState == LiveSessionState.LIVE_ACTIVE) liveAudioRms else audioRms
    val effectiveSpeaking = if (liveState == LiveSessionState.LIVE_ACTIVE) isLiveAiSpeaking else isSpeaking
    val effectiveListening = (liveState == LiveSessionState.LIVE_ACTIVE) || isListening

    val customApiKey by viewModel.customApiKey.collectAsState()
    val hasApiKey by viewModel.hasConfiguredApiKey.collectAsState()
    val alwaysOnEnabled by viewModel.alwaysOnEnabled.collectAsState()
    val voiceOutputEnabled by viewModel.voiceOutputEnabled.collectAsState()
    val speechPitch by viewModel.speechPitch.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()

    // Multi-permission request launcher
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissions()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBackground),
        containerColor = HudBackground,
        topBar = {
            JarvisTopBar(
                isServiceRunning = isServiceRunning,
                hasApiKey = hasApiKey,
                liveState = liveState,
                onSettingsClick = { viewModel.openSettings() }
            )
        },
        bottomBar = {
            JarvisVoiceCommandDock(
                liveState = liveState,
                sessionTimerFormatted = sessionTimerFormatted,
                inactivitySilenceSeconds = inactivitySilenceSeconds,
                isListening = effectiveListening,
                isSpeaking = effectiveSpeaking,
                isProcessing = isProcessing,
                currentStep = currentStep,
                audioRms = effectiveRms,
                onToggleListening = {
                    if (!permissions.hasRecordAudio) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        viewModel.toggleListening()
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Permission status & elevation hub
            AnimatedVisibility(visible = !permissions.isAllGranted) {
                PermissionsHub(
                    status = permissions,
                    onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) },
                    onRequestBatteryOptimization = { viewModel.openAccessibilitySettings() }
                )
            }

            // 1.5 Free API Key Reminder Banner (If not configured)
            AnimatedVisibility(visible = !hasApiKey) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.openSettings() }
                        .testTag("free_api_key_banner"),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF041A28),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(AmberAccent.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = AmberAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ENTER FREE GEMINI API KEY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberAccent
                            )
                            Text(
                                text = "Gemini Live Bi-directional WebSocket ke liye key set karein (Tap here)",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { viewModel.openSettings() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberAccent,
                                contentColor = Color(0xFF041E28)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "ADD KEY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Central Holographic Arc Reactor
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ArcReactorView(
                        isListening = effectiveListening,
                        isSpeaking = effectiveSpeaking,
                        audioRms = effectiveRms,
                        onClick = {
                            if (!permissions.hasRecordAudio) {
                                permissionLauncher.launch(permissionsToRequest)
                            } else {
                                viewModel.toggleListening()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            currentStep != null -> "● [STEP]: $currentStep"
                            liveState == LiveSessionState.LIVE_ACTIVE && effectiveSpeaking -> "● GEMINI LIVE VOICE RESPONSE // PLAYING (24kHz)"
                            liveState == LiveSessionState.LIVE_ACTIVE -> "● FULL DUPLEX LIVE // [$sessionTimerFormatted / 10:00] // AEC & BARGE-IN ACTIVE"
                            liveState == LiveSessionState.CONNECTING -> "● CONNECTING TO GEMINI MULTIMODAL LIVE WEBSOCKET..."
                            liveState == LiveSessionState.DISCONNECTING -> "● CLOSING LIVE STREAM // ENTERING WAKE-WORD STANDBY"
                            effectiveListening -> "● NON-STOP MIC ACTIVE // LISTENING..."
                            else -> "● WAKE-WORD IDLE // SAY 'HELLO' (OR TAP REACTOR TO START LIVE)"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            currentStep != null -> NeonGreen
                            liveState == LiveSessionState.LIVE_ACTIVE && effectiveSpeaking -> CyanGlow
                            liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen
                            liveState == LiveSessionState.CONNECTING -> AmberAccent
                            effectiveListening -> AmberAccent
                            else -> CyanPrimary.copy(alpha = 0.8f)
                        },
                        letterSpacing = 1.sp
                    )
                }
            }

            // 3. Real-Time Telemetry status
            TelemetryBar(
                telemetry = telemetry,
                isServiceRunning = isServiceRunning,
                onFlashlightToggle = {
                    if (!permissions.hasCamera) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    } else {
                        viewModel.toggleFlashlight()
                    }
                }
            )

            // 4. Mark LIII Fast Action Dispatch
            QuickActionGrid(
                onActionClick = { cmd ->
                    viewModel.sendUserMessage(cmd)
                }
            )

            // 5. Communication Terminal & Log
            CommandTerminal(
                messages = messages,
                modifier = Modifier.height(260.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (isSettingsOpen) {
        SettingsDialog(
            currentApiKey = customApiKey,
            isAlwaysOnEnabled = alwaysOnEnabled,
            isVoiceOutputEnabled = voiceOutputEnabled,
            currentPitch = speechPitch,
            currentRate = speechRate,
            onOpenApiKeyPortal = { viewModel.openFreeApiKeyPortal() },
            onValidateApiKey = { key -> viewModel.validateApiKey(key) },
            onSave = { key, alwaysOn, voice, pitch, rate ->
                viewModel.saveSettings(key, alwaysOn, voice, pitch, rate)
            },
            onDismiss = { viewModel.closeSettings() },
            onTestVoice = { pitch, rate ->
                viewModel.testVoice(pitch, rate)
            }
        )
    }
}

@Composable
fun JarvisTopBar(
    isServiceRunning: Boolean,
    hasApiKey: Boolean,
    liveState: LiveSessionState,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = HudSurface,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, HudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (liveState) {
                                LiveSessionState.LIVE_ACTIVE -> NeonGreen
                                LiveSessionState.CONNECTING -> AmberAccent
                                else -> if (isServiceRunning) NeonGreen else CyanPrimary
                            }
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "VAKAAR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = when (liveState) {
                            LiveSessionState.LIVE_ACTIVE -> "GEMINI MULTIMODAL LIVE // DUPLEX ACTIVE"
                            LiveSessionState.CONNECTING -> "GEMINI LIVE // CONNECTING..."
                            else -> "MARK LIII // WAKE-WORD ('HELLO') STANDBY"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = if (liveState == LiveSessionState.LIVE_ACTIVE) NeonGreen else Color(0xFF94A3B8)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Live Stream indicator pill
                if (liveState == LiveSessionState.LIVE_ACTIVE) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF063327),
                        border = BorderStroke(1.dp, NeonGreen),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(NeonGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }
                    }
                }

                // AI Status Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasApiKey) Color(0xFF063327) else Color(0xFF332006),
                    border = BorderStroke(1.dp, if (hasApiKey) NeonGreen.copy(alpha = 0.5f) else AmberAccent.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .clickable { onSettingsClick() }
                        .padding(end = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasApiKey) Icons.Default.AutoAwesome else Icons.Default.Key,
                            contentDescription = null,
                            tint = if (hasApiKey) NeonGreen else AmberAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasApiKey) "AI READY" else "FREE KEY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasApiKey) NeonGreen else AmberAccent
                        )
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("topbar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Protocol Settings",
                        tint = CyanPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun JarvisVoiceCommandDock(
    liveState: LiveSessionState,
    sessionTimerFormatted: String,
    inactivitySilenceSeconds: Int,
    isListening: Boolean,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    currentStep: String?,
    audioRms: Float,
    onToggleListening: () -> Unit
) {
    Surface(
        color = Color(0xFF040A14),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Audio Frequency Waveform + Arc Mic Core
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left dynamic soundwave bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val bars = listOf(0.4f, 0.7f, 1.0f, 0.8f, 0.5f)
                    bars.forEachIndexed { _, factor ->
                        val barHeight = if (isListening || isSpeaking) {
                            (10 + (audioRms * 28 * factor)).coerceIn(8f, 36f)
                        } else 6f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        isSpeaking -> CyanGlow
                                        liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen
                                        isListening -> AmberAccent
                                        else -> Color(0xFF1E3A5F)
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Center Mic Activation Orb (Iron Man Mark LIII Voice Hub)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSpeaking -> CyanGlow.copy(alpha = 0.25f)
                                liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen.copy(alpha = 0.25f)
                                isListening -> AmberAccent.copy(alpha = 0.25f)
                                else -> Color(0xFF102035)
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = when {
                                isSpeaking -> CyanGlow
                                liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen
                                isListening -> AmberAccent
                                else -> CyanPrimary.copy(alpha = 0.5f)
                            },
                            shape = CircleShape
                        )
                        .clickable(onClick = onToggleListening)
                        .testTag("voice_listen_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing || liveState == LiveSessionState.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = if (liveState == LiveSessionState.CONNECTING) AmberAccent else NeonGreen
                        )
                    } else {
                        Icon(
                            imageVector = if (liveState == LiveSessionState.LIVE_ACTIVE || isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Voice Input Toggle",
                            tint = when {
                                isSpeaking -> CyanGlow
                                liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen
                                isListening -> AmberAccent
                                else -> Color(0xFF64748B)
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right dynamic soundwave bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val bars = listOf(0.5f, 0.8f, 1.0f, 0.7f, 0.4f)
                    bars.forEachIndexed { _, factor ->
                        val barHeight = if (isListening || isSpeaking) {
                            (10 + (audioRms * 28 * factor)).coerceIn(8f, 36f)
                        } else 6f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        isSpeaking -> CyanGlow
                                        liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen
                                        isListening -> AmberAccent
                                        else -> Color(0xFF1E3A5F)
                                    }
                                )
                        )
                    }
                }
            }

            // 1.5 Live Full Duplex Telemetry Badges (Session timer + Silence fallback + AEC)
            if (liveState == LiveSessionState.LIVE_ACTIVE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱️ SESSION: $sessionTimerFormatted / 10:00",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Text(
                        text = "AEC: ON  |  BARGE-IN: READY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CyanGlow
                    )
                    Text(
                        text = "SILENCE: ${120 - inactivitySilenceSeconds}s",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (inactivitySilenceSeconds > 90) AmberAccent else Color(0xFF94A3B8)
                    )
                }
            }

            // 2. Real-time Status Banner
            Surface(
                color = Color(0xFF071220),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, HudBorder.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            currentStep != null -> "● [PROGRESS]: $currentStep"
                            isSpeaking -> "● TRANSMITTING AUDIO REPLY (GEMINI LIVE)..."
                            isProcessing -> "● NEURAL REASONING..."
                            liveState == LiveSessionState.LIVE_ACTIVE -> "● LIVE DUPLEX ACTIVE // CONTINUOUS 16kHz PCM // BOLTE RAHIYE"
                            liveState == LiveSessionState.CONNECTING -> "● ESTABLISHING BI-DIRECTIONAL GEMINI WEBSOCKET..."
                            liveState == LiveSessionState.DISCONNECTING -> "● CLOSING STREAM // ENTERING WAKE-WORD STANDBY"
                            isListening -> "● DIRECT VOICE ACTIVE // BOLTE RAHIYE SIR VAKAAR"
                            else -> "● WAKE-WORD STANDBY // SAY 'HELLO' (OR TAP CORE TO START)"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            currentStep != null -> NeonGreen
                            isSpeaking -> CyanGlow
                            isProcessing -> NeonGreen
                            liveState == LiveSessionState.LIVE_ACTIVE -> NeonGreen
                            liveState == LiveSessionState.CONNECTING -> AmberAccent
                            isListening -> AmberAccent
                            else -> Color(0xFF94A3B8)
                        },
                        maxLines = 1,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
