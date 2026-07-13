package com.paliddo.pdmxcontroller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paliddo.pdmxcontroller.data.model.FixtureInstance
import com.paliddo.pdmxcontroller.ui.viewmodel.MainViewModel
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaderScreen(viewModel: MainViewModel) {
    // --- STATI DAL VIEWMODEL ---
    val dmxData by viewModel.dmxState.collectAsState()
    val currentShow by viewModel.currentShow.collectAsState()
    val activeSceneIndex by viewModel.activeSceneIndex.collectAsState()
    val currentCueIndex by viewModel.currentCueIndex.collectAsState()
    val isLoopEnabled by viewModel.isLoopEnabled.collectAsState()
    val isSingleModeEnabled by viewModel.isSingleModeEnabled.collectAsState()
    val isLiveMode by viewModel.isLiveMode.collectAsState()
    val availableShows by viewModel.availableShows.collectAsState()
    val isConnected by viewModel.isControllerConnected.collectAsState()

    // --- STATI LOCALI ---
    val selectedFixtureIds = remember { mutableStateListOf<String>() }
    var grandMasterValue by remember { mutableStateOf(100f) }
    var previousMasterValue by remember { mutableStateOf(100f) } // Per Toggle Blackout

    // Stati Navigazione Settings
    var isSettingsOpen by remember { mutableStateOf(false) }
    var currentSettingsTab by remember { mutableStateOf("CONTROLLER") }

    // Stati Dialog
    var showMenuExpanded by remember { mutableStateOf(false) }
    var sceneMenuExpanded by remember { mutableStateOf(false) }
    var patchDialogOpened by remember { mutableStateOf(false) }
    var groupDialogOpened by remember { mutableStateOf(false) }
    var confirmDeleteGroupDialogOpened by remember { mutableStateOf(false) }

    // Input Dialog Patch
    var newShowNameInput by remember { mutableStateOf("") }
    var newFixtureNameInput by remember { mutableStateOf("") }
    var newFixtureAddressInput by remember { mutableStateOf("1") }
    var newFixtureQuantityInput by remember { mutableStateOf("1") }
    var newGroupNameInput by remember { mutableStateOf("") }
    var selectedProfileIdForPatch by remember { mutableStateOf("std_dimmer") }
    var activeGroupId by remember { mutableStateOf<String?>(null) }

    // --- LOGICA CUE ---
    val currentScene = remember(currentShow, activeSceneIndex) {
        currentShow.scenes.getOrNull(activeSceneIndex) ?: currentShow.scenes.first()
    }
    val allProfiles = remember(currentShow) { viewModel.getAllAvailableProfiles() }
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
    Row(modifier = Modifier.fillMaxSize().background(colorBackground).padding(12.dp)) {

        // ==========================================
        // COLONNA 1: MASTER & BLACKOUT (Sempre visibile)
        // ==========================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight().width(75.dp)
        ) {
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
                        onValueChange = { grandMasterValue = it },
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

            // PULSANTE BLACKOUT CON LOGICA TOGGLE E TESTO CENTRATO
            Button(
                onClick = {
                    if (grandMasterValue > 0f) {
                        previousMasterValue = grandMasterValue
                        grandMasterValue = 0f
                    } else {
                        grandMasterValue = if (previousMasterValue > 0f) previousMasterValue else 100f
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (grandMasterValue == 0f) colorCyan else colorDisconnected),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(55.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "BLACK\nOUT",
                    color = if (grandMasterValue == 0f) colorBackground else Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ==========================================
        // AREA DINAMICA (SETTINGS O WORKSPACE)
        // ==========================================
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

            // --- BARRA SUPERIORE COMUNE ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SHOWFILE: ${currentShow.showName} ▾",
                        color = colorCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { if (!isLiveMode && !isSettingsOpen) { viewModel.refreshShowList(); showMenuExpanded = true } }
                    )
                    Text(
                        text = when {
                            isSettingsOpen -> "IMPOSTAZIONI DI SISTEMA"
                            isLiveMode -> "MODALITÀ LIVE - VIRTUAL EXECUTOR"
                            else -> "MODALITÀ DI PROGRAMMAZIONE"
                        },
                        color = if (isLiveMode) colorGreenLive else colorPurple, fontSize = 9.sp, fontWeight = FontWeight.SemiBold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Tasti Azione Patch/Gruppi (Solo in Edit Mode)
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
                        Button(onClick = { patchDialogOpened = true }, colors = ButtonDefaults.buttonColors(containerColor = colorCyan), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                            Text("🛠 PATCH", color = colorBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // PULSANTE IMPOSTAZIONI (STEP 2)
                    Button(
                        onClick = { isSettingsOpen = !isSettingsOpen },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSettingsOpen) colorCyan else colorSurfaceAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.border(1.dp, if (isSettingsOpen) Color.Transparent else colorCyan.copy(0.5f), RoundedCornerShape(50))
                    ) {
                        Text(if (isSettingsOpen) "✕ CHIUDI" else "⚙️ SETTINGS", color = if (isSettingsOpen) colorBackground else colorTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // SWITCH LIVE/EDIT (Nascosto se Settings è aperto)
                    if (!isSettingsOpen) {
                        Button(
                            onClick = { viewModel.toggleLiveMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLiveMode) colorGreenLive else colorSurfaceAccent),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            modifier = Modifier.border(1.dp, if (isLiveMode) Color.Transparent else colorPurple, RoundedCornerShape(50))
                        ) {
                            Text(if (isLiveMode) "🔒 LIVE" else "📝 EDIT", color = colorTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            // --- CONTENUTO PRINCIPALE ---
            if (isSettingsOpen) {
                // ==========================================
                // VISTA IMPOSTAZIONI (Tabbed)
                // ==========================================
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar interna Settings
                    Column(
                        modifier = Modifier.width(180.dp).fillMaxHeight().background(colorSurface, RoundedCornerShape(8.dp)).padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tabs = listOf("CONTROLLER" to "🎛️ Controller", "FIXTURES" to "💡 Fixtures", "BACKUP" to "💾 Backup", "ABOUT" to "ℹ️ About")
                        tabs.forEach { (id, label) ->
                            val isSelected = currentSettingsTab == id
                            Box(modifier = Modifier.fillMaxWidth().background(if (isSelected) colorPurple else colorSurfaceAccent, RoundedCornerShape(6.dp)).clickable { currentSettingsTab = id }.padding(12.dp)) {
                                Text(text = label, color = colorTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Area Contenuto Settings
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(colorSurface, RoundedCornerShape(8.dp)).padding(20.dp)) {
                        when (currentSettingsTab) {
                            "CONTROLLER" -> Column {
                                Text("CONFIGURAZIONE ART-NET", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                // Campi che implementeremo nello Step 3...
                                Text("Indirizzo IP Controller:", color = colorTextPrimary.copy(0.6f))
                                OutlinedTextField(value = "192.168.4.1", onValueChange = {}, textStyle = TextStyle(color = colorTextPrimary))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Porta Art-Net (Default 6454):", color = colorTextPrimary.copy(0.6f))
                                OutlinedTextField(value = "6454", onValueChange = {}, textStyle = TextStyle(color = colorTextPrimary))
                            }
                            "FIXTURES" -> Text("Libreria Fixture Globali", color = colorCyan)
                            "BACKUP" -> Text("Esportazione/Importazione Show", color = colorCyan)
                            "ABOUT" -> Text("pdmxcontroller v1.0.0", color = colorCyan)
                        }
                    }
                }
            } else {
                // ==========================================
                // VISTA WORKSPACE (Programmazione o Live)
                // ==========================================
                Row(modifier = Modifier.fillMaxSize()) {
                    // AREA CENTRALE (Fader o Executor)
                    Column(modifier = Modifier.weight(0.65f).fillMaxHeight()) {
                        if (isLiveMode) {
                            // LIVE MODE: Griglia Executor
                            Box(modifier = Modifier.fillMaxSize().background(colorSurface, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    gridItemsIndexed(currentScene.cueList) { idx, cue ->
                                        val isSelected = idx == currentCueIndex
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) colorGreenLive.copy(0.25f) else colorSurfaceAccent),
                                            modifier = Modifier.fillMaxWidth().height(75.dp).border(2.dp, if (isSelected) colorGreenLive else Color.Transparent, RoundedCornerShape(8.dp)).clickable { if (isSingleModeEnabled) viewModel.selectCueManually(idx) else viewModel.triggerFadeToCue(idx) },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                Text(text = "CUE ${cue.number}", color = if (isSelected) colorGreenLive else colorCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text(text = cue.name, color = colorTextPrimary, fontSize = 12.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // EDIT MODE: Controllo Fixture
                            Text(text = "GRUPPI SALVATI:", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(currentShow.fixtureGroups) { _, grp ->
                                    Box(modifier = Modifier.background(if (activeGroupId == grp.id) colorPurple.copy(0.4f) else colorSurfaceAccent, shape = RoundedCornerShape(4.dp)).border(1.dp, if (activeGroupId == grp.id) colorCyan else colorPurple.copy(0.5f), shape = RoundedCornerShape(4.dp)).clickable { selectedFixtureIds.clear(); selectedFixtureIds.addAll(grp.fixtureIds); activeGroupId = grp.id }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(text = grp.name, color = colorTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(text = "SELEZIONE FIXTURE:", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                            // Slider Parametri
                            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(colorSurface, shape = RoundedCornerShape(8.dp)).padding(8.dp)) {
                                val currentSelFixture = currentShow.fixtureInstances.find { it.id == selectedFixtureIds.firstOrNull() }
                                if (currentSelFixture == null) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Seleziona fixture per i controlli", color = colorTextPrimary.copy(0.4f)) }
                                } else {
                                    val profile = allProfiles.find { it.id == currentSelFixture.profileId }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        profile?.channels?.let { channels ->
                                            itemsIndexed(channels) { _, ch ->
                                                val realDmx = currentSelFixture.startAddress - 1 + ch.offset
                                                val valRaw = if (realDmx in dmxData.indices) dmxData[realDmx].toInt() and 0xFF else 0
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight().width(110.dp).background(colorSurfaceAccent, shape = RoundedCornerShape(6.dp)).padding(6.dp)) {
                                                    Text(text = ch.name.uppercase(), color = colorCyan, fontSize = 10.sp, maxLines = 1)
                                                    Text(text = "$valRaw", color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                                    Slider(value = valRaw.toFloat(), onValueChange = { nv -> selectedFixtureIds.forEach { fid -> currentShow.fixtureInstances.find { it.id == fid }?.let { inst -> viewModel.updateDmxChannel(inst.startAddress - 1 + ch.offset, nv.toInt().toByte()) } } }, valueRange = 0f..255f, modifier = Modifier.graphicsLayer { rotationZ = -90f }.layout { m, c -> val p = m.measure(c.copy(minWidth = c.maxHeight, maxWidth = c.maxHeight)); layout(p.height, p.width) { p.place(-p.width / 2 + p.height / 2, -p.height / 2 + p.width / 2) } }.fillMaxHeight().weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // COLONNA 3: CUE LIST & CONTROLLO GO
                    Column(modifier = Modifier.weight(0.35f).fillMaxHeight().background(Color(0xFF11141A), shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "SCENA: ${currentScene.name}", color = colorPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(if (isConnected) colorGreenLive else colorDisconnected, RoundedCornerShape(50)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isConnected) "ARTNET" else "OFFLINE", color = if (isConnected) colorGreenLive else colorDisconnected, fontSize = 9.sp)
                            }
                        }

                        // Cue List
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, colorSurfaceAccent, RoundedCornerShape(4.dp)).padding(4.dp)) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(currentScene.cueList) { index, cue ->
                                    val isSelected = index == currentCueIndex
                                    Row(modifier = Modifier.fillMaxWidth().background(if (isSelected) colorPurple.copy(0.4f) else Color.Transparent, shape = RoundedCornerShape(4.dp)).clickable { if (isSingleModeEnabled || isLiveMode) viewModel.selectCueManually(index) else viewModel.triggerFadeToCue(index) }.padding(8.dp)) {
                                        Text(text = "${cue.number}: ${cue.name}", color = if (isSelected) colorCyan else colorTextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        if (!isLiveMode) Text("✕", color = colorDisconnected, modifier = Modifier.clickable { viewModel.deleteCue(index) })
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tasti GO
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { viewModel.toggleSingleMode() }, modifier = Modifier.weight(0.5f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isSingleModeEnabled) colorPurple else colorSurfaceAccent), shape = RoundedCornerShape(4.dp)) {
                                    Text(if (isSingleModeEnabled) "🎯 SINGLE" else "⏭️ NEXT", fontSize = 9.sp)
                                }
                                Button(onClick = { viewModel.toggleLoop() }, modifier = Modifier.weight(0.5f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isLoopEnabled) colorCyan else colorSurfaceAccent), shape = RoundedCornerShape(4.dp)) {
                                    Text("AUTOLOOP", fontSize = 9.sp)
                                }
                            }
                            val isRunning by viewModel.isSequenceRunning.collectAsState()
                            Button(onClick = { viewModel.handleGoStopAction() }, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) colorDisconnected else if (isSingleModeEnabled) colorCyan else colorPurple), shape = RoundedCornerShape(6.dp)) {
                                Text(if (isRunning) "STOP" else if (isSingleModeEnabled) "GO (SEL)" else "GO", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DROPDOWN MENU SHOW ---
    DropdownMenu(expanded = showMenuExpanded, onDismissRequest = { showMenuExpanded = false }, modifier = Modifier.background(colorSurface)) {
        availableShows.forEach { showName ->
            DropdownMenuItem(text = { Text(showName, color = colorTextPrimary) }, onClick = { viewModel.loadShow(showName); showMenuExpanded = false })
        }
    }
}