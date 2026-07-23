package com.paliddo.pdmxcontroller.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.paliddo.pdmxcontroller.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paliddo.pdmxcontroller.data.model.*
import com.paliddo.pdmxcontroller.ui.screens.components.CreateOrCopySceneDialog
import com.paliddo.pdmxcontroller.ui.screens.components.CreateOrCopyShowDialog
import com.paliddo.pdmxcontroller.network.ConnectionState
import com.paliddo.pdmxcontroller.ui.screens.components.ConnectionPanel
import com.paliddo.pdmxcontroller.ui.screens.components.ProfileSelectorDialog
import com.paliddo.pdmxcontroller.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.*

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
    val isBlackout by viewModel.isBlackout.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()

    // --- LAUNCHER PER BACKUP ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportShow(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importShow(it) }
    }

    // Monitoraggio messaggi di backup
    val context = LocalContext.current
    LaunchedEffect(backupStatus) {
        backupStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearBackupStatus()
        }
    }

    // --- STATI LOCALI ---
    val selectedFixtureIds = remember { mutableStateListOf<String>() }
    var previousMasterValue by remember { mutableStateOf(100f) }
    var currentColorSelection by remember { mutableStateOf(Color.Red) }

    // Stati Navigazione Settings
    var isSettingsOpen by remember { mutableStateOf(false) }
    var currentSettingsTab by remember { mutableStateOf("CONTROLLER") }
    var isFixtureEditorOpen by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<FixtureProfile?>(null) }

    // Stato per toggle EDIT CUE (default: chiuso)
    var isCueEditorOpen by remember { mutableStateOf(false) }

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
    var fixtureToDeleteId by remember { mutableStateOf<String?>(null) }
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
    
    val userProfiles by viewModel.userFixtureProfiles.collectAsState()
    val allProfiles = remember(currentShow, userProfiles) { viewModel.getAllAvailableProfiles() }

    // Monitoraggio selezione fixture per aggiornare il colore attuale
    LaunchedEffect(selectedFixtureIds.toList()) {
        if (selectedFixtureIds.isNotEmpty()) {
            val firstFid = selectedFixtureIds.first()
            val inst = currentShow.fixtureInstances.find { it.id == firstFid }
            val profile = allProfiles.find { it.id == inst?.profileId }
            if (inst != null && profile != null) {
                var r = 0; var g = 0; var b = 0
                profile.channels.forEach { ch: ChannelDefinition ->
                    val addr = inst.startAddress - 1 + ch.offset
                    if (addr in dmxData.indices) {
                        val v = dmxData[addr].toInt() and 0xFF
                        when (ch.type) {
                            ChannelType.COLOR_R -> r = v
                            ChannelType.COLOR_G -> g = v
                            ChannelType.COLOR_B -> b = v
                            else -> {}
                        }
                    }
                }
                currentColorSelection = Color(r, g, b)
            }
        }
    }

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
    var cueFadeInput by remember { mutableStateOf(TextFieldValue("0.0")) }

    LaunchedEffect(nextCueNumber) { cueNumberInput = TextFieldValue(nextCueNumber) }

    // --- PALETTE COLORI ---
    val colorBackground = Color(0xFF0B0E14)
    val colorSurface = Color(0xFF161A23)
    val colorSurfaceAccent = Color(0xFF232834)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorPurple = Color(0xFF9D4EDD)
    val colorCyan = Color(0xFF00B4D8)
    val colorDisconnected = Color(0xFFEF4444)
    val colorOrange = Color(0xFFF59E0B)
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
                    onClick = { viewModel.toggleBlackout() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isBlackout) colorCyan else colorDisconnected),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(55.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "BLACK\nOUT", color = if (isBlackout) colorBackground else Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                            HorizontalDivider(color = colorSurfaceAccent)
                            DropdownMenuItem(text = { Text("+ Nuovo Show", color = colorCyan) }, onClick = { createShowDialogOpened = true; showMenuExpanded = false })
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!isLiveMode && !isSettingsOpen) {
                            if (!isLiveMode) {
                                Button(onClick = { isCueEditorOpen = !isCueEditorOpen }, colors = ButtonDefaults.buttonColors(containerColor = if (isCueEditorOpen) colorPurple else colorSurfaceAccent), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp), modifier = Modifier.border(1.dp, if (isCueEditorOpen) Color.Transparent else colorPurple.copy(0.5f), RoundedCornerShape(50))) {
                                    Text("🎬 CUE", color = colorTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                // Pulsante SALVA SHOW
                                Button(onClick = { viewModel.saveCurrentShow() }, colors = ButtonDefaults.buttonColors(containerColor = colorGreenLive), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp), modifier = Modifier.border(1.dp, colorGreenLive, RoundedCornerShape(50))) {
                                    Text("💾 SALVA", color = colorBackground, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
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

                // INDICATORE CONNESSIONE (sempre visibile)
                val connState by viewModel.connectionState.collectAsState()
                val (statusColor, statusLabel) = when (connState) {
                    is ConnectionState.Connected -> colorGreenLive to "ARTNET"
                    is ConnectionState.Scanning -> colorOrange to "SCAN..."
                    is ConnectionState.Connecting -> colorOrange to "CONN..."
                    is ConnectionState.Handshaking -> colorOrange to "HANDSHAKE"
                    is ConnectionState.Disconnected -> colorDisconnected to "OFFLINE"
                    is ConnectionState.Error -> colorDisconnected to "ERRORE"
                    is ConnectionState.DiscoveryFailed -> colorDisconnected to "NON TROVATO"
                    is ConnectionState.Idle -> colorDisconnected to "OFFLINE"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = statusLabel, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                                "CONTROLLER" -> Column(modifier = Modifier.fillMaxSize()) {
                                    val netSettings by viewModel.networkSettings.collectAsState()
                                    val connState by viewModel.connectionState.collectAsState()
                                    val connLog by viewModel.connectionLog.collectAsState()

                                    // Sezione CONNESSIONE (ConnectionPanel)
                                    ConnectionPanel(
                                        connectionState = connState,
                                        connectionLog = connLog,
                                        networkIp = netSettings.ipAddress,
                                        onConnect = { viewModel.connectToController() },
                                        onDisconnect = { viewModel.disconnectFromController() },
                                        onScan = { viewModel.scanForControllers() },
                                        onSetIp = { ip -> viewModel.connectToControllerAt(ip) },
                                        onClearLog = { viewModel.clearConnectionLog() },
                                        modifier = Modifier.weight(1f)
                                    )

                                    HorizontalDivider(color = colorSurfaceAccent, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                                    // Sezione CONFIGURAZIONE RETE (compatto)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("CONFIG. ART-NET", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                        // Auto-Connect Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Auto-Connect", color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("Connetti all'avvio", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp)
                                            }
                                            Switch(
                                                checked = netSettings.autoConnect,
                                                onCheckedChange = { viewModel.updateNetworkSettings(netSettings.copy(autoConnect = it)) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = colorCyan,
                                                    checkedTrackColor = colorCyan.copy(0.3f)
                                                )
                                            )
                                        }

                                        // Porta e Universo
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Porta:", color = colorTextPrimary.copy(0.6f), fontSize = 11.sp)
                                                OutlinedTextField(
                                                    value = netSettings.port.toString(),
                                                    onValueChange = { val p = it.toIntOrNull() ?: 6454; viewModel.updateNetworkSettings(netSettings.copy(port = p)) },
                                                    textStyle = TextStyle(color = colorTextPrimary, fontSize = 13.sp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = colorSurfaceAccent,
                                                        focusedBorderColor = colorCyan
                                                    ),
                                                    modifier = Modifier.height(50.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Universo:", color = colorTextPrimary.copy(0.6f), fontSize = 11.sp)
                                                OutlinedTextField(
                                                    value = netSettings.universe.toString(),
                                                    onValueChange = { val u = it.toIntOrNull() ?: 0; viewModel.updateNetworkSettings(netSettings.copy(universe = u)) },
                                                    textStyle = TextStyle(color = colorTextPrimary, fontSize = 13.sp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = colorSurfaceAccent,
                                                        focusedBorderColor = colorCyan
                                                    ),
                                                    modifier = Modifier.height(50.dp)
                                                )
                                            }
                                        }
                                    }

                                    LaunchedEffect(isConnected) {
                                        if (!isConnected && netSettings.autoConnect && connState is ConnectionState.Idle) {
                                            delay(3000)
                                            if (!isConnected) showConnectionErrorDialog = true
                                        }
                                    }
                                }
                                "FIXTURES" -> Column(modifier = Modifier.fillMaxSize()) {
                                    val userProfiles by viewModel.userFixtureProfiles.collectAsState()
                                    val factoryProfiles = remember { com.paliddo.pdmxcontroller.data.repository.DefaultFixtureLibrary.profiles }
                                    val allLibraryProfiles = remember(userProfiles) { factoryProfiles + userProfiles }

                                    // Launcher per export/import libreria
                                    val exportLibLauncher = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.CreateDocument("application/json")
                                    ) { uri ->
                                        uri?.let { viewModel.exportFixtureLibrary(it) }
                                    }

                                    val importLibLauncher = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.OpenDocument()
                                    ) { uri ->
                                        uri?.let { viewModel.importFixtureLibrary(it) }
                                    }

                                    Text("LIBRERIA FIXTURE", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Pulsanti Export/Import Libreria
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { exportLibLauncher.launch("pdmx_fixture_library.json") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPurple),
                                            border = BorderStroke(1.dp, colorPurple)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("EXPORT LIBRERIA", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { importLibLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorCyan),
                                            border = BorderStroke(1.dp, colorCyan)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("IMPORT LIBRERIA", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("${userProfiles.size} profili utente, ${factoryProfiles.size} factory", color = colorTextPrimary.copy(0.4f), fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
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
                                                        editingProfile = profile.copy(id = UUID.randomUUID().toString())
                                                        isFixtureEditorOpen = true
                                                    }) {
                                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copia", tint = colorCyan)
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
                                "BACKUP" -> Column(modifier = Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("BACKUP E PORTABILITÀ", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = colorSurfaceAccent),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("ESPORTA SHOW CORRENTE", color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                            Text("Salva lo showfile '${currentShow.showName}' in un file JSON per condividerlo o spostarlo su un altro tablet.", color = colorTextPrimary.copy(alpha = 0.6f), fontSize = 12.sp)
                                            Button(
                                                onClick = { exportLauncher.launch("${currentShow.showName}.json") },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = colorPurple)
                                            ) {
                                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("ESPORTA ORA (.json)")
                                            }
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = colorSurfaceAccent),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("IMPORTA SHOW ESTERNO", color = colorTextPrimary, fontWeight = FontWeight.Bold)
                                            Text("Carica uno showfile salvato precedentemente. Se il nome esiste già, ne verrà creata una copia.", color = colorTextPrimary.copy(alpha = 0.6f), fontSize = 12.sp)
                                            Button(
                                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = colorCyan)
                                            ) {
                                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("IMPORTA FILE", color = colorBackground)
                                            }
                                        }
                                    }
                                }
                                "ABOUT" -> AboutContent(context = context, colorSurface = colorSurface, colorSurfaceAccent = colorSurfaceAccent, colorTextPrimary = colorTextPrimary, colorPurple = colorPurple, colorCyan = colorCyan, colorBackground = colorBackground)
                            }
                        }
                    }
                } else {
                    // VISTA WORKSPACE
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (isLiveMode) {
                            // --- LIVE MODE: griglia cue cards (centrale) + lista cue + GO/STOP (destra) ---
                            Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                                Box(modifier = Modifier.fillMaxSize().background(colorSurface, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 110.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        gridItemsIndexed(currentScene.cueList) { idx, cue ->
                                            val isSelected = idx == currentCueIndex
                                            Card(colors = CardDefaults.cardColors(containerColor = if (isSelected) colorGreenLive.copy(0.25f) else colorSurfaceAccent), modifier = Modifier.fillMaxWidth().height(75.dp).border(2.dp, if (isSelected) colorGreenLive else Color.Transparent, RoundedCornerShape(8.dp)).clickable { if (isSingleModeEnabled) viewModel.selectCueManually(idx) else viewModel.triggerFadeToCue(idx) }, shape = RoundedCornerShape(8.dp)) {
                                                Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                                    Text(text = "CUE ${cue.number}", color = if (isSelected) colorGreenLive else colorCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(text = cue.name, color = colorTextPrimary, fontSize = 12.sp, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Colonna destra: lista cue + GO/STOP (sempre in LIVE mode)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(0.35f).fillMaxHeight().background(Color(0xFF11141A), shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                // Selettore scena in Live mode
                                var liveSceneMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colorPurple.copy(0.2f), RoundedCornerShape(6.dp))
                                            .clickable { liveSceneMenuExpanded = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "SCENA: ${currentScene.name} ▾",
                                            color = colorPurple,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = liveSceneMenuExpanded,
                                        onDismissRequest = { liveSceneMenuExpanded = false },
                                        modifier = Modifier.background(colorSurface)
                                    ) {
                                        currentShow.scenes.forEachIndexed { idx, scene ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        scene.name,
                                                        color = if (idx == activeSceneIndex) colorCyan else colorTextPrimary,
                                                        fontWeight = if (idx == activeSceneIndex) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.selectScene(idx)
                                                    liveSceneMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("CUE LIST", color = colorPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, colorSurfaceAccent, RoundedCornerShape(4.dp)).padding(4.dp)) {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        itemsIndexed(currentScene.cueList) { index, cue ->
                                            val isSelected = index == currentCueIndex
                                            val isActive = index == runningCueIndex
                                            val bgColor = when { isActive && isSelected -> colorGreenLive.copy(0.4f); isActive -> colorGreenLive.copy(0.2f); isSelected -> colorPurple.copy(0.4f); else -> Color.Transparent }
                                            val textColor = when { isActive -> colorGreenLive; isSelected -> colorCyan; else -> colorTextPrimary }
                                            Row(modifier = Modifier.fillMaxWidth().background(bgColor, shape = RoundedCornerShape(4.dp)).border(width = 1.dp, color = if (isSelected) colorCyan.copy(0.5f) else Color.Transparent, shape = RoundedCornerShape(4.dp)).clickable { if (isSingleModeEnabled) viewModel.selectCueManually(index) else viewModel.triggerFadeToCue(index) }.padding(8.dp)) {
                                                Text(text = "${cue.number}: ${cue.name}", color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
                        } else {
                            // --- EDIT MODE: fixture, gruppi, controlli (centrale) ---
                            Column(modifier = Modifier.weight(if (isCueEditorOpen) 0.6f else 0.65f).fillMaxHeight()) {
                                Text("GRUPPI SALVATI:", color = colorTextPrimary.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isSelected) colorPurple.copy(0.4f) else colorSurfaceAccent, shape = RoundedCornerShape(4.dp))
                                                    .border(1.dp, if (isSelected) colorCyan else Color.Transparent, shape = RoundedCornerShape(4.dp))
                                                    .clickable { if (isSelected) selectedFixtureIds.remove(fixture.id) else selectedFixtureIds.add(fixture.id); activeGroupId = null }
                                                    .padding(6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = fixture.userGivenName, color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                                        Text(text = "DMX: ${fixture.startAddress}", color = colorCyan, fontSize = 9.sp)
                                                    }
                                                    if (!isLiveMode) {
                                                        IconButton(
                                                            onClick = { fixtureToDeleteId = fixture.id },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = colorDisconnected, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // AREA CONTROLLI AVANZATI (COLOR PICKER & PAN/TILT)
                                Box(modifier = Modifier.fillMaxWidth().weight(1f).background(colorSurface, shape = RoundedCornerShape(8.dp)).padding(8.dp)) {
                                    if (selectedFixtureIds.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Seleziona fixture per i controlli", color = colorTextPrimary.copy(0.4f)) }
                                    } else {
                                        val selectedInstances = currentShow.fixtureInstances.filter { it.id in selectedFixtureIds }
                                        val hasColor = selectedInstances.any { inst -> allProfiles.find { it.id == inst.profileId }?.channels?.any { it.type in listOf(ChannelType.COLOR_R, ChannelType.COLOR_G, ChannelType.COLOR_B) } == true }
                                        val hasPanTilt = selectedInstances.any { inst -> allProfiles.find { it.id == inst.profileId }?.channels?.any { it.type in listOf(ChannelType.PAN, ChannelType.TILT) } == true }

                                        Column {
                                            if (hasColor || hasPanTilt) {
                                                var controlTab by remember { mutableStateOf(if (hasColor) "COLOR" else "POSITION") }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (hasColor) {
                                                        Button(
                                                            onClick = { controlTab = "COLOR" },
                                                            colors = ButtonDefaults.buttonColors(containerColor = if (controlTab == "COLOR") colorPurple else colorSurfaceAccent),
                                                            modifier = Modifier.weight(1f)
                                                        ) { Text("COLORI") }
                                                    }
                                                    if (hasPanTilt) {
                                                        Button(
                                                            onClick = { controlTab = "POSITION" },
                                                            colors = ButtonDefaults.buttonColors(containerColor = if (controlTab == "POSITION") colorPurple else colorSurfaceAccent),
                                                            modifier = Modifier.weight(1f)
                                                        ) { Text("POSIZIONE") }
                                                    }
                                                    Button(
                                                        onClick = { controlTab = "FADERS" },
                                                        colors = ButtonDefaults.buttonColors(containerColor = if (controlTab == "FADERS") colorPurple else colorSurfaceAccent),
                                                        modifier = Modifier.weight(1f)
                                                    ) { Text("FADERS") }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))

                                                when (controlTab) {
                                                    "COLOR" -> ColorControlSection(
                                                        viewModel = viewModel, 
                                                        selectedIds = selectedFixtureIds.toList(), 
                                                        palettes = currentShow.colorPalettes,
                                                        currentColor = currentColorSelection,
                                                        onColorChange = { currentColorSelection = it }
                                                    )
                                                    "POSITION" -> PositionControlSection(viewModel, selectedFixtureIds.toList(), currentShow, allProfiles)
                                                    "FADERS" -> FaderControlSection(viewModel, selectedFixtureIds.toList(), currentShow, allProfiles, dmxData)
                                                }
                                            } else {
                                                FaderControlSection(viewModel, selectedFixtureIds.toList(), currentShow, allProfiles, dmxData)
                                            }
                                        }
                                    }
                                }
                            }

                            // Pannello CUE unificato (solo se EDIT mode e isCueEditorOpen)
                            if (isCueEditorOpen) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(0.35f).fillMaxHeight().background(Color(0xFF11141A), shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                                    // --- SEZIONE 1: RECORD CUE ---
                                    Text("EDIT CUE", color = colorPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedTextField(value = cueNumberInput, onValueChange = { cueNumberInput = it }, label = { Text("Num", fontSize = 9.sp) }, textStyle = TextStyle(color = colorTextPrimary, fontSize = 11.sp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorPurple), modifier = Modifier.weight(0.3f).height(64.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                                        OutlinedTextField(value = cueNameInput, onValueChange = { cueNameInput = it }, label = { Text("Nome", fontSize = 9.sp) }, textStyle = TextStyle(color = colorTextPrimary, fontSize = 11.sp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorPurple), modifier = Modifier.weight(0.7f).height(64.dp), singleLine = true)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = {
                                            val num = cueNumberInput.text.toFloatOrNull() ?: 0f
                                            val fade = cueFadeInput.text.toFloatOrNull() ?: 0.0f
                                            val finalName = if (cueNameInput.text.isEmpty()) "CUE $num" else cueNameInput.text
                                            viewModel.recordCue(num, finalName, fade)
                                        }, modifier = Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = colorPurple), contentPadding = PaddingValues(0.dp)) {
                                            Text("🔴 RECORD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(onClick = {
                                            val currentCue = currentScene.cueList.getOrNull(currentCueIndex)
                                            currentCue?.let { viewModel.duplicateCue(it) }
                                        }, modifier = Modifier.weight(0.6f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = colorSurfaceAccent), enabled = currentCueIndex != -1, contentPadding = PaddingValues(0.dp)) {
                                            Text("👯 DUPLICA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // --- SEZIONE 2: FADE (campo numerico) ---
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = cueFadeInput,
                                        onValueChange = { cueFadeInput = it },
                                        label = { Text("Fade (s)", fontSize = 9.sp) },
                                        textStyle = TextStyle(color = colorTextPrimary, fontSize = 11.sp),
                                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colorSurfaceAccent, focusedBorderColor = colorCyan),
                                        modifier = Modifier.fillMaxWidth().height(64.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        trailingIcon = {
                                            Text("sec", color = colorTextPrimary.copy(0.4f), fontSize = 10.sp)
                                        }
                                    )
                                    HorizontalDivider(color = colorSurfaceAccent, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                    // --- SEZIONE 3: SCENA E CUE LIST ---
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Box {
                                            Text(text = "SCENA: ${currentScene.name} ▾", color = colorPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { sceneMenuExpanded = true })
                                            DropdownMenu(expanded = sceneMenuExpanded, onDismissRequest = { sceneMenuExpanded = false }, modifier = Modifier.background(colorSurface)) {
                                                currentShow.scenes.forEachIndexed { idx, scene ->
                                                    DropdownMenuItem(
                                                        text = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(scene.name, color = colorTextPrimary, modifier = Modifier.weight(1f)); if (currentShow.scenes.size > 1) { Text("🗑", color = colorDisconnected, modifier = Modifier.clickable { sceneToDeleteIndex = idx; sceneMenuExpanded = false }.padding(8.dp)) } } },
                                                        onClick = { viewModel.selectScene(idx); sceneMenuExpanded = false }
                                                    )
                                                }
                                                HorizontalDivider(color = colorSurfaceAccent)
                                                DropdownMenuItem(text = { Text("+ Nuova Scena", color = colorCyan) }, onClick = { createSceneDialogOpened = true; sceneMenuExpanded = false })
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, colorSurfaceAccent, RoundedCornerShape(4.dp)).padding(4.dp)) {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            itemsIndexed(currentScene.cueList) { index, cue ->
                                                val isSelected = index == currentCueIndex
                                                val isActive = index == runningCueIndex
                                                val bgColor = when { isActive && isSelected -> colorGreenLive.copy(0.4f); isActive -> colorGreenLive.copy(0.2f); isSelected -> colorPurple.copy(0.4f); else -> Color.Transparent }
                                                val textColor = when { isActive -> colorGreenLive; isSelected -> colorCyan; else -> colorTextPrimary }
                                                Row(modifier = Modifier.fillMaxWidth().background(bgColor, shape = RoundedCornerShape(4.dp)).border(width = 1.dp, color = if (isSelected) colorCyan.copy(0.5f) else Color.Transparent, shape = RoundedCornerShape(4.dp)).clickable { if (isSingleModeEnabled) viewModel.selectCueManually(index) else viewModel.triggerFadeToCue(index) }.padding(8.dp)) {
                                                    Text(text = "${cue.number}: ${cue.name}", color = textColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    Text("✕", color = colorDisconnected, modifier = Modifier.clickable { viewModel.deleteCue(index) })
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

    if (fixtureToDeleteId != null) {
        val fixtureName = currentShow.fixtureInstances.find { it.id == fixtureToDeleteId }?.userGivenName ?: ""
        AlertDialog(
            onDismissRequest = { fixtureToDeleteId = null },
            containerColor = colorSurface,
            title = { Text("ELIMINA FIXTURE", color = colorDisconnected) },
            text = { Text("Sei sicuro di voler eliminare '$fixtureName' dallo show?", color = colorTextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        fixtureToDeleteId?.let { 
                            viewModel.deleteFixtureInstance(it)
                            selectedFixtureIds.remove(it)
                        }
                        fixtureToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorDisconnected)
                ) {
                    Text("ELIMINA", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { fixtureToDeleteId = null }) {
                    Text("ANNULLA", color = colorTextPrimary)
                }
            }
        )
    }

    if (showConnectionErrorDialog) {
        AlertDialog(onDismissRequest = { showConnectionErrorDialog = false }, containerColor = colorSurface, title = { Text("CONTROLLER NON RAGGIUNGIBILE", color = colorDisconnected) }, text = { Text("L'app non riceve risposta dal controller Art-Net.\n\nAssicurati che:\n1. Il tablet sia connesso al Wi-Fi 'P-DMX GG'\n2. L'indirizzo IP sia correttamente impostato su 192.168.4.1\n3. L'ESP32 sia alimentato correttamente.", color = colorTextPrimary) }, confirmButton = { Button(onClick = { showConnectionErrorDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = colorCyan)) { Text("HO CAPITO", color = colorBackground) } })
    }
}

@Composable
fun ColorControlSection(
    viewModel: MainViewModel, 
    selectedIds: List<String>, 
    palettes: List<ColorPalette>,
    currentColor: Color,
    onColorChange: (Color) -> Unit
) {
    var paletteNameInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    val colorBackground = Color(0xFF0B0E14)
    val colorSurfaceAccent = Color(0xFF232834)
    val colorPurple = Color(0xFF9D4EDD)
    val colorCyan = Color(0xFF00B4D8)
    val colorTextPrimary = Color(0xFFF8FAFC)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // COLOR PICKER (CANVAS) — dimensioni stabili, eredita altezza dalla Row
            Box(modifier = Modifier.fillMaxWidth(0.35f).padding(6.dp)) {
                ColorWheel(initialColor = currentColor) { color ->
                    onColorChange(color)
                    viewModel.applyColorToSelected(selectedIds, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                }
            }

            // INFO E SALVATAGGIO
            Column(modifier = Modifier.weight(0.65f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("COLORE ATTUALE", color = colorCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(40.dp).background(currentColor, CircleShape).border(2.dp, Color.White, CircleShape))
                    
                    var hexInput by remember(currentColor) { 
                        mutableStateOf(String.format("%06X", (0xFFFFFF and currentColor.toArgb())))
                    }
                    
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { 
                            hexInput = it.uppercase()
                            if (it.length == 6) {
                                try {
                                    val parsedColor = Color(android.graphics.Color.parseColor("#$it"))
                                    onColorChange(parsedColor)
                                    viewModel.applyColorToSelected(selectedIds, (parsedColor.red * 255).toInt(), (parsedColor.green * 255).toInt(), (parsedColor.blue * 255).toInt())
                                } catch (e: Exception) {}
                            }
                        },
                        prefix = { Text("#") },
                        modifier = Modifier.width(110.dp),
                        textStyle = TextStyle(color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = paletteNameInput,
                    onValueChange = { paletteNameInput = it },
                    label = { Text("Nome Palette", fontSize = 10.sp) },
                    textStyle = TextStyle(color = colorTextPrimary, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (paletteNameInput.isNotEmpty()) {
                            val hex = String.format("#%06X", (0xFFFFFF and currentColor.toArgb()))
                            viewModel.saveColorPalette(paletteNameInput, hex, (currentColor.red * 255).toInt(), (currentColor.green * 255).toInt(), (currentColor.blue * 255).toInt())
                            paletteNameInput = ""
                            Toast.makeText(context, "Palette Salvata", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorPurple),
                    enabled = paletteNameInput.isNotEmpty()
                ) {
                    Text("SALVA PALETTE", fontSize = 10.sp)
                }
            }
        }

        // Palette colori di base (sempre disponibili)
        Text("COLORI BASE", color = colorCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val baseColors = listOf(
                "Rosso" to Color.Red,
                "Verde" to Color.Green,
                "Blu" to Color.Blue,
                "Giallo" to Color.Yellow,
                "Ciano" to Color.Cyan,
                "Magenta" to Color.Magenta,
                "Bianco" to Color.White,
                "Arancione" to Color(0xFFFFA500)
            )
            baseColors.forEach { (name, color) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(48.dp)
                        .clickable {
                            onColorChange(color)
                            viewModel.applyColorToSelected(selectedIds, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                        }
                        .padding(2.dp)
                ) {
                    Box(modifier = Modifier.size(28.dp).background(color, CircleShape).border(1.dp, Color.White.copy(0.3f), CircleShape))
                    Text(name, color = colorTextPrimary, fontSize = 7.sp, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }

        // ELENCO PALETTE SALVATE
        if (palettes.isNotEmpty()) {
            Text("PALETTE SALVATE", color = colorPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            LazyRow(modifier = Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(palettes) { palette ->
                    val pColor = Color(android.graphics.Color.parseColor(palette.hexCode))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(70.dp)
                            .background(colorSurfaceAccent, RoundedCornerShape(4.dp))
                            .clickable { 
                                onColorChange(pColor)
                                viewModel.applyColorToSelected(selectedIds, palette.r, palette.g, palette.b) 
                            }
                            .padding(4.dp)
                    ) {
                        Box(modifier = Modifier.size(24.dp).background(pColor, CircleShape).border(1.dp, Color.White.copy(0.3f), CircleShape))
                        Text(palette.name, color = colorTextPrimary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(initialColor: Color, onColorSelected: (Color) -> Unit) {
    val hsv = remember(initialColor) {
        val floatArray = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), floatArray)
        floatArray
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val center = Offset(size.width.toFloat() / 2, size.height.toFloat() / 2)
                val touch = change.position
                val distance = sqrt((touch.x - center.x).pow(2) + (touch.y - center.y).pow(2))
                val radius = min(size.width.toFloat(), size.height.toFloat()) / 2
                
                if (distance <= radius) {
                    // atan2 in screen coordinates: 0° at +X (right), +° clockwise due to inverted Y
                    val angle = atan2(touch.y - center.y, touch.x - center.x) * (180 / PI).toFloat()
                    val hue = (angle + 360) % 360
                    val saturation = (distance / radius).coerceIn(0f, 1f)
                    onColorSelected(Color.hsv(hue, saturation, 1f))
                }
            }
        }) {
        val radius = min(size.width, size.height) / 2
        val center = Offset(size.width / 2, size.height / 2)

        // Gradiente in senso orario corrispondente alla ruota HSV standard:
        // 0°=Rosso, 60°=Giallo, 120°=Verde, 180°=Ciano, 240°=Blu, 300°=Magenta
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                center = center
            ),
            radius = radius
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius
        )

        // Cursore: posizione calcolata dall'HSV del colore corrente
        // cos/sin mappano l'angolo HSV in coordinate schermo (Y invertita)
        val angleRad = (hsv[0] * PI / 180f).toFloat()
        val dist = hsv[1] * radius
        val cursorOffset = Offset(
            x = center.x + dist * cos(angleRad),
            y = center.y + dist * sin(angleRad)
        )

        drawCircle(
            color = Color.Black,
            radius = 6.dp.toPx(),
            center = cursorOffset,
            style = Stroke(2.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = cursorOffset
        )
    }
}

@Composable
fun PositionControlSection(viewModel: MainViewModel, selectedIds: List<String>, show: Showfile, allProfiles: List<FixtureProfile>) {
    var pan by remember { mutableFloatStateOf(127f) }
    var tilt by remember { mutableFloatStateOf(127f) }
    
    val colorCyan = Color(0xFF00B4D8)
    val colorSurfaceAccent = Color(0xFF232834)

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PAD PAN / TILT", color = colorCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(colorSurfaceAccent, RoundedCornerShape(8.dp))
                    .border(2.dp, colorCyan.copy(0.3f), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val x = (change.position.x / size.width).coerceIn(0f, 1f)
                            val y = (change.position.y / size.height).coerceIn(0f, 1f)
                            pan = x * 255
                            tilt = y * 255
                            
                            // Invio immediato al ViewModel
                            selectedIds.forEach { fid ->
                                val inst = show.fixtureInstances.find { it.id == fid } ?: return@forEach
                                val profile = allProfiles.find { it.id == inst.profileId } ?: return@forEach
                                profile.channels.forEach { ch ->
                                    val addr = inst.startAddress - 1 + ch.offset
                                    if (ch.type == ChannelType.PAN) viewModel.updateDmxChannel(addr, pan.toInt().toByte())
                                    if (ch.type == ChannelType.TILT) viewModel.updateDmxChannel(addr, tilt.toInt().toByte())
                                }
                            }
                        }
                    }
            ) {
                // Mirino
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val px = (pan / 255) * size.width
                    val py = (tilt / 255) * size.height
                    drawLine(colorCyan.copy(0.3f), Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
                    drawLine(colorCyan.copy(0.3f), Offset(0f, py), Offset(size.width, py), 1.dp.toPx())
                    drawCircle(colorCyan, 8.dp.toPx(), Offset(px, py))
                    drawCircle(Color.White, 8.dp.toPx(), Offset(px, py), style = Stroke(2.dp.toPx()))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("PAN: ${pan.toInt()}", color = Color.White, fontSize = 12.sp)
                Text("TILT: ${tilt.toInt()}", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun updateDmxChannelToZero(viewModel: MainViewModel, selectedIds: List<String>, show: Showfile, allProfiles: List<FixtureProfile>, ch: ChannelDefinition) {
    selectedIds.forEach { fid ->
        show.fixtureInstances.find { it.id == fid }?.let { inst ->
            val p = allProfiles.find { it.id == inst.profileId }
            val actualCh = p?.channels?.find { it.name == ch.name && it.type == ch.type }
            actualCh?.let { viewModel.updateDmxChannel(inst.startAddress - 1 + it.offset, 0) }
        }
    }
}

private fun updateDmxChannelValue(viewModel: MainViewModel, selectedIds: List<String>, show: Showfile, allProfiles: List<FixtureProfile>, ch: ChannelDefinition, value: Int) {
    selectedIds.forEach { fid ->
        show.fixtureInstances.find { it.id == fid }?.let { inst ->
            val p = allProfiles.find { it.id == inst.profileId }
            val actualCh = p?.channels?.find { it.name == ch.name && it.type == ch.type }
            actualCh?.let { viewModel.updateDmxChannel(inst.startAddress - 1 + it.offset, value.toByte()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaderControlSection(viewModel: MainViewModel, selectedIds: List<String>, show: Showfile, allProfiles: List<FixtureProfile>, dmxData: ByteArray) {
    val commonChannels = remember(selectedIds, allProfiles) {
        val selectedInstances = show.fixtureInstances.filter { it.id in selectedIds }
        if (selectedInstances.isEmpty()) return@remember emptyList<ChannelDefinition>()
        val firstProfile = allProfiles.find { it.id == selectedInstances.first().profileId }
        val firstChannels = firstProfile?.channels ?: emptyList()
        firstChannels.filter { ch1 -> selectedInstances.all { inst -> val p = allProfiles.find { it.id == inst.profileId }; p?.channels?.any { ch2 -> ch2.name == ch1.name && ch2.type == ch1.type } == true } }
    }

    val colorSurfaceAccent = Color(0xFF232834)
    val colorCyan = Color(0xFF00B4D8)
    val colorPurple = Color(0xFF9D4EDD)
    val colorBackground = Color(0xFF0B0E14)
    val colorTextPrimary = Color(0xFFF8FAFC)
    val colorDisconnected = Color(0xFFEF4444)

    // Stato toggle per ogni canale: false = vista preset, true = vista fader
    val useFaderForChannels = remember { mutableStateMapOf<String, Boolean>() }

    if (commonChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nessun parametro comune selezionato", color = colorTextPrimary.copy(0.4f)) }
    } else {
        LazyRow(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(commonChannels, key = { it.name + it.offset }) { ch ->
                val firstInst = show.fixtureInstances.find { it.id == selectedIds.first() }!!
                val realDmxFirst = firstInst.startAddress - 1 + ch.offset
                val valRaw = if (realDmxFirst in dmxData.indices) dmxData[realDmxFirst].toInt() and 0xFF else 0
                val channelKey = "${ch.name}_${ch.offset}"
                val showFader = useFaderForChannels[channelKey] ?: false

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight().width(110.dp).background(colorSurfaceAccent, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    // Nome canale
                    Text(text = ch.name.uppercase(), color = colorCyan, fontSize = 10.sp, maxLines = 1)

                    if (ch.hasPresets && ch.presets.isNotEmpty()) {
                        // Label del valore attuale
                        Text(text = ch.presets.find { valRaw in it.from..it.to }?.label ?: "$valRaw", color = colorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(4.dp))

                        // Pulsante ZERO + Toggle PRESET/FADER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Pulsante "0" per azzerare
                            Box(
                                modifier = Modifier
                                    .weight(0.4f)
                                    .background(colorDisconnected.copy(0.3f), RoundedCornerShape(4.dp))
                                    .border(1.dp, colorDisconnected, RoundedCornerShape(4.dp))
                                    .clickable { updateDmxChannelToZero(viewModel, selectedIds, show, allProfiles, ch) }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("RESET", color = colorDisconnected, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            // Toggle PRESET / FADER
                            Box(
                                modifier = Modifier
                                    .weight(0.6f)
                                    .background(if (showFader) colorPurple.copy(0.3f) else colorCyan.copy(0.3f), RoundedCornerShape(4.dp))
                                    .border(1.dp, if (showFader) colorPurple else colorCyan, RoundedCornerShape(4.dp))
                                    .clickable { useFaderForChannels[channelKey] = !showFader }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (showFader) "FADER" else "PRESET",
                                    color = if (showFader) colorPurple else colorCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Contenuto variabile: Preset buttons o Fader verticale
                        if (showFader) {
                            // Modalità FADER: slider verticale
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Slider(
                                    value = valRaw.toFloat(),
                                    onValueChange = { nv -> updateDmxChannelValue(viewModel, selectedIds, show, allProfiles, ch, nv.toInt()) },
                                    valueRange = 0f..255f,
                                    colors = SliderDefaults.colors(activeTrackColor = colorPurple, inactiveTrackColor = colorBackground),
                                    thumb = { Box(modifier = Modifier.width(24.dp).height(36.dp).background(colorCyan, shape = RoundedCornerShape(4.dp))) },
                                    modifier = Modifier
                                        .graphicsLayer { rotationZ = -90f }
                                        .layout { measurable, constraints ->
                                            val customConstraints = constraints.copy(minWidth = constraints.maxHeight, maxWidth = constraints.maxHeight)
                                            val placeable = measurable.measure(customConstraints)
                                            layout(placeable.height, placeable.width) { placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2) }
                                        }
                                        .fillMaxHeight()
                                )
                            }
                        } else {
                            // Modalità PRESET: bottoni macro
                            Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                ch.presets.forEach { preset ->
                                    val isCurrent = valRaw in preset.from..preset.to
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(if (isCurrent) colorPurple else colorBackground, RoundedCornerShape(4.dp))
                                            .border(1.dp, if (isCurrent) colorCyan else Color.Transparent, RoundedCornerShape(4.dp))
                                            .clickable { updateDmxChannelValue(viewModel, selectedIds, show, allProfiles, ch, preset.from) }
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(preset.label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 10.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Canale SENZA preset: mostra valore + fader + pulsante zero
                        @Suppress("DEPRECATION")
                        Text(text = "$valRaw", color = colorTextPrimary, fontWeight = FontWeight.Bold)

                        // Pulsante "0" per azzerare
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorDisconnected.copy(0.3f), RoundedCornerShape(4.dp))
                                .border(1.dp, colorDisconnected, RoundedCornerShape(4.dp))
                                .clickable { updateDmxChannelToZero(viewModel, selectedIds, show, allProfiles, ch) }
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("RESET", color = colorDisconnected, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Slider verticale
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Slider(
                                value = valRaw.toFloat(),
                                onValueChange = { nv -> updateDmxChannelValue(viewModel, selectedIds, show, allProfiles, ch, nv.toInt()) },
                                valueRange = 0f..255f,
                                colors = SliderDefaults.colors(activeTrackColor = colorPurple, inactiveTrackColor = colorBackground),
                                thumb = { Box(modifier = Modifier.width(24.dp).height(36.dp).background(colorCyan, shape = RoundedCornerShape(4.dp))) },
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = -90f }
                                    .layout { measurable, constraints ->
                                        val customConstraints = constraints.copy(minWidth = constraints.maxHeight, maxWidth = constraints.maxHeight)
                                        val placeable = measurable.measure(customConstraints)
                                        layout(placeable.height, placeable.width) { placeable.place(-placeable.width / 2 + placeable.height / 2, -placeable.height / 2 + placeable.width / 2) }
                                    }
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutContent(
    context: android.content.Context,
    colorSurface: Color,
    colorSurfaceAccent: Color,
    colorTextPrimary: Color,
    colorPurple: Color,
    colorCyan: Color,
    colorBackground: Color
) {
    val appVersion = BuildConfig.VERSION_NAME
    val appVersionCode = BuildConfig.VERSION_CODE
    val githubUser = "Paliddo86"
    val githubRepo = "PDMXController"
    val githubApiUrl = "https://api.github.com/repos/$githubUser/$githubRepo/releases/latest"
    val githubRepoUrl = "https://github.com/$githubUser/$githubRepo"

    // Stato per l'update check
    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }

    // Scope per coroutine
    val scope = rememberCoroutineScope()

    // Dialog per risultato aggiornamento
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateDialogMessage by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Icona app
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = com.paliddo.pdmxcontroller.R.drawable.ic_launcher_foreground),
            contentDescription = "PDMX Controller Logo",
            tint = Color.Unspecified,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Nome app
        Text(
            text = "P-DMX CONTROLLER",
            color = colorCyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 4.sp
        )

        // Versione
        Text(
            text = "Versione $appVersion (build $appVersionCode)",
            color = colorTextPrimary.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        // Divisore
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(2.dp)
                .background(colorCyan.copy(alpha = 0.4f))
        )

        // Info sviluppatore
        Text(
            text = "Sviluppato da",
            color = colorTextPrimary.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        Text(
            text = "Paliddo (Luigi Pallante)",
            color = colorTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Link GitHub
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubRepoUrl))
                    context.startActivity(intent)
                }
                .background(colorSurfaceAccent, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\uD83D\uDCC1", fontSize = 16.sp)
            Text(
                text = "github.com/$githubUser/$githubRepo",
                color = colorCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = colorCyan,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Pulsante Cerca aggiornamenti
        Button(
            onClick = {
                updateCheckState = UpdateCheckState.Checking
                scope.launch {
                    val result = checkForUpdates(githubApiUrl, appVersion)
                    updateCheckState = result
                    when (result) {
                        is UpdateCheckState.UpdateAvailable -> {
                            updateDialogMessage = "Nuova versione ${result.latestVersion} disponibile!"
                            updateUrl = result.releaseUrl
                            showUpdateDialog = true
                        }
                        is UpdateCheckState.UpToDate -> {
                            updateDialogMessage = "Sei già all'ultima versione ($appVersion)."
                            showUpdateDialog = true
                        }
                        is UpdateCheckState.Error -> {
                            updateDialogMessage = "Errore durante il controllo: ${result.message}"
                            showUpdateDialog = true
                        }
                        else -> {}
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (updateCheckState) {
                    is UpdateCheckState.Checking -> colorSurfaceAccent
                    else -> colorPurple
                }
            ),
            enabled = updateCheckState !is UpdateCheckState.Checking
        ) {
            if (updateCheckState is UpdateCheckState.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("CONTROLLO IN CORSO...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("\uD83D\uDD0D CERCA AGGIORNAMENTI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Copyright
        Text(
            text = "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} Paliddo",
            color = colorTextPrimary.copy(alpha = 0.3f),
            fontSize = 10.sp
        )
        Text(
            text = "Open source software - Licenza MIT",
            color = colorTextPrimary.copy(alpha = 0.2f),
            fontSize = 9.sp
        )
    }

    // Dialog per risultato aggiornamento
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                updateCheckState = UpdateCheckState.Idle
            },
            containerColor = colorSurface,
            title = {
                Text(
                    if (updateCheckState is UpdateCheckState.UpdateAvailable) "AGGIORNAMENTO DISPONIBILE" else "CONTROLLO AGGIORNAMENTI",
                    color = if (updateCheckState is UpdateCheckState.UpdateAvailable) colorCyan else colorTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(updateDialogMessage, color = colorTextPrimary)
            },
            confirmButton = {
                if (updateCheckState is UpdateCheckState.UpdateAvailable) {
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            updateCheckState = UpdateCheckState.Idle
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorCyan)
                    ) {
                        Text("SCARICA", color = colorBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            updateCheckState = UpdateCheckState.Idle
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorPurple)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        updateCheckState = UpdateCheckState.Idle
                    }
                ) {
                    Text("CHIUDI", color = colorTextPrimary)
                }
            }
        )
    }
}

private sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpdateAvailable(val latestVersion: String, val releaseUrl: String) : UpdateCheckState()
    data object UpToDate : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

private suspend fun checkForUpdates(apiUrl: String, currentVersion: String): UpdateCheckState {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val latestTag = json.getString("tag_name") // es. "v1.0.1"
                val releaseUrl = json.getString("html_url")

                // Confronto versioni: rimuovi 'v' iniziale se presente
                val remoteVer = latestTag.removePrefix("v")
                val localVer = currentVersion.removePrefix("v")

                if (remoteVer != localVer) {
                    UpdateCheckState.UpdateAvailable(latestVersion = latestTag, releaseUrl = releaseUrl)
                } else {
                    UpdateCheckState.UpToDate
                }
            } else if (responseCode == 302 || responseCode == 301) {
                // Redirect - segui
                val redirectUrl = connection.getHeaderField("Location")
                if (redirectUrl != null) {
                    return@withContext checkForUpdates(redirectUrl, currentVersion)
                }
                UpdateCheckState.Error("Risposta inaspettata (redirect)")
            } else {
                UpdateCheckState.Error("Risposta HTTP $responseCode")
            }
        } catch (e: Exception) {
            UpdateCheckState.Error(e.message ?: "Errore sconosciuto")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
