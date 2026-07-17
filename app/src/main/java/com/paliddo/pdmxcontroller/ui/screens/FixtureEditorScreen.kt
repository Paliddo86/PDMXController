package com.paliddo.pdmxcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paliddo.pdmxcontroller.data.model.*
import com.paliddo.pdmxcontroller.ui.viewmodel.MainViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixtureEditorScreen(
    viewModel: MainViewModel,
    initialProfile: FixtureProfile? = null,
    onBack: () -> Unit
) {
    var manufacturer by remember { mutableStateOf(initialProfile?.manufacturer ?: "") }
    var modelName by remember { mutableStateOf(initialProfile?.modelName ?: "") }
    val channels = remember { 
        val list = mutableStateListOf<ChannelDefinition>()
        initialProfile?.channels?.let { list.addAll(it) }
        list
    }

    var channelToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var offsetError by remember { mutableStateOf<String?>(null) }

    val colorBackground = Color(0xFF0B0E14)
    val colorSurface = Color(0xFF161A23)
    val colorSurfaceAccent = Color(0xFF232834)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorPurple = Color(0xFF9D4EDD)
    val colorCyan = Color(0xFF00B4D8)
    val colorDisconnected = Color(0xFFEF4444)

    // Validazione canali (offset duplicati)
    LaunchedEffect(channels.toList()) {
        val offsets = channels.map { it.offset }
        offsetError = if (offsets.size != offsets.toSet().size) {
            "Attenzione: ci sono canali con lo stesso indirizzo offset!"
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBackground)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = colorTextPrimary)
            }
            Text(
                text = if (initialProfile != null) "MODIFICA PROFILO" else "NUOVO PROFILO FIXTURE",
                color = colorCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    if (manufacturer.isNotEmpty() && modelName.isNotEmpty() && channels.isNotEmpty() && offsetError == null) {
                        val profile = FixtureProfile(
                            id = initialProfile?.id ?: UUID.randomUUID().toString(),
                            manufacturer = manufacturer,
                            modelName = modelName,
                            channelCount = (channels.maxOfOrNull { it.offset } ?: 0) + 1,
                            channels = channels.toList().sortedBy { it.offset }
                        )
                        viewModel.addUserFixtureProfile(profile)
                        onBack()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colorPurple),
                enabled = manufacturer.isNotEmpty() && modelName.isNotEmpty() && channels.isNotEmpty() && offsetError == null
            ) {
                Text("SALVA NELLA LIBRERIA", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (offsetError != null) {
            Surface(
                color = colorDisconnected.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorDisconnected),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text(
                    text = offsetError!!,
                    color = colorDisconnected,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Info Generali
        Card(colors = CardDefaults.cardColors(containerColor = colorSurface), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Produttore") },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = colorTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorCyan)
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Modello") },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = colorTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Header Canali
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CONFIGURAZIONE CANALI (${channels.size})",
                color = colorPurple,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    val nextOffset = if (channels.isEmpty()) 0 else (channels.maxOf { it.offset } + 1)
                    channels.add(
                        ChannelDefinition(
                            offset = nextOffset,
                            name = "Parametro ${nextOffset + 1}",
                            type = ChannelType.OTHER
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = colorSurfaceAccent),
                modifier = Modifier.border(1.dp, colorCyan.copy(0.3f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("AGGIUNGI CANALE", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(channels) { index, channel ->
                ChannelEditorItem(
                    index = index,
                    channel = channel,
                    onUpdate = { updated -> channels[index] = updated },
                    onDelete = { channelToDeleteIndex = index },
                    colorSurface = colorSurface,
                    colorSurfaceAccent = colorSurfaceAccent,
                    colorTextPrimary = colorTextPrimary,
                    colorCyan = colorCyan,
                    colorDisconnected = colorDisconnected,
                    colorPurple = colorPurple
                )
            }
        }
    }

    // Dialog Conferma Cancellazione
    if (channelToDeleteIndex != null) {
        AlertDialog(
            onDismissRequest = { channelToDeleteIndex = null },
            containerColor = colorSurface,
            title = { Text("ELIMINA CANALE", color = colorDisconnected) },
            text = { Text("Vuoi davvero rimuovere il canale '${channels[channelToDeleteIndex!!].name}' (Offset ${channels[channelToDeleteIndex!!].offset + 1})?", color = colorTextPrimary) },
            confirmButton = {
                Button(
                    onClick = { 
                        channels.removeAt(channelToDeleteIndex!!)
                        channelToDeleteIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorDisconnected)
                ) {
                    Text("ELIMINA", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToDeleteIndex = null }) {
                    Text("ANNULLA", color = colorTextPrimary)
                }
            }
        )
    }
}

@Composable
fun ChannelEditorItem(
    index: Int,
    channel: ChannelDefinition,
    onUpdate: (ChannelDefinition) -> Unit,
    onDelete: () -> Unit,
    colorSurface: Color,
    colorSurfaceAccent: Color,
    colorTextPrimary: Color,
    colorCyan: Color,
    colorDisconnected: Color,
    colorPurple: Color
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colorSurface),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) colorCyan.copy(0.5f) else colorSurfaceAccent)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Input Offset (Manuale)
                OutlinedTextField(
                    value = (channel.offset + 1).toString(),
                    onValueChange = { 
                        val newOffset = (it.toIntOrNull() ?: 1) - 1
                        if (newOffset >= 0) onUpdate(channel.copy(offset = newOffset))
                    },
                    label = { Text("CH", fontSize = 9.sp) },
                    modifier = Modifier.width(65.dp),
                    textStyle = TextStyle(color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = channel.name,
                    onValueChange = { onUpdate(channel.copy(name = it)) },
                    label = { Text("Nome Funzione", fontSize = 10.sp) },
                    modifier = Modifier.weight(0.4f),
                    textStyle = TextStyle(color = colorTextPrimary, fontSize = 14.sp),
                    singleLine = true
                )

                // Selettore Tipo
                var typeMenuExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(0.4f)) {
                    OutlinedButton(onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = channel.type.name, fontSize = 10.sp, color = colorCyan, maxLines = 1)
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }, modifier = Modifier.background(colorSurfaceAccent)) {
                        ChannelType.entries.forEach { type ->
                            DropdownMenuItem(text = { Text(type.name, color = colorTextPrimary, fontSize = 12.sp) }, onClick = { onUpdate(channel.copy(type = type)); typeMenuExpanded = false })
                        }
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colorTextPrimary)
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = colorDisconnected)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("PRESET DMX / MACRO", color = colorPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                channel.presets.forEachIndexed { pIdx, preset ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = preset.from.toString(), onValueChange = { val v = it.toIntOrNull() ?: 0; val updated = channel.presets.toMutableList(); updated[pIdx] = preset.copy(from = v); onUpdate(channel.copy(presets = updated, hasPresets = true)) }, label = { Text("Da", fontSize = 9.sp) }, modifier = Modifier.width(60.dp), textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = preset.to.toString(), onValueChange = { val v = it.toIntOrNull() ?: 0; val updated = channel.presets.toMutableList(); updated[pIdx] = preset.copy(to = v); onUpdate(channel.copy(presets = updated, hasPresets = true)) }, label = { Text("A", fontSize = 9.sp) }, modifier = Modifier.width(60.dp), textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = preset.label, onValueChange = { val updated = channel.presets.toMutableList(); updated[pIdx] = preset.copy(label = it); onUpdate(channel.copy(presets = updated, hasPresets = true)) }, label = { Text("Etichetta", fontSize = 9.sp) }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp))
                        IconButton(onClick = { val updated = channel.presets.toMutableList(); updated.removeAt(pIdx); onUpdate(channel.copy(presets = updated, hasPresets = updated.isNotEmpty())) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = colorDisconnected, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                TextButton(onClick = { 
                    val lastTo = channel.presets.lastOrNull()?.to ?: -1
                    val start = if (lastTo < 255) lastTo + 1 else 0
                    val updated = channel.presets + DmxValueRange(start, 255, "Nuovo")
                    onUpdate(channel.copy(presets = updated, hasPresets = true)) 
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AGGIUNGI PRESET", fontSize = 10.sp, color = colorCyan)
                }
            }
        }
    }
}
