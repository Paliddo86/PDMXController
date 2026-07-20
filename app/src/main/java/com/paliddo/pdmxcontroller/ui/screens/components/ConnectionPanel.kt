package com.paliddo.pdmxcontroller.ui.screens.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paliddo.pdmxcontroller.network.ConnectionState
import com.paliddo.pdmxcontroller.ui.viewmodel.MainViewModel

/**
 * Pannello UI per il controllo esplicito della connessione con il controller ESP32.
 * Mostra: stato attuale, indicatori visivi, pulsanti Connetti/Disconnetti,
 * log diagnostico in tempo reale e scansione automatica.
 */
@Composable
fun ConnectionPanel(
    connectionState: ConnectionState,
    connectionLog: List<String>,
    networkIp: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit,
    onSetIp: (String) -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorBackground = Color(0xFF0B0E14)
    val colorSurface = Color(0xFF161A23)
    val colorSurfaceAccent = Color(0xFF232834)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorPurple = Color(0xFF9D4EDD)
    val colorCyan = Color(0xFF00B4D8)
    val colorGreen = Color(0xFF10B981)
    val colorRed = Color(0xFFEF4444)
    val colorOrange = Color(0xFFF59E0B)
    val colorYellow = Color(0xFFFBBF24)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "CONTROLLO CONNESSIONE",
            color = colorCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        // --- INDICATORE STATO PRINCIPALE ---
        StateIndicator(
            state = connectionState,
            colorGreen = colorGreen,
            colorRed = colorRed,
            colorOrange = colorOrange,
            colorYellow = colorYellow
        )

        HorizontalDivider(color = colorSurfaceAccent, thickness = 1.dp)

        // --- CONTROLLI CONNESSIONE ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Bottone Connetti/Disconnetti
            when {
                connectionState.isActive -> {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colorRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DISCONNETTI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                connectionState.isBusy -> {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colorSurfaceAccent),
                        shape = RoundedCornerShape(8.dp),
                        enabled = false
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colorCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONNESSIONE...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                else -> {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = colorGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONNETTI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Bottone Scan
            Button(
                onClick = onScan,
                modifier = Modifier.weight(0.6f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState is ConnectionState.Scanning) colorOrange else colorSurfaceAccent
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !connectionState.isBusy
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SCAN", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        // --- INPUT IP MANUALE ---
        var ipInput by remember(connectionState, networkIp) { mutableStateOf(networkIp) }

        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            label = { Text("IP Controller") },
            placeholder = { Text("192.168.4.1", color = colorTextPrimary.copy(0.3f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorTextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = colorSurfaceAccent,
                focusedBorderColor = colorCyan,
                unfocusedLabelColor = colorTextPrimary.copy(0.5f),
                focusedLabelColor = colorCyan,
                cursorColor = colorCyan
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSetIp(ipInput) }
            ),
            trailingIcon = {
                IconButton(onClick = {
                    onSetIp(ipInput)
                    onConnect()
                }) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Connetti a IP",
                        tint = if (ipInput.isNotBlank()) colorCyan else colorTextPrimary.copy(0.3f)
                    )
                }
            }
        )

        // --- INFO DISPOSITIVO (se connesso) ---
        if (connectionState is ConnectionState.Connected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorSurfaceAccent),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DISPOSITIVO", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    InfoRow("IP", connectionState.targetIp)
                    InfoRow("Nome", connectionState.deviceName)
                    if (connectionState.firmwareVersion.isNotBlank()) {
                        InfoRow("Firmware", connectionState.firmwareVersion)
                    }
                    if (connectionState.developer.isNotBlank()) {
                        InfoRow("Sviluppato da", connectionState.developer)
                    }
                }
            }
        }

        HorizontalDivider(color = colorSurfaceAccent, thickness = 1.dp)

        // --- LOG DIAGNOSTICA ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LOG DIAGNOSTICA",
                color = colorOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            if (connectionLog.isNotEmpty()) {
                TextButton(
                    onClick = onClearLog,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Cancella log",
                        modifier = Modifier.size(14.dp),
                        tint = colorTextPrimary.copy(0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CANCELLA", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp)
                }
            }
        }

        // Console log
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 300.dp),
            shape = RoundedCornerShape(6.dp)
        ) {
            if (connectionLog.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessun evento di connessione.\nPremi CONNETTI o SCAN per iniziare.",
                        color = colorTextPrimary.copy(0.3f),
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(connectionLog) { index, entry ->
                        Text(
                            text = entry,
                            color = when {
                                entry.contains("✅") -> colorGreen
                                entry.contains("❌") || entry.contains("⚠️") -> colorRed
                                entry.contains("🔄") -> colorYellow
                                entry.contains("🔍") -> colorCyan
                                entry.contains("🚀") || entry.contains("⏹️") -> colorOrange
                                else -> colorTextPrimary.copy(0.8f)
                            },
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateIndicator(
    state: ConnectionState,
    colorGreen: Color,
    colorRed: Color,
    colorOrange: Color,
    colorYellow: Color
) {
    val (statusColor, statusText, statusIcon) = when (state) {
        is ConnectionState.Idle -> Triple(colorRed, "INATTIVO", Icons.Default.Cancel)
        is ConnectionState.Scanning -> Triple(colorOrange, "SCANSIONE IN CORSO...", Icons.Default.Search)
        is ConnectionState.Connecting -> Triple(colorYellow, "CONNESSIONE A ${state.targetIp}...", Icons.Default.NetworkCheck)
        is ConnectionState.Handshaking -> Triple(colorYellow, "HANDSHAKE CON ${state.targetIp}...", Icons.Default.Sync)
        is ConnectionState.Connected -> Triple(colorGreen, "CONNESSO A ${state.targetIp}", Icons.Default.CheckCircle)
        is ConnectionState.Disconnected -> Triple(colorRed, "DISCONNESSO", Icons.Default.Cancel)
        is ConnectionState.Error -> Triple(colorRed, "ERRORE: ${state.message}", Icons.Default.Error)
        is ConnectionState.DiscoveryFailed -> Triple(colorOrange, "NESSUN CONTROLLER TROVATO", Icons.Default.VisibilityOff)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pulsante LED - usa semplicemente il colore, nessuna animazione
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Barra di progresso per stati di connessione
        if (state is ConnectionState.Connecting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = colorYellow,
                trackColor = colorYellow.copy(0.2f),
                progress = {
                    state.attempt.toFloat() / state.maxAttempts.toFloat()
                }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colorCyan = Color(0xFF00B4D8)
    val colorTextPrimary = Color(0xFFF8FAFC)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colorTextPrimary.copy(0.5f), fontSize = 12.sp)
        Text(value, color = colorTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}