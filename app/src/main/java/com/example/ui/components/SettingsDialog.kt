package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.NeonGreen
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    currentApiKey: String,
    isAlwaysOnEnabled: Boolean,
    isVoiceOutputEnabled: Boolean,
    currentPitch: Float,
    currentRate: Float,
    onOpenApiKeyPortal: () -> Unit,
    onValidateApiKey: suspend (String) -> Result<String>,
    onSave: (apiKey: String, alwaysOn: Boolean, voiceOutput: Boolean, pitch: Float, rate: Float) -> Unit,
    onDismiss: () -> Unit,
    onTestVoice: (pitch: Float, rate: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isValidatingKey by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showHelpGuide by remember { mutableStateOf(apiKeyText.isBlank()) }

    var alwaysOnState by remember { mutableStateOf(isAlwaysOnEnabled) }
    var voiceOutputState by remember { mutableStateOf(isVoiceOutputEnabled) }
    var pitchState by remember { mutableFloatStateOf(currentPitch) }
    var rateState by remember { mutableFloatStateOf(currentRate) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("settings_dialog_surface"),
            shape = RoundedCornerShape(16.dp),
            color = HudSurface,
            border = BorderStroke(1.dp, CyanPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙️ VAKAAR SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Close Settings",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 1. FREE GEMINI AI API KEY CONFIGURATION CARD
                // ==========================================
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF041926),
                    border = BorderStroke(
                        1.dp,
                        if (apiKeyText.isNotBlank()) NeonGreen.copy(alpha = 0.6f) else AmberAccent.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = if (apiKeyText.isNotBlank()) NeonGreen else AmberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "FREE GEMINI AI API KEY",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Key Status Badge
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (apiKeyText.isNotBlank()) NeonGreen.copy(alpha = 0.15f)
                                        else AmberAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (apiKeyText.isNotBlank()) "ACTIVE" else "FREE KEY NEEDED",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (apiKeyText.isNotBlank()) NeonGreen else AmberAccent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Har user apni free Google Gemini API key daal sakta hai. Google AI Studio par bina kisi fees ya credit card ke free key milti hai.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // API Key Input Field
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = {
                                apiKeyText = it
                                validationResult = null
                            },
                            placeholder = {
                                Text(
                                    "Paste your free API Key (AIzaSy...)",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            },
                            singleLine = true,
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (apiKeyText.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                apiKeyText = ""
                                                validationResult = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear Key",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { isKeyVisible = !isKeyVisible },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isKeyVisible) "Hide Key" else "Show Key",
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = HudBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Buttons: Get Free Key & Test Key
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenApiKeyPortal,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AmberAccent
                                ),
                                border = BorderStroke(1.dp, AmberAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("get_free_key_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = AmberAccent
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "GET FREE KEY",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    if (apiKeyText.isNotBlank() && !isValidatingKey) {
                                        coroutineScope.launch {
                                            isValidatingKey = true
                                            val res = onValidateApiKey(apiKeyText.trim())
                                            isValidatingKey = false
                                            validationResult = res.isSuccess to (res.getOrNull() ?: res.exceptionOrNull()?.message ?: "Validation error")
                                        }
                                    }
                                },
                                enabled = apiKeyText.isNotBlank() && !isValidatingKey,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0F3952),
                                    contentColor = CyanPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_api_key_button")
                            ) {
                                if (isValidatingKey) {
                                    CircularProgressIndicator(
                                        color = CyanPrimary,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isValidatingKey) "TESTING..." else "TEST & VERIFY",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Validation Result Feedback
                        AnimatedVisibility(visible = validationResult != null) {
                            validationResult?.let { (isOk, msg) ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isOk) NeonGreen.copy(alpha = 0.12f) else Color.Red.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (isOk) NeonGreen else Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = msg,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = if (isOk) NeonGreen else Color(0xFFFF8A80)
                                    )
                                }
                            }
                        }

                        // Step-by-Step Guide Toggle
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHelpGuide = !showHelpGuide }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showHelpGuide) "Hide Free Key Guide ▲" else "How to get 100% Free Key? (4 steps) ▼",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = CyanPrimary
                            )
                        }

                        AnimatedVisibility(visible = showHelpGuide) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF02131D), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "1. 'GET FREE KEY' par tap karein\n2. Apne Google account se sign in karein\n3. 'Create API key' button click karke copy karein\n4. Yahan paste karein aur 'TEST & VERIFY' dabayein!",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 2. ALWAYS-ON BACKGROUND SERVICE TOGGLE
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ALWAYS-ON MONITORING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Runs in background to maintain continuous standby & battery diagnostics.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = alwaysOnState,
                        onCheckedChange = { alwaysOnState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonGreen
                        ),
                        modifier = Modifier.testTag("always_on_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 3. VOICE SPEECH OUTPUT & PITCH CONTROLS
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VAKAAR VOICE OUTPUT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Speaks responses aloud in English & Hindi speech synthesis.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = voiceOutputState,
                        onCheckedChange = { voiceOutputState = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanPrimary
                        ),
                        modifier = Modifier.testTag("voice_output_switch")
                    )
                }

                if (voiceOutputState) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "VOICE PITCH: ${Math.round(pitchState * 100) / 100f}x",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CyanPrimary
                    )
                    Slider(
                        value = pitchState,
                        onValueChange = { pitchState = it },
                        valueRange = 0.6f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanPrimary,
                            activeTrackColor = CyanPrimary
                        )
                    )

                    Text(
                        text = "SPEECH RATE: ${Math.round(rateState * 100) / 100f}x",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CyanPrimary
                    )
                    Slider(
                        value = rateState,
                        onValueChange = { rateState = it },
                        valueRange = 0.7f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyanPrimary,
                            activeTrackColor = CyanPrimary
                        )
                    )

                    Button(
                        onClick = { onTestVoice(pitchState, rateState) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF162A45)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TEST VOICE RESPONSE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CyanPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CANCEL",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(apiKeyText.trim(), alwaysOnState, voiceOutputState, pitchState, rateState)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color(0xFF041E28)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text(
                            text = "SAVE & SYNC",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
