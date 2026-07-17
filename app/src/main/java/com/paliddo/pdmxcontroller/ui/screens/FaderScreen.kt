package com.paliddo.pdmxcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paliddo.pdmxcontroller.data.model.ChannelDefinition
import com.paliddo.pdmxcontroller.ui.screens.components.CreateOrCopySceneDialog
import com.paliddo.pdmxcontroller.ui.screens.components.CreateOrCopyShowDialog
import com.paliddo.pdmxcontroller.ui.screens.components.ProfileSelectorDialog
import com.paliddo.pdmxcontroller.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaderScreen(viewModel: MainViewModel) {
    // --- STATI DAL VIEWMODEL ---
    val dmxData by viewModel.dmxState.collectAsState()
    val currentShow by viewModel.currentShow.collectAsState()
    val activeSceneIndex by viewModel.activeSceneIndex.collectAsState()
    val currentCueIndex by viewModel.currentCueIndex.collectAsState()
    val runningCueIndex by viewModel.runningCueIndex.collectAsState()
    val isLoopEnabled by viewModel.isLoopEnabled.collectAsState()
    val isSingleModeEnabled by viewModel.isSingleModeEnabled.collectAsState()
    val isLiveMode by viewModel.isLiveMode.collectAsState()
    val availableShows by viewModel.availableShows.collectAsState()
    val isConnected by viewModel.isControllerConnected.collectAsState()
    val grandMasterValue by viewModel.grandMaster.collectAsState()

    // --- STATI LOCALI ---
    val selectedFixtureIds = remember { mutableStateListOf<String>() }
    var previousMasterValue by remember { mutableStateOf(100f) }

    // Stati Navigazione Settings
    var isSettingsOpen by remember { mutableStateOf(false) }
    var currentSettingsTab by remember { mutableStateOf("CONTROLLER") }
    var isFixtureEditorOpen by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<com.paliddo.pdmxcontroller.data.model.FixtureProfile?>(null) }

    // Stati Dialog
    var showMenuExpanded by remember { mutableStateOf(false) }
    var sceneMenuExpanded by remember { mutableStateOf(false) }
    var patchDialogOpened by remember { mutableStateOf(false) }
    var groupDialogOpened by remember { mutableStateOf(false) }
    var confirmDeleteGroupDialogOpened by remember { mutableStateOf(false) }

    // Nuovi stati per gestione Show/Scene
    var createShowDialogOpened by remember { mutableStateOf(false) }
    var createSceneDialogOpened by remember { mutableStateOf(false) }
    var showToDelete by remember { mutableStateOf<String?>(null) }
    var sceneToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var showConnectionErrorDialog by remember { mutableStateOf(false) }

    // Input Dialog Patch
    var newFixtureNameInput by remember { mutableStateOf("") }
    var newFixtureAddressInput by remember { mutableStateOf("1") }
    var newFixtureQuantityInput by remember { mutableStateOf("1") }
    var newGroupNameInput by remember { mutableStateOf("") }
    var selectedProfileIdForPatch by remember { mutableStateOf("std_dimmer") }
    var activeGroupId by remember { mutableStateOf<String?>(null) }

    // Stati Selettore Profili Avanzato
    var isProfileSelectorOpen by remember { mutableStateOf(false) }
    val recentProfileIds = remember { mutableStateListOf("std_dimmer", "rgb_generic", "rgbw_generic") }

    // --- LOGICA CUE ---
    val currentScene = remember(currentShow, activeSceneIndex) {
        currentShow.scenes.getOrNull(activeSceneIndex) ?: currentShow.scenes.first()
    }
    val allProfiles = remember(currentShow) { viewModel.getAllAvailableProfiles() }

    // Auto-popolamento Patch
    LaunchedEffect(selectedProfileIdForPatch, patchDialogOpened) {
        if (patchDialogOpened) {
            val profile = allProfiles.find { it.id == selectedProfileIdForPatch }
            if (newFixtureNameInput.isEmpty() || allProfiles.any { it.modelName == newFixtureNameInput }) {
                newFixtureNameInput = profile?.modelName ?: ""
            }
            
            val instances = currentShow.fixtureInstances
            if (instances.isEmpty()) {
                newFixtureAddressInput = "1"
            } else {
                val nextAddr = instances.maxOf { inst ->
                    val p = allProfiles.find { it.id == inst.profileId }
                    inst.startAddress + (p?.channelCount ?: 1)
                }
                newFixtureAddressInput = nextAddr.coerceIn(1, 512).toString()
            }
        }
    }

    val nextCueNumber = remember(currentScene.cueList) {
        val lastNum = currentScene.cueList.lastOrNull()?.number ?: 0f
        (floor(lastNum) + 1).toInt().toString()
    }
    var cueNumberInput by remember { mutableStateOf(TextFieldValue(nextCueNumber)) }
    var cueNameInput by remember { mutableStateOf(TextFieldValue("")) }
    var cueFadeInput by remember { mutableStateOf(TextFieldValue("2.0")) }

    LaunchedEffect(nextCueNumber) { cueNumberInput = TextFieldValue(nextCueNumber) }

    // --- PALETTE COLORI ---
    val colorBackground = Color(0xFF0B0E14)
    val colorSurface = Color(0xFF161A23)
    val colorSurfaceAccent = Color(0xFF232834)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorPurple = Color(0xFF9D4EDD)
    val colorCyan = Color(0xFF00B4D8)
    val colorDisconnected = Color(0xFFEF4444)
    val colorGreenLive = Color(0xFF10B981)

    // --- INTERFACCIA PRINCIPALE ---
    if (isFixtureEditorOpen) {
        FixtureEditorScreen(
            viewModel = viewModel,
            initialProfile = editingProfile,
            onBack = { 
                isFixtureEditorOpen = false
                editingProfile = null
            }
        )
    } else {
        Row(modifier = Modifier.fillMaxSize().background(colorBackground).padding(12.dp)) {

            // COLONNA 1: MASTER & BLACKOUT
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight().width(75.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1C1326), shape = RoundedCornerShape(8.dp)).border(1.dp, colorPurple.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Text(text = "MASTER", color = colorPurple, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text(text = "${grandMasterValue.toInt()}%", color = colorTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Slider(
                            value = grandMasterValue,
                            onValueChange = { viewModel.setGrandMaster(it) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(activeTrackColor = colorPurple, inactiveTrackColor = colorBackground),
                            thumb = { Box(modifier = Modifier.width(24.dp).height(36.dp).background(colorCyan, shape = RoundedCornerShape(4.dp))) },
                            modifier = Modifier.graphicsLayer { rotationZ = -90f }.layout { measurable, constraints ->
                                val customConstraints = constraints.copy(minWidth = constraints.maxHeight, maxWidth = constraints.maxHeight)
                                val placeable = measurable.measure(customConstraints)
                                layout(placeable.height, placeable.width) { placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2) }
                            }.fillMaxHeight()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (grandMasterValue > 0f) {
                            previousMasterValue = grandMasterValue
                            viewModel.setGrandMaster(0f)
                        } else {
                            viewModel.setGrandMaster(if (previousMasterValue > 0f) previousMasterValue else 100f)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (grandMasterValue == 0f) colorCyan else colorDisconnected),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(55.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "BLACK\nOUT", color = if (grandMasterValue == 0f) colorBackground else Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // AREA DINAMICA
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // BARRA SUPERIORE
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Column {
                            Text(text = "SHOWFILE: ${currentShow.showName} ▾", color = colorCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (!isLiveMode && !isSettingsOpen) { viewModel.refreshShowList(); showMenuExpanded = true } })
                            Text(text = when { isSettingsOpen -> "IMPOSTAZIONI DI SISTEMA"; isLiveMode -> "MODALITÀ LIVE"; else -> "MODALITÀ DI PROGRAMMAZIONE" }, color = if (isLiveMode) colorGreenLive else colorPurple, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                        DropdownMenu(expanded = showMenuExpanded, onDismissRequest = { showMenuExpanded = false }, modifier = Modifier.background(colorSurface)) {
                            availableShows.forEach { showName ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(showName, color = colorTextPrimary, modifier = Modifier.weight(1f))
                                            if (showName != "Default_Show") {
                                                Text("🗑", color = colorDisconnected, modifier = Modifier.clickable { showToDelete = showName; showMenuExpanded = false }.padding(8.dp))
                                            }
                                        }
                                    },
                                    onClick = { viewModel.loadShow(showName); showMenuExpanded = false }
                                )
                            }
                            Divider(color = colorSurfaceAccent)
                            DropdownMenuItem(text = { Text("+ Nuovo Show", color = colorCyan) }, onClick = { createShowDialogOpened = true; showMenuExpanded = false })
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!isLiveMode && !isSettingsOpen) {
                            if (selectedFixtureIds.isNotEmpty()) {
                                Button(onClick = { groupDialogOpened = true }, colors = ButtonDefaults.buttonColors(containerColor = colorPurple), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                                    Text("💾 GRUPPO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                if (activeGroupId != null) {
                                    Button(onClick = { confirmDeleteGroupDialogOpened = true }, colors = ButtonDefaults.buttonColors(containerColor = colorDisconnected), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                                        Text("🗑 ELIMINA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Button(onClick = { patchDialogOpened = true }, colors = ButtonDefaults.buttonColors(containerColor = colorSurfaceAccent), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), modifier = Modifier.border(1.dp, colorCyan.copy(alpha = 0.5f), RoundedCornerShape(50))) {
                                Text("🛠 PATCH", color = colorTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(onClick = { isSettingsOpen = !isSettingsOpen }, colors = ButtonDefaults.buttonColors(containerColor = if (isSettingsOpen) colorCyan else colorSurfaceAccent), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), modifier = Modifier.border(1.dp, if (isSettingsOpen) Color.Transparent else colorCyan.copy(0.5f), RoundedCornerShape(50))) {
                            Text(if (isSettingsOpen) "✕ CHIUDI" else "⚙️ SETTINGS", color = if (isSettingsOpen) colorBackground else colorTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (!isSettingsOpen) {
                            Button(onClick = { viewModel.toggleLiveMode() }, colors = ButtonDefaults.buttonColors(containerColor = if (isLiveMode) colorGreenLive else colorSurfaceAccent), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp), modifier = Modifier.border(1.dp, if (isLiveMode) Color.Transparent else colorPurple, RoundedCornerShape(50))) {
                                Text(if (isLiveMode) "🔒 LIVE" else "📝 EDIT", color = colorTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                if (isSettingsOpen) {
                    // VISTA IMPOSTAZIONI
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.width(180.dp).fillMaxHeight().background(colorSurface, RoundedCornerShape(8.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val tabs = listOf("CONTROLLER" to "🎛️ Controller", "FIXTURES" to "💡 Fixtures", "BACKUP" to "💾 Backup", "ABOUT" to "ℹ️ About")
                            tabs.forEach { (id, label) ->
                                val isSelected = currentSettingsTab == id
                                Box(modifier = Modifier.fillMaxWidth().background(if (isSelected) colorPurple else colorSurfaceAccent, RoundedCornerShape(6.dp)).clickable { currentSettingsTab = id }.padding(12.dp)) {
                                    Text(text = label, color = colorTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(colorSurface, RoundedCornerShape(8.dp)).padding(20.dp)) {
                            when (currentSettingsTab) {
                                "CONTROLLER" -> Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val netSettings by viewModel.networkSettings.collectAsState()
                                    Text("CONFIGURAZIONE ART-NET", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("Connessione Automatica", color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                            Text("Avvia lo streaming all'apertura dell'app", color = colorTextPrimary.copy(0.5f), fontSize = 11.sp)
                                        }
                                        Switch(checked = netSettings.autoConnect, onCheckedChange = { viewModel.updateNetworkSettings(netSettings.copy(autoConnect = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = colorCyan, checkedTrackColor = colorCyan.copy(0.3f)))
                                    }
                                    Divider(color = colorSurfaceAccent, thickness = 1.dp)
                                    Text("Indirizzo IP Controller:", color = colorTextPrimary.copy(0.6f), fontSize = 12.sp)
                                    OutlinedTextField(value = netSettings.ipAddress, onValueChange = { viewModel.updateNetworkSettings(netSettings.copy(ipAddress = it)) }, textStyle = TextStyle(color = colorTextPrimary), modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorCyan))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Column(modifier = Modifier.weight(0.5f)) {
                                            Text("Porta Art-Net:", color = colorTextPrimary.copy(0.6f), fontSize = 12.sp)
                                            OutlinedTextField(value = netSettings.port.toString(), onValueChange = { val p = it.toIntOrNull() ?: 6454; viewModel.updateNetworkSettings(netSettings.copy(port = p)) }, textStyle = TextStyle(color = colorTextPrimary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorCyan))
                                        }
                                        Column(modifier = Modifier.weight(0.5f)) {
                                            Text("Universo (Sub/Net):", color = colorTextPrimary.copy(0.6f), fontSize = 12.sp)
                                            OutlinedTextField(value = netSettings.universe.toString(), onValueChange = { val u = it.toIntOrNull() ?: 0; viewModel.updateNetworkSettings(netSettings.copy(universe = u)) }, textStyle = TextStyle(color = colorTextPrimary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorCyan))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(onClick = { viewModel.updateNetworkSettings(netSettings.copy(autoConnect = true)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colorPurple)) { Text("FORZA RICONNESSIONE", fontWeight = FontWeight.Bold) }
                                    
                                    LaunchedEffect(isConnected) {
                                        if (!isConnected && netSettings.autoConnect) {
                                            delay(5000)
                                            if (!isConnected) showConnectionErrorDialog = true
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    val details by viewModel.controllerDetails.collectAsState()
                                    Card(colors = CardDefaults.cardColors(containerColor = if (isConnected) colorGreenLive.copy(0.1f) else colorDisconnected.copy(0.1f)), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, if (isConnected) colorGreenLive else colorDisconnected)) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                                                Box(modifier = Modifier.size(10.dp).background(if (isConnected) colorGreenLive else colorDisconnected, RoundedCornerShape(50)))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = if (isConnected) "CONTROLLER ONLINE" else "CONTROLLER OFFLINE", color = if (isConnected) colorGreenLive else colorDisconnected, fontWeight = FontWeight.Bold)
                                            }
                                            if (isConnected && details != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Divider(color = colorGreenLive.copy(alpha = 0.2f))
                                                Spacer(modifier = Modifier.height(12.dp))
                                                DetailRow("Nome:", details?.shortName ?: "-")
                                                DetailRow("Modello:", details?.longName ?: "-")
                                                DetailRow("IP:", details?.ipAddress ?: "-")
                                                DetailRow("Firmware:", details?.firmwareVersion ?: "-")
                                                DetailRow("Stato:", details?.status ?: "OK")
                                            }
                                        }
                                    }
                                }
                                "FIXTURES" -> Column(modifier = Modifier.fillMaxSize()) {
                                    val userProfiles by viewModel.userFixtureProfiles.collectAsState()
                                    val factoryProfiles = remember { com.paliddo.pdmxcontroller.data.repository.DefaultFixtureLibrary.profiles }
                                    val allLibraryProfiles = remember(userProfiles) { factoryProfiles + userProfiles }

                                    Text("LIBRERIA FIXTURE", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(allLibraryProfiles) { profile ->
                                            val isFactory = factoryProfiles.any { it.id == profile.id }
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = colorSurfaceAccent),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(profile.modelName, color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                                        Text(if (isFactory) "FACTORY - ${profile.manufacturer}" else profile.manufacturer, color = colorCyan, fontSize = 11.sp)
                                                    }
                                                    
                                                    IconButton(onClick = { 
                                                        editingProfile = profile.copy(id = java.util.UUID.randomUUID().toString())
                                                        isFixtureEditorOpen = true
                                                    }) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Copia",
                                                            tint = colorCyan
                                                        )
                                                    }

                                                    if (!isFactory) {
                                                        IconButton(onClick = { 
                                                            editingProfile = profile
                                                            isFixtureEditorOpen = true
                                                        }) {
                                                            Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = colorPurple)
                                                        }
                                                        IconButton(onClick = { viewModel.removeUserFixtureProfile(profile.id) }) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = colorDisconnected)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Button(
                                        onClick = { 
                                            editingProfile = null
                                            isFixtureEditorOpen = true 
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = colorPurple)
                                    ) {
                                        Text("+ CREA NUOVO PROFILO")
                                    }
                                }
                                "BACKUP" -> Text("Esportazione/Importazione Show", color = colorCyan)
                                "ABOUT" -> Text("pdmxcontroller v1.0.0", color = colorCyan)
                            }
                        }
                    }
                } else {
                    // VISTA WORKSPACE
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (!isLiveMode) {
                            Column(modifier = Modifier.width(160.dp).fillMaxHeight().background(colorSurface, RoundedCornerShape(8.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("EDIT CUE", color = colorPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedTextField(value = cueNumberInput, onValueChange = { cueNumberInput = it }, label = { Text("Num", fontSize = 10.sp) }, textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorPurple), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                OutlinedTextField(value = cueNameInput, onValueChange = { cueNameInput = it }, label = { Text("Nome", fontSize = 10.sp) }, textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorPurple), modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = cueFadeInput, onValueChange = { cueFadeInput = it }, label = { Text("Fade (s)", fontSize = 10.sp) }, textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorPurple), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                                Button(onClick = { val num = cueNumberInput.text.toFloatOrNull() ?: 0f; val fade = cueFadeInput.text.toFloatOrNull() ?: 2.0f; val finalName = if (cueNameInput.text.isEmpty()) "CUE $num" else cueNameInput.text; viewModel.recordCue(num, finalName, fade) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colorPurple)) { Text("🔴 RECORD", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                Button(onClick = { val currentCue = currentScene.cueList.getOrNull(currentCueIndex); currentCue?.let { viewModel.duplicateCue(it) } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colorSurfaceAccent), enabled = currentCueIndex != -1) { Text("👯 DUPLICA", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(if (isLiveMode) 0.65f else 0.5f).fillMaxHeight()) {
                            if (isLiveMode) {
                                Box(modifier = Modifier.fillMaxSize().background(colorSurface, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        gridItemsIndexed(currentScene.cueList) { idx, cue ->
                                            val isSelected = idx == currentCueIndex
                                            Card(colors = CardDefaults.cardColors(containerColor = if (isSelected) colorGreenLive.copy(0.25f) else colorSurfaceAccent), modifier = Modifier.fillMaxWidth().height(75.dp).border(2.dp, if (isSelected) colorGreenLive else Color.Transparent, RoundedCornerShape(8.dp)).clickable { if (isSingleModeEnabled) viewModel.selectCueManually(idx) else viewModel.triggerFadeToCue(idx) }, shape = RoundedCornerShape(8.dp)) {
                                                @Suppress("DEPRECATION")
                                                Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = "CUE ${cue.number}", color = if (isSelected) colorGreenLive else colorCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(text = cue.name, color = colorTextPrimary, fontSize = 12.sp, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(text = "GRUPPI SALVATI:", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    itemsIndexed(currentShow.fixtureGroups) { _, grp ->
                                        Box(modifier = Modifier.background(if (activeGroupId == grp.id) colorPurple.copy(0.4f) else colorSurfaceAccent, shape = RoundedCornerShape(4.dp)).border(1.dp, if (activeGroupId == grp.id) colorCyan else colorPurple.copy(0.5f), shape = RoundedCornerShape(4.dp)).clickable { selectedFixtureIds.clear(); selectedFixtureIds.addAll(grp.fixtureIds); activeGroupId = grp.id }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(text = grp.name, color = colorTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(text = "SELEZIONE FIXTURE:", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f)) {}
                                    if (selectedFixtureIds.isNotEmpty()) {
                                        Text(text = "DESELEZIONA TUTTO ✕", color = colorDisconnected, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { selectedFixtureIds.clear(); activeGroupId = null }.padding(vertical = 4.dp))
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(90.dp).background(colorSurface, shape = RoundedCornerShape(6.dp)).padding(6.dp)) {
                                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(currentShow.fixtureInstances) { fixture ->
                                            val isSelected = selectedFixtureIds.contains(fixture.id)
                                            Box(modifier = Modifier.background(if (isSelected) colorPurple.copy(0.4f) else colorSurfaceAccent, shape = RoundedCornerShape(4.dp)).border(1.dp, if (isSelected) colorCyan else Color.Transparent, shape = RoundedCornerShape(4.dp)).clickable { if (isSelected) selectedFixtureIds.remove(fixture.id) else selectedFixtureIds.add(fixture.id); activeGroupId = null }.padding(6.dp)) {
                                                Column {
                                                    Text(text = fixture.userGivenName, color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                                    Text(text = "DMX: ${fixture.startAddress}", color = colorCyan, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(colorSurface, shape = RoundedCornerShape(8.dp)).padding(8.dp)) {
                                    if (selectedFixtureIds.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Seleziona fixture per i controlli", color = colorTextPrimary.copy(0.4f)) }
                                    } else {
                                        val commonChannels = remember(selectedFixtureIds.toList(), currentShow.fixtureInstances) {
                                            val selectedInstances = currentShow.fixtureInstances.filter { it.id in selectedFixtureIds }
                                            if (selectedInstances.isEmpty()) return@remember emptyList<ChannelDefinition>()
                                            val firstProfile = allProfiles.find { it.id == selectedInstances.first().profileId }
                                            val firstChannels = firstProfile?.channels ?: emptyList()
                                            firstChannels.filter { ch1 -> selectedInstances.all { inst -> val p = allProfiles.find { it.id == inst.profileId }; p?.channels?.any { ch2 -> ch2.name == ch1.name && ch2.type == ch1.type } == true } }
                                        }

                                        if (commonChannels.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nessun parametro comune selezionato", color = colorTextPrimary.copy(0.4f)) }
                                        } else {
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                items(commonChannels, key = { it.name + it.offset }) { ch ->
                                                    val firstInst = currentShow.fixtureInstances.find { it.id == selectedFixtureIds.first() }!!
                                                    val realDmxFirst = firstInst.startAddress - 1 + ch.offset
                                                    val valRaw = if (realDmxFirst in dmxData.indices) dmxData[realDmxFirst].toInt() and 0xFF else 0
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight().width(110.dp).background(colorSurfaceAccent, shape = RoundedCornerShape(6.dp)).padding(6.dp)) {
                                                        Text(text = ch.name.uppercase(), color = colorCyan, fontSize = 10.sp, maxLines = 1)
                                                        if (ch.hasPresets && ch.presets.isNotEmpty()) {
                                                            Text(text = ch.presets.find { valRaw in it.from..it.to }?.label ?: "$valRaw", color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                ch.presets.forEach { preset ->
                                                                    val isCurrent = valRaw in preset.from..preset.to
                                                                    Box(modifier = Modifier.fillMaxWidth().weight(1f).background(if (isCurrent) colorPurple else colorBackground, RoundedCornerShape(4.dp)).border(1.dp, if (isCurrent) colorCyan else Color.Transparent, RoundedCornerShape(4.dp)).clickable { selectedFixtureIds.forEach { fid -> currentShow.fixtureInstances.find { it.id == fid }?.let { inst -> val p = allProfiles.find { it.id == inst.profileId }; val actualCh = p?.channels?.find { it.name == ch.name && it.type == ch.type }; actualCh?.let { viewModel.updateDmxChannel(inst.startAddress - 1 + it.offset, preset.from.toByte()) } } } }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                                                        Text(preset.label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 10.sp)
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Text(text = "$valRaw", color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                                            Spacer(modifier = Modifier.height(10.dp))
                                                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                                Slider(value = valRaw.toFloat(), onValueChange = { nv -> selectedFixtureIds.forEach { fid -> currentShow.fixtureInstances.find { it.id == fid }?.let { inst -> val p = allProfiles.find { it.id == inst.profileId }; val actualCh = p?.channels?.find { it.name == ch.name && it.type == ch.type }; actualCh?.let { viewModel.updateDmxChannel(inst.startAddress - 1 + it.offset, nv.toInt().toByte()) } } } }, valueRange = 0f..255f, colors = SliderDefaults.colors(activeTrackColor = colorPurple, inactiveTrackColor = colorBackground), thumb = { Box(modifier = Modifier.width(24.dp).height(36.dp).background(colorCyan, shape = RoundedCornerShape(4.dp))) }, modifier = Modifier.graphicsLayer { rotationZ = -90f }.layout { measurable, constraints -> val customConstraints = constraints.copy(minWidth = constraints.maxHeight, maxWidth = constraints.maxHeight); val placeable = measurable.measure(customConstraints); layout(placeable.height, placeable.width) { placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2) } }.fillMaxHeight())
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // COLONNA 3: CUE LIST
                    Column(modifier = Modifier.weight(0.35f).fillMaxHeight().background(Color(0xFF11141A), shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Text(text = "SCENA: ${currentScene.name} ▾", color = colorPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { sceneMenuExpanded = true })
                                DropdownMenu(expanded = sceneMenuExpanded, onDismissRequest = { sceneMenuExpanded = false }, modifier = Modifier.background(colorSurface)) {
                                    currentShow.scenes.forEachIndexed { idx, scene ->
                                        DropdownMenuItem(
                                            text = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(scene.name, color = colorTextPrimary, modifier = Modifier.weight(1f)); if (currentShow.scenes.size > 1) { Text("🗑", color = colorDisconnected, modifier = Modifier.clickable { sceneToDeleteIndex = idx; sceneMenuExpanded = false }.padding(8.dp)) } } },
                                            onClick = { viewModel.selectScene(idx); sceneMenuExpanded = false }
                                        )
                                    }
                                    Divider(color = colorSurfaceAccent)
                                    DropdownMenuItem(text = { Text("+ Nuova Scena", color = colorCyan) }, onClick = { createSceneDialogOpened = true; sceneMenuExpanded = false })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(if (isConnected) colorGreenLive else colorDisconnected, RoundedCornerShape(50)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isConnected) "ARTNET" else "OFFLINE", color = if (isConnected) colorGreenLive else colorDisconnected, fontSize = 9.sp)
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, colorSurfaceAccent, RoundedCornerShape(4.dp)).padding(4.dp)) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(currentScene.cueList) { index, cue ->
                                    val isSelected = index == currentCueIndex
                                    val isActive = index == runningCueIndex
                                    val bgColor = when { isActive && isSelected -> colorGreenLive.copy(0.4f); isActive -> colorGreenLive.copy(0.2f); isSelected -> colorPurple.copy(0.4f); else -> Color.Transparent }
                                    val textColor = when { isActive -> colorGreenLive; isSelected -> colorCyan; else -> colorTextPrimary }
                                    Row(modifier = Modifier.fillMaxWidth().background(bgColor, shape = RoundedCornerShape(4.dp)).border(width = 1.dp, color = if (isSelected) colorCyan.copy(0.5f) else Color.Transparent, shape = RoundedCornerShape(4.dp)).clickable { if (isSingleModeEnabled || isLiveMode) viewModel.selectCueManually(index) else viewModel.triggerFadeToCue(index) }.padding(8.dp)) {
                                        Text(text = "${cue.number}: ${cue.name}", color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (!isLiveMode) Text("✕", color = colorDisconnected, modifier = Modifier.clickable { viewModel.deleteCue(index) })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { viewModel.toggleSingleMode() }, modifier = Modifier.weight(0.5f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isSingleModeEnabled) colorPurple else colorSurfaceAccent), shape = RoundedCornerShape(4.dp)) { Text(if (isSingleModeEnabled) "🎯 SINGLE" else "⏭️ NEXT", fontSize = 9.sp) }
                                Button(onClick = { viewModel.toggleLoop() }, modifier = Modifier.weight(0.5f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isLoopEnabled) colorCyan else colorSurfaceAccent), shape = RoundedCornerShape(4.dp)) { Text("AUTOLOOP", fontSize = 9.sp) }
                            }
                            val isRunning by viewModel.isSequenceRunning.collectAsState()
                            Button(onClick = { viewModel.handleGoStopAction() }, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) colorDisconnected else if (isSingleModeEnabled) colorCyan else colorPurple), shape = RoundedCornerShape(6.dp)) { Text(if (isRunning) "STOP" else if (isSingleModeEnabled) "GO (SEL)" else "GO", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
            }
        }
    }

    // DIALOG PATCH
    if (patchDialogOpened) {
        AlertDialog(
            onDismissRequest = { patchDialogOpened = false },
            containerColor = colorSurface,
            title = { Text("PATCH NUOVE FIXTURE", color = colorCyan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newFixtureNameInput, onValueChange = { newFixtureNameInput = it }, label = { Text("Nome Base") }, textStyle = TextStyle(color = colorTextPrimary))
                    Text("Profilo:", color = colorTextPrimary, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f).background(colorSurfaceAccent, RoundedCornerShape(4.dp)).clickable { isProfileSelectorOpen = true }.padding(12.dp)) { Text(text = allProfiles.find { it.id == selectedProfileIdForPatch }?.modelName ?: "Seleziona...", color = colorTextPrimary) }
                        Button(onClick = { isProfileSelectorOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = colorPurple), shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("🔍 LIBRERIA", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                    val recentProfiles = allProfiles.filter { recentProfileIds.contains(it.id) }
                    if (recentProfiles.isNotEmpty()) {
                        Text("Recenti:", color = colorTextPrimary.copy(alpha = 0.6f), fontSize = 10.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentProfiles) { p -> FilterChip(selected = selectedProfileIdForPatch == p.id, onClick = { selectedProfileIdForPatch = p.id; newFixtureNameInput = p.modelName }, label = { Text(p.modelName) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorCyan, labelColor = colorTextPrimary)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = newFixtureAddressInput, onValueChange = { newFixtureAddressInput = it }, label = { Text("DMX") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = colorTextPrimary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = newFixtureQuantityInput, onValueChange = { newFixtureQuantityInput = it }, label = { Text("Quantità") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = colorTextPrimary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            },
            confirmButton = { Button(onClick = { val addr = newFixtureAddressInput.toIntOrNull() ?: 1; val quant = newFixtureQuantityInput.toIntOrNull() ?: 1; viewModel.patchMultipleFixtures(newFixtureNameInput, selectedProfileIdForPatch, addr, quant); patchDialogOpened = false }, colors = ButtonDefaults.buttonColors(containerColor = colorCyan)) { Text("PATCH", color = colorBackground) } },
            dismissButton = { TextButton(onClick = { patchDialogOpened = false }) { Text("ANNULLA", color = colorTextPrimary) } }
        )
    }

    // DIALOG GRUPPI
    if (groupDialogOpened) {
        AlertDialog(onDismissRequest = { groupDialogOpened = false }, containerColor = colorSurface, title = { Text("CREA GRUPPO", color = colorPurple) }, text = { OutlinedTextField(value = newGroupNameInput, onValueChange = { newGroupNameInput = it }, label = { Text("Nome Gruppo") }, textStyle = TextStyle(color = colorTextPrimary)) }, confirmButton = { Button(onClick = { viewModel.createFixtureGroup(newGroupNameInput, selectedFixtureIds.toList()); groupDialogOpened = false; newGroupNameInput = "" }, colors = ButtonDefaults.buttonColors(containerColor = colorPurple)) { Text("SALVA", color = Color.White) } })
    }

    if (confirmDeleteGroupDialogOpened) {
        AlertDialog(onDismissRequest = { confirmDeleteGroupDialogOpened = false }, containerColor = colorSurface, title = { Text("ELIMINA GRUPPO", color = colorDisconnected) }, text = { Text("Sei sicuro di voler eliminare questo gruppo?", color = colorTextPrimary) }, confirmButton = { Button(onClick = { activeGroupId?.let { viewModel.deleteFixtureGroup(it) }; confirmDeleteGroupDialogOpened = false; activeGroupId = null; selectedFixtureIds.clear() }, colors = ButtonDefaults.buttonColors(containerColor = colorDisconnected)) { Text("ELIMINA", color = Color.White) } }, dismissButton = { TextButton(onClick = { confirmDeleteGroupDialogOpened = false }) { Text("ANNULLA", color = colorTextPrimary) } })
    }

    if (isProfileSelectorOpen) {
        ProfileSelectorDialog(allProfiles = allProfiles, selectedProfileId = selectedProfileIdForPatch, onProfileSelected = { id -> selectedProfileIdForPatch = id; if (!recentProfileIds.contains(id)) { recentProfileIds.add(0, id); if (recentProfileIds.size > 5) recentProfileIds.removeAt(recentProfileIds.size - 1) }; isProfileSelectorOpen = false }, onDismiss = { isProfileSelectorOpen = false })
    }

    if (createShowDialogOpened) {
        CreateOrCopyShowDialog(availableShows = availableShows, onConfirm = { name, source -> if (source != null) viewModel.copyShow(source, name) else viewModel.createNewShow(name); createShowDialogOpened = false }, onDismiss = { createShowDialogOpened = false })
    }

    if (createSceneDialogOpened) {
        CreateOrCopySceneDialog(existingScenes = currentShow.scenes.map { it.name }, onConfirm = { name, sourceIndex -> if (sourceIndex != null) viewModel.createSceneFromExisting(sourceIndex, name) else viewModel.createNewScene(name); createSceneDialogOpened = false }, onDismiss = { createSceneDialogOpened = false })
    }

    if (showToDelete != null) {
        AlertDialog(onDismissRequest = { showToDelete = null }, containerColor = colorSurface, title = { Text("ELIMINA SHOWFILE", color = colorDisconnected) }, text = { Text("Sei sicuro di voler eliminare lo show '${showToDelete}'? L'operazione non è reversibile.", color = colorTextPrimary) }, confirmButton = { Button(onClick = { showToDelete?.let { viewModel.deleteShow(it) }; showToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = colorDisconnected)) { Text("ELIMINA", color = Color.White) } }, dismissButton = { TextButton(onClick = { showToDelete = null }) { Text("ANNULLA", color = colorTextPrimary) } })
    }

    if (sceneToDeleteIndex != null) {
        val sceneName = sceneToDeleteIndex?.let { currentShow.scenes.getOrNull(it)?.name } ?: ""
        AlertDialog(onDismissRequest = { sceneToDeleteIndex = null }, containerColor = colorSurface, title = { Text("ELIMINA SCENA", color = colorDisconnected) }, text = { Text("Sei sicuro di voler eliminare la scena '$sceneName'? Tutte le cue contenute andranno perse.", color = colorTextPrimary) }, confirmButton = { Button(onClick = { sceneToDeleteIndex?.let { viewModel.deleteScene(it) }; sceneToDeleteIndex = null }, colors = ButtonDefaults.buttonColors(containerColor = colorDisconnected)) { Text("ELIMINA", color = Color.White) } }, dismissButton = { TextButton(onClick = { sceneToDeleteIndex = null }) { Text("ANNULLA", color = colorTextPrimary) } })
    }

    if (showConnectionErrorDialog) {
        AlertDialog(onDismissRequest = { showConnectionErrorDialog = false }, containerColor = colorSurface, title = { Text("CONTROLLER NON RAGGIUNGIBILE", color = colorDisconnected) }, text = { Text("L'app non riceve risposta dal controller Art-Net.\n\nAssicurati che:\n1. Il tablet sia connesso al Wi-Fi 'P-DMX GG'\n2. L'indirizzo IP sia correttamente impostato su 192.168.4.1\n3. L'ESP32 sia alimentato correttamente.", color = colorTextPrimary) }, confirmButton = { Button(onClick = { showConnectionErrorDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = colorCyan)) { Text("HO CAPITO", color = colorBackground) } })
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
