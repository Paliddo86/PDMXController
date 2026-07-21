package com.paliddo.pdmxcontroller.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paliddo.pdmxcontroller.data.model.*
import com.paliddo.pdmxcontroller.data.repository.FixtureLibraryRepository
import com.paliddo.pdmxcontroller.data.repository.ShowRepository
import com.paliddo.pdmxcontroller.network.ArtNetForegroundService
import com.paliddo.pdmxcontroller.network.ConnectionManager
import com.paliddo.pdmxcontroller.network.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.floor
import com.paliddo.pdmxcontroller.data.repository.DefaultFixtureLibrary

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShowRepository(application)
    private val libraryRepository = FixtureLibraryRepository(application)

    private val _dmxState = MutableStateFlow(ByteArray(512))
    val dmxState: StateFlow<ByteArray> = _dmxState.asStateFlow()

    private val _isControllerConnected = MutableStateFlow(false)
    val isControllerConnected: StateFlow<Boolean> = _isControllerConnected.asStateFlow()

    private val _controllerDetails = MutableStateFlow<ControllerInfo?>(null)
    val controllerDetails: StateFlow<ControllerInfo?> = _controllerDetails.asStateFlow()

    /** Stato connessione avanzato dal ConnectionManager */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentShow = MutableStateFlow(Showfile("Default_Show"))
    val currentShow: StateFlow<Showfile> = _currentShow.asStateFlow()

    private val _activeSceneIndex = MutableStateFlow(0)
    val activeSceneIndex: StateFlow<Int> = _activeSceneIndex.asStateFlow()

    private val _currentCueIndex = MutableStateFlow(-1) // Questo sarà il "Next" o "Selezionato"
    val currentCueIndex: StateFlow<Int> = _currentCueIndex.asStateFlow()

    private val _runningCueIndex = MutableStateFlow(-1) // Questo è quello attualmente in output (o l'ultimo triggerato)
    val runningCueIndex: StateFlow<Int> = _runningCueIndex.asStateFlow()

    private val _isLoopEnabled = MutableStateFlow(false)
    val isLoopEnabled: StateFlow<Boolean> = _isLoopEnabled.asStateFlow()

    private val _isSequenceRunning = MutableStateFlow(false)
    val isSequenceRunning: StateFlow<Boolean> = _isSequenceRunning.asStateFlow()

    // Modalità Single Mode
    private val _isSingleModeEnabled = MutableStateFlow(false)
    val isSingleModeEnabled: StateFlow<Boolean> = _isSingleModeEnabled.asStateFlow()

    // Stato della visualizzazione (True = Live Mode, False = Edit Mode)
    private val _isLiveMode = MutableStateFlow(false)
    val isLiveMode: StateFlow<Boolean> = _isLiveMode.asStateFlow()

    private val _grandMaster = MutableStateFlow(100f)
    val grandMaster: StateFlow<Float> = _grandMaster.asStateFlow()

    private val _availableShows = MutableStateFlow<List<String>>(emptyList())
    val availableShows: StateFlow<List<String>> = _availableShows.asStateFlow()

    // Configurazione Rete
    private val _networkSettings = MutableStateFlow(NetworkSettings())
    val networkSettings: StateFlow<NetworkSettings> = _networkSettings.asStateFlow()

    // Libreria Profili Utente Globali (Richiesta per le nuove implementazioni)
    private val _userFixtureProfiles = MutableStateFlow<List<FixtureProfile>>(emptyList())
    val userFixtureProfiles: StateFlow<List<FixtureProfile>> = _userFixtureProfiles.asStateFlow()

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    private val _connectionLog = MutableStateFlow<List<String>>(emptyList())
    val connectionLog: StateFlow<List<String>> = _connectionLog.asStateFlow()

    private var artNetService: ArtNetForegroundService? = null
    private var isBound = false
    private var networkSendJob: Job? = null
    private var fadeJob: Job? = null
    private var playbackJob: Job? = null

    private var lastSendTime = 0L
    private val dmxFrameRateInterval = 25L
    private var serviceConnectionJob: Job? = null

    // Set in memoria per tracciare gli indirizzi DMX assoluti dei Dimmer (0-indexed)
    private val dimmerAddresses = mutableSetOf<Int>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ArtNetForegroundService.LocalBinder
            val activeService = binder.getService()
            artNetService = activeService
            isBound = true
            
            // Applichiamo le impostazioni attuali appena ci connettiamo al servizio
            val s = _networkSettings.value
            activeService.updateSettings(s.ipAddress, s.port, s.universe, s.autoConnect)
            
            // Quando ci colleghiamo al servizio, salviamo lo show con i profili libreria
            val currentProfiles = _userFixtureProfiles.value
            val currentShowVal = _currentShow.value
            if (currentProfiles.isNotEmpty()) {
                repository.saveShowfileWithLibrary(currentShowVal, currentProfiles)
            }

            _dmxState.value = activeService.dmxData.clone()
            serviceConnectionJob?.cancel()
            serviceConnectionJob = viewModelScope.launch {
                launch { activeService.isControllerAlive.collect { _isControllerConnected.value = it } }
                launch { activeService.controllerInfo.collect { _controllerDetails.value = it } }
                launch { activeService.connectionState.collect { state ->
                    _connectionState.value = state
                    // Aggiungi log solo per stati significativi
                    when (state) {
                        is ConnectionState.Connected -> addConnectionLog("✅ Connesso a ${state.targetIp}")
                        is ConnectionState.Disconnected -> addConnectionLog("❌ Disconnesso: ${state.reason}")
                        is ConnectionState.Error -> addConnectionLog("⚠️ Errore: ${state.message}")
                        is ConnectionState.Handshaking -> addConnectionLog("🔄 Handshake con ${state.targetIp} (tentativo ${state.attempt})")
                        is ConnectionState.DiscoveryFailed -> addConnectionLog("🔍 Nessun controller trovato sulla rete")
                        else -> {}
                    }
                } }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { artNetService = null; isBound = false; _isControllerConnected.value = false }
    }

    init {
        loadNetworkSettings()
        loadGlobalUserProfiles()
        val intent = Intent(application, ArtNetForegroundService::class.java)
        application.startService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        refreshShowList()
        val salvati = repository.getAvailableShows()
        if (salvati.isNotEmpty()) loadShow(salvati.first()) else createNewShow("Default_Show")
    }

    private fun addConnectionLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY).format(java.util.Date())
        val entry = "[$timestamp] $message"
        _connectionLog.value = (_connectionLog.value + entry).takeLast(50) // Mantieni ultimi 50 log
    }

    private fun loadNetworkSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("pdmx_prefs", Context.MODE_PRIVATE)
        val ip = prefs.getString("ip", "192.168.4.1") ?: "192.168.4.1"
        val port = prefs.getInt("port", 6454)
        val uni = prefs.getInt("universe", 0)
        val auto = prefs.getBoolean("auto_connect", true)
        _networkSettings.value = NetworkSettings(ip, port, uni, auto)
    }

    private fun loadGlobalUserProfiles() {
        _userFixtureProfiles.value = libraryRepository.loadUserProfiles()
    }

    fun updateNetworkSettings(newSettings: NetworkSettings) {
        _networkSettings.value = newSettings
        val prefs = getApplication<Application>().getSharedPreferences("pdmx_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("ip", newSettings.ipAddress)
            putInt("port", newSettings.port)
            putInt("universe", newSettings.universe)
            putBoolean("auto_connect", newSettings.autoConnect)
            apply()
        }
        artNetService?.updateSettings(newSettings.ipAddress, newSettings.port, newSettings.universe, newSettings.autoConnect)
    }

    // ==========================================
    // CONTROLLI CONNESSIONE ESPLICITA
    // ==========================================
    fun connectToController() {
        addConnectionLog("🚀 Avvio connessione...")
        artNetService?.connectToController(_networkSettings.value.ipAddress)
    }

    fun connectToControllerAt(ip: String) {
        val newSettings = _networkSettings.value.copy(ipAddress = ip)
        updateNetworkSettings(newSettings)
        addConnectionLog("🚀 Connessione a $ip...")
        artNetService?.connectToController(ip)
    }

    fun disconnectFromController() {
        addConnectionLog("⏹️ Disconnessione in corso...")
        artNetService?.disconnectFromController()
    }

    fun reconnectToController() {
        addConnectionLog("🔄 Riconnessione...")
        artNetService?.connectionManager?.reconnect()
    }

    fun scanForControllers() {
        addConnectionLog("🔍 Scansione rete in corso...")
        artNetService?.connectionManager?.connect() // Discovery automatico
    }

    fun clearConnectionLog() {
        _connectionLog.value = emptyList()
    }

    fun toggleLiveMode() { _isLiveMode.value = !_isLiveMode.value }
    fun toggleSingleMode() { _isSingleModeEnabled.value = !_isSingleModeEnabled.value }

    fun selectCueManually(index: Int) { _currentCueIndex.value = index }

    fun handleGoStopAction() {
        if (_isLoopEnabled.value) {
            if (_isSequenceRunning.value) stopSequence() else startAutomaticLoopSequence()
        } else {
            if (_isSingleModeEnabled.value) {
                if (_currentCueIndex.value != -1) {
                    triggerFadeToCue(_currentCueIndex.value)
                }
            } else {
                executeNextGo()
            }
        }
    }

    private fun executeNextGo() {
        val show = _currentShow.value
        val sceneIndex = _activeSceneIndex.value
        val cueList = show.scenes.getOrNull(sceneIndex)?.cueList ?: return
        if (cueList.isEmpty()) return

        val prossimoIndice = _currentCueIndex.value + 1
        if (prossimoIndice < cueList.size) {
            triggerFadeToCue(prossimoIndice)
        }
    }

    fun triggerFadeToCue(index: Int) {
        stopSequence()
        val show = _currentShow.value
        val sceneIndex = _activeSceneIndex.value
        val cueList = show.scenes.getOrNull(sceneIndex)?.cueList ?: return
        if (index !in cueList.indices) return

        _runningCueIndex.value = index
        _currentCueIndex.value = index // Allineiamo la selezione a quello che sta girando
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch(Dispatchers.Default) {
            val targetCue = cueList[index]
            triggerFadeCoroutine(targetCue)
        }
    }

    private suspend fun triggerFadeCoroutine(targetCue: Cue): Boolean {
        val targetDmx = targetCue.dmxValues
        val duration = targetCue.fadeTimeMs

        // Applica immediatamente il risultato finale
        val finalBuffer = ByteArray(512)
        for (i in 0..511) {
            finalBuffer[i] = targetDmx[i].toByte()
            artNetService?.dmxData?.set(i, targetDmx[i].toByte())
        }

        if (duration <= 0) {
            _dmxState.value = finalBuffer
            return true
        }

        // Fade progressivo
        val startDmx = _dmxState.value.map { it.toInt() and 0xFF }
        val startTime = System.currentTimeMillis()
        var elapsed = 0L
        while (elapsed < duration) {
            elapsed = System.currentTimeMillis() - startTime
            val fraction = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            val buffer = ByteArray(512)
            for (i in 0..511) {
                val valInterp = startDmx[i] + ((targetDmx[i] - startDmx[i]) * fraction)
                val byteF = valInterp.roundToInt().toByte()
                buffer[i] = byteF
                artNetService?.dmxData?.set(i, byteF)
            }
            _dmxState.value = buffer
            delay(dmxFrameRateInterval)
        }

        // Assicura lo stato finale
        _dmxState.value = finalBuffer
        return true
    }

    fun refreshShowList() { _availableShows.value = repository.getAvailableShows() }

    // Unione sicura di Profili Built-In + Profili Utente Globali + Fallback per Showfile vecchi
    fun getAllAvailableProfiles(): List<FixtureProfile> {
        val legacyProfiles = try {
            _currentShow.value.customProfiles ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return DefaultFixtureLibrary.profiles + _userFixtureProfiles.value + legacyProfiles
    }

    fun patchMultipleFixtures(baseName: String, profileId: String, startAddress: Int, quantity: Int) {
        val allProfiles = getAllAvailableProfiles()
        val selectedProfile = allProfiles.find { it.id == profileId } ?: return
        val channelSize = selectedProfile.channelCount
        
        val currentInstances = _currentShow.value.fixtureInstances.toMutableList()
        val currentShowProfiles = _currentShow.value.customProfiles.toMutableList()
        
        // Se è un profilo utente, assicuriamoci che sia salvato anche nello showfile per portabilità
        if (!DefaultFixtureLibrary.profiles.any { it.id == profileId } && 
            !currentShowProfiles.any { it.id == profileId }) {
            currentShowProfiles.add(selectedProfile)
        }

        var currentDmxAddress = startAddress
        if (currentDmxAddress == 1 && currentInstances.isNotEmpty()) {
            val maxOccupied = currentInstances.maxOf { inst ->
                val footprint = allProfiles.find { it.id == inst.profileId }?.channelCount ?: 1
                inst.startAddress + footprint - 1
            }
            currentDmxAddress = (maxOccupied + 1).coerceIn(1, 512)
        }

        for (i in 1..quantity) {
            if (currentDmxAddress + channelSize - 1 > 512) break
            currentInstances.add(FixtureInstance(
                UUID.randomUUID().toString(), 
                if (quantity == 1) baseName else "$baseName ${String.format("%02d", i)}", 
                profileId, 
                currentDmxAddress
            ))
            currentDmxAddress += channelSize
        }

        val updatedShow = _currentShow.value.copy(
            fixtureInstances = currentInstances,
            customProfiles = currentShowProfiles
        )
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
        updateDimmerMap()
    }

    fun createFixtureGroup(name: String, ids: List<String>) {
        val updatedGroups = _currentShow.value.fixtureGroups.toMutableList().apply { add(FixtureGroup(UUID.randomUUID().toString(), name, ids)) }
        val updatedShow = _currentShow.value.copy(fixtureGroups = updatedGroups)
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun deleteFixtureGroup(id: String) {
        val updatedShow = _currentShow.value.copy(fixtureGroups = _currentShow.value.fixtureGroups.filter { it.id != id })
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun deleteFixtureInstance(id: String) {
        val updatedInstances = _currentShow.value.fixtureInstances.filter { it.id != id }
        // Rimuoviamo l'id anche dai gruppi
        val updatedGroups = _currentShow.value.fixtureGroups.map { group ->
            group.copy(fixtureIds = group.fixtureIds.filter { it != id })
        }
        val updatedShow = _currentShow.value.copy(
            fixtureInstances = updatedInstances,
            fixtureGroups = updatedGroups
        )
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
        updateDimmerMap()
    }

    fun deleteScene(index: Int) {
        val show = _currentShow.value
        if (show.scenes.size <= 1) return // Non eliminare l'ultima scena

        val updatedScenes = show.scenes.toMutableList().apply { removeAt(index) }
        val updatedShow = show.copy(scenes = updatedScenes)
        
        // Se la scena eliminata era quella attiva, resettiamo l'indice
        if (_activeSceneIndex.value >= updatedScenes.size) {
            _activeSceneIndex.value = updatedScenes.size - 1
        } else if (_activeSceneIndex.value == index) {
            _activeSceneIndex.value = 0
        } else if (_activeSceneIndex.value > index) {
            _activeSceneIndex.value -= 1
        }
        
        _currentCueIndex.value = -1
        _runningCueIndex.value = -1
        stopSequence()

        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun selectScene(index: Int) { _activeSceneIndex.value = index; _currentCueIndex.value = -1; stopSequence() }

    fun createNewScene(name: String) {
        val updatedShow = _currentShow.value.copy(scenes = _currentShow.value.scenes.toMutableList().apply { add(Scene(name)) })
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun createSceneFromExisting(sourceIndex: Int, newName: String) {
        val sourceScene = _currentShow.value.scenes.getOrNull(sourceIndex) ?: return
        val newScene = sourceScene.copy(name = newName)
        val updatedShow = _currentShow.value.copy(scenes = _currentShow.value.scenes.toMutableList().apply { add(newScene) })
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun updateDmxChannel(ch: Int, v: Byte) {
        if (ch !in 0..511) return
        stopSequence()
        
        // Aggiorniamo lo stato nominale nel ViewModel
        val curr = _dmxState.value.clone()
        curr[ch] = v
        _dmxState.value = curr
        
        // Aggiorniamo IMMEDIATAMENTE il buffer del servizio senza rate-limiting qui.
        // Sarà il loop del servizio a gestire l'invio costante a 25fps.
        artNetService?.dmxData?.set(ch, v)
    }

    fun setGrandMaster(v: Float) {
        _grandMaster.value = v
        artNetService?.grandMasterValue = v
    }

    fun recordCue(num: Float, name: String, fadeSec: Float) {
        val scene = _currentShow.value.scenes[_activeSceneIndex.value]
        val nuovaCue = Cue(num, name, _dmxState.value.map { it.toInt() and 0xFF }, (fadeSec * 1000).toLong())
        val lista = scene.cueList.toMutableList().apply {
            val i = indexOfFirst { it.number == num }; if (i != -1) set(i, nuovaCue) else { add(nuovaCue); sortBy { it.number } }
        }
        val show = _currentShow.value.copy(scenes = _currentShow.value.scenes.toMutableList().apply { set(_activeSceneIndex.value, scene.copy(cueList = lista)) })
        _currentShow.value = show; repository.saveShowfile(show)
    }

    fun duplicateCue(cue: Cue) { recordCue(cue.number + 1, "${cue.name} (Copy)", cue.fadeTimeMs / 1000f) }

    fun deleteCue(i: Int) {
        val scene = _currentShow.value.scenes[_activeSceneIndex.value]
        val lista = scene.cueList.toMutableList().apply { removeAt(i) }
        val show = _currentShow.value.copy(scenes = _currentShow.value.scenes.toMutableList().apply { set(_activeSceneIndex.value, scene.copy(cueList = lista)) })
        _currentShow.value = show; repository.saveShowfile(show)
    }

    fun toggleLoop() { _isLoopEnabled.value = !_isLoopEnabled.value; if (!_isLoopEnabled.value) stopSequence() }

    private fun stopSequence() { _isSequenceRunning.value = false; playbackJob?.cancel(); fadeJob?.cancel() }

    private fun startAutomaticLoopSequence() {
        _isSequenceRunning.value = true
        playbackJob = viewModelScope.launch(Dispatchers.Default) {
            val list = _currentShow.value.scenes[_activeSceneIndex.value].cueList
            while (_isSequenceRunning.value && list.isNotEmpty()) {
                val next = (_currentCueIndex.value + 1) % list.size
                _currentCueIndex.value = next
                _runningCueIndex.value = next
                triggerFadeCoroutine(list[next])
                delay(1000)
            }
        }
    }

    fun loadShow(n: String) {
        repository.loadShowfile(n)?.let {
            _currentShow.value = it
            _activeSceneIndex.value = 0
            _currentCueIndex.value = -1
            _runningCueIndex.value = -1
            updateDimmerMap()
        }
    }

    fun deleteShow(showName: String) {
        if (repository.deleteShowfile(showName)) {
            refreshShowList()
            // Se abbiamo eliminato lo show corrente, carichiamo il primo disponibile o ne creiamo uno nuovo
            if (_currentShow.value.showName == showName) {
                val nextShow = availableShows.value.firstOrNull()
                if (nextShow != null) loadShow(nextShow) else createNewShow("Default_Show")
            }
        }
    }

    fun createNewShow(n: String) {
        val s = Showfile(n)
        _currentShow.value = s
        repository.saveShowfile(s)
        refreshShowList()
        _activeSceneIndex.value = 0
        _currentCueIndex.value = -1
        _runningCueIndex.value = -1
        updateDimmerMap()
    }

    fun copyShow(sourceName: String, newName: String) {
        repository.loadShowfile(sourceName)?.let { source ->
            val newShow = source.copy(showName = newName)
            _currentShow.value = newShow
            repository.saveShowfile(newShow)
            refreshShowList()
            _activeSceneIndex.value = 0
            _currentCueIndex.value = -1
            _runningCueIndex.value = -1
            updateDimmerMap()
        }
    }

    private fun Double.roundToInt() = kotlin.math.round(this).toInt()
    private fun Float.roundToInt() = kotlin.math.round(this).toInt()

    // ==========================================
    // LOGICHE DI GESTIONE DEI DIMMER COMPATIBILI
    // ==========================================
    fun updateDimmerMap() {
        synchronized(dimmerAddresses) {
            dimmerAddresses.clear()
            val show = _currentShow.value
            val allProfiles = getAllAvailableProfiles()

            for (fixture in show.fixtureInstances) {
                val profile = allProfiles.find { it.id == fixture.profileId } ?: continue
                val channels = profile.channels ?: emptyList()
                
                // Se la fixture ha un canale DIMMER dedicato, scala quello
                val hasDimmer = channels.any { it.type == ChannelType.DIMMER }
                // Se non ha dimmer ma ha canali colore, scala i canali colore
                val hasColor = channels.any { it.type in listOf(ChannelType.COLOR_R, ChannelType.COLOR_G, ChannelType.COLOR_B, ChannelType.COLOR_W) }
                
                val targetTypes = if (hasDimmer) {
                    listOf(ChannelType.DIMMER)
                } else if (hasColor) {
                    listOf(ChannelType.COLOR_R, ChannelType.COLOR_G, ChannelType.COLOR_B, ChannelType.COLOR_W)
                } else {
                    emptyList()
                }
                
                for (channel in channels) {
                    if (channel.type in targetTypes) {
                        val absoluteAddress = (fixture.startAddress - 1) + channel.offset
                        if (absoluteAddress in 0..511) {
                            dimmerAddresses.add(absoluteAddress)
                        }
                    }
                }
            }
            // Sincronizziamo la mappa con il servizio foreground
            artNetService?.syncDimmerMap(dimmerAddresses)
        }
    }

    fun getOutputDmxData(grandMasterValue: Float): ByteArray {
        val nominalValues = _dmxState.value
        val finalOutput = nominalValues.copyOf()
        val multiplier = grandMasterValue / 100f

        synchronized(dimmerAddresses) {
            for (address in dimmerAddresses) {
                if (address in finalOutput.indices) {
                    val nominalByte = finalOutput[address].toInt() and 0xFF
                    finalOutput[address] = (nominalByte * multiplier).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return finalOutput
    }

    fun addUserFixtureProfile(profile: FixtureProfile) {
        val updatedList = _userFixtureProfiles.value.filter { it.id != profile.id } + profile
        _userFixtureProfiles.value = updatedList
        libraryRepository.saveUserProfiles(updatedList)
        // Salva lo show corrente con i profili aggiornati
        repository.saveShowfileWithLibrary(_currentShow.value, updatedList)
        updateDimmerMap()
    }

    fun removeUserFixtureProfile(profileId: String) {
        val updatedList = _userFixtureProfiles.value.filter { it.id != profileId }
        _userFixtureProfiles.value = updatedList
        libraryRepository.saveUserProfiles(updatedList)
        repository.saveShowfileWithLibrary(_currentShow.value, updatedList)
        updateDimmerMap()
    }

    // ==========================================
    // EXPORT / IMPORT LIBRERIA FIXTURE
    // ==========================================
    fun exportFixtureLibrary(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profiles = _userFixtureProfiles.value
                val success = libraryRepository.exportLibraryToUri(profiles, uri)
                _backupStatus.value = if (success) {
                    "Libreria esportata (${profiles.size} profili)"
                } else {
                    "Errore durante l'esportazione della libreria"
                }
            } catch (e: Exception) {
                _backupStatus.value = "Errore export libreria: ${e.message}"
            }
        }
    }

    fun importFixtureLibrary(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val importedProfiles = libraryRepository.importLibraryFromUri(uri)
                if (importedProfiles != null) {
                    // Unisci con i profili esistenti (sovrascrive duplicati per id)
                    val existingIds = _userFixtureProfiles.value.map { it.id }.toSet()
                    val newProfiles = importedProfiles.filter { it.id !in existingIds }
                    val updatedList = _userFixtureProfiles.value + newProfiles
                    _userFixtureProfiles.value = updatedList
                    libraryRepository.saveUserProfiles(updatedList)
                    repository.saveShowfileWithLibrary(_currentShow.value, updatedList)
                    _backupStatus.value = "Importati ${newProfiles.size} nuovi profili (${importedProfiles.size - newProfiles.size} già presenti)"
                } else {
                    _backupStatus.value = "File non valido o vuoto"
                }
            } catch (e: Exception) {
                _backupStatus.value = "Errore import libreria: ${e.message}"
            }
        }
    }

    fun applyColorToSelected(fixtureIds: List<String>, r: Int, g: Int, b: Int) {
        val show = _currentShow.value
        val allProfiles = getAllAvailableProfiles()
        
        fixtureIds.forEach { fid ->
            val inst = show.fixtureInstances.find { it.id == fid } ?: return@forEach
            val profile = allProfiles.find { it.id == inst.profileId } ?: return@forEach
            
            // Controlla se questo profilo ha un canale WHITE dedicato
            val hasWhiteChannel = profile.channels.any { it.type == ChannelType.COLOR_W }
            
            // Calcolo del bianco: se esiste canale W, lo sottraiamo da R, G, B
            val w = if (hasWhiteChannel) minOf(r, minOf(g, b)) else 0
            val rAdjusted = (r - w).coerceIn(0, 255)
            val gAdjusted = (g - w).coerceIn(0, 255)
            val bAdjusted = (b - w).coerceIn(0, 255)
            
            profile.channels.forEach { ch ->
                val addr = inst.startAddress - 1 + ch.offset
                when (ch.type) {
                    ChannelType.COLOR_R -> updateDmxChannel(addr, rAdjusted.toByte())
                    ChannelType.COLOR_G -> updateDmxChannel(addr, gAdjusted.toByte())
                    ChannelType.COLOR_B -> updateDmxChannel(addr, bAdjusted.toByte())
                    ChannelType.COLOR_W -> updateDmxChannel(addr, w.toByte())
                    else -> {}
                }
            }
        }
    }

    fun saveColorPalette(name: String, hex: String, r: Int, g: Int, b: Int) {
        val w = minOf(r, minOf(g, b))
        val newPalette = ColorPalette(UUID.randomUUID().toString(), name, hex, r, g, b, w)
        val updatedPalettes = _currentShow.value.colorPalettes + newPalette
        val updatedShow = _currentShow.value.copy(colorPalettes = updatedPalettes)
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun deleteColorPalette(id: String) {
        val updatedPalettes = _currentShow.value.colorPalettes.filter { it.id != id }
        val updatedShow = _currentShow.value.copy(colorPalettes = updatedPalettes)
        _currentShow.value = updatedShow
        repository.saveShowfile(updatedShow)
    }

    fun exportShow(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Includi i profili della libreria globale usati dalle fixture dello show
                val json = repository.serializeShow(_currentShow.value, _userFixtureProfiles.value)
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { 
                    it.write(json.toByteArray())
                }
                _backupStatus.value = "Esportazione completata con successo"
            } catch (e: Exception) {
                _backupStatus.value = "Errore durante l'esportazione: ${e.message}"
            }
        }
    }

    fun importShow(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    repository.deserializeShow(json)?.let { importedShow ->
                        val existing = repository.getAvailableShows()
                        var finalName = importedShow.showName
                        if (existing.contains(finalName)) {
                            finalName = "${finalName}_Imported_${System.currentTimeMillis() % 1000}"
                        }
                        val showToSave = importedShow.copy(showName = finalName)
                        repository.saveShowfile(showToSave)
                        refreshShowList()
                        _backupStatus.value = "Show '$finalName' importato correttamente"
                    } ?: run {
                        _backupStatus.value = "File non valido o corrotto"
                    }
                }
            } catch (e: Exception) {
                _backupStatus.value = "Errore durante l'importazione: ${e.message}"
            }
        }
    }

    fun saveCurrentShow() {
        val show = _currentShow.value
        val profiles = _userFixtureProfiles.value
        repository.saveShowfileWithLibrary(show, profiles)
        _backupStatus.value = "Show '${show.showName}' salvato"
        addConnectionLog("💾 Show salvato: ${show.showName}")
    }

    override fun onCleared() {
        super.onCleared()
        // Salva automaticamente lo show alla chiusura dell'app
        val show = _currentShow.value
        val profiles = _userFixtureProfiles.value
        repository.saveShowfileWithLibrary(show, profiles)
    }

    fun clearBackupStatus() { _backupStatus.value = null }
}
