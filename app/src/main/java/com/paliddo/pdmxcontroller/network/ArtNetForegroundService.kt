package com.paliddo.pdmxcontroller.network

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArtNetForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var artNetService = ArtNetService()

    /** ConnectionManager per la state machine della connessione */
    val connectionManager = ConnectionManager(serviceScope)

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null

    val dmxData = ByteArray(512)
    
    // Configurazione dinamica
    private var targetIp: String = "192.168.4.1"
    private var port: Int = 6454
    private var universe: Int = 0
    private var isConnectionEnabled: Boolean = true
    var grandMasterValue: Float = 100f
    var isBlackoutMode: Boolean = false

    // Indirizzi DMX assoluti dei Dimmer per applicazione Master
    private val dimmerAddresses = mutableSetOf<Int>()

    // Stato di handshake reale esposto al ViewModel
    val isControllerAlive = MutableStateFlow(false)
    val controllerInfo = MutableStateFlow<com.paliddo.pdmxcontroller.data.model.ControllerInfo?>(null)
    
    /** Espone lo stato della connessione dal ConnectionManager */
    val connectionState: StateFlow<ConnectionState> get() = connectionManager.connectionState

    inner class LocalBinder : Binder() {
        fun getService(): ArtNetForegroundService = this@ArtNetForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun updateSettings(ip: String, p: Int, uni: Int, enabled: Boolean) {
        val ipChanged = targetIp != ip
        targetIp = ip
        port = p
        universe = uni
        
        if (!isConnectionEnabled && enabled) {
            isConnectionEnabled = enabled
            // Se lo stato è Idle o Disconnected, avvia connessione tramite ConnectionManager
            val state = connectionManager.connectionState.value
            if (state is ConnectionState.Idle || state is ConnectionState.Disconnected || state is ConnectionState.DiscoveryFailed || state is ConnectionState.Error) {
                connectionManager.connect(ip)
            }
            startNetworkLoop() 
        } else {
            isConnectionEnabled = enabled
            if (!enabled) {
                // Disconnessione esplicita
                connectionManager.disconnect()
                connectionJob?.cancel()
                isControllerAlive.value = false
            } else if (ipChanged && isConnectionEnabled) {
                connectionJob?.cancel()
                connectionManager.connect(ip)
                startNetworkLoop()
            }
        }
    }

    fun syncDimmerMap(addresses: Set<Int>) {
        synchronized(dimmerAddresses) {
            dimmerAddresses.clear()
            dimmerAddresses.addAll(addresses)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PDMXController::LiveWakeLock").apply {
            acquire()
        }

        setupNetworkBinding()
        
        // Colleghiamo il ConnectionManager allo stato del controller
        connectionManager.onStateChanged = { state ->
            when (state) {
                is ConnectionState.Connected -> {
                    isControllerAlive.value = true
                    // Avvia lo streaming DMX se non già attivo
                    if (connectionJob?.isActive != true) {
                        startNetworkLoop()
                    }
                }
                is ConnectionState.Disconnected -> {
                    isControllerAlive.value = false
                    controllerInfo.value = null
                }
                is ConnectionState.Error -> {
                    isControllerAlive.value = false
                    controllerInfo.value = null
                }
                else -> {}
            }
        }
        
        // Avvia connessione automaticamente se abilitato
        if (isConnectionEnabled) {
            connectionManager.connect(targetIp)
            startNetworkLoop()
        }
    }

    /**
     * Forza Android a usare l'interfaccia WiFi anche se non c'è internet.
     * Risolve il problema dell'instradamento verso il 4G/5G.
     */
    private fun setupNetworkBinding() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("ArtNetService", "Rete WiFi rilevata. Binding del processo...")
                connectivityManager?.bindProcessToNetwork(network)
                
                // IMPORTANTE: Ricreiamo il servizio ArtNet dopo il binding
                // così il nuovo socket "eredita" la rete corretta
                serviceScope.launch {
                    artNetService.close()
                    delay(200)
                    artNetService = ArtNetService()
                    
                    Log.d("ArtNetService", "Socket ricreato su interfaccia WiFi. Invio Wake-up...")
                    
                    // Invia subito lo sblocco con più insistenza
                    repeat(10) {
                        artNetService.sendWakeUpHandshake(targetIp, port)
                        delay(100)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.w("ArtNetService", "Connessione WiFi persa.")
                connectivityManager?.bindProcessToNetwork(null)
                isControllerAlive.value = false
            }
        })
    }

    fun connectToController(ip: String = targetIp) {
        isConnectionEnabled = true
        connectionManager.connect(ip)
    }

    fun disconnectFromController() {
        isConnectionEnabled = false
        connectionManager.disconnect()
        connectionJob?.cancel()
        isControllerAlive.value = false
        controllerInfo.value = null
    }

    fun sendUpdate(channelIndex: Int, value: Byte) {
        dmxData[channelIndex] = value
        if (isConnectionEnabled && connectionManager.connectionState.value is ConnectionState.Connected) {
            serviceScope.launch {
                artNetService.sendArtDmx(targetIp, universe, dmxData, port)
            }
        }
    }

    private fun startNetworkLoop() {
        connectionJob?.cancel()
        if (!isConnectionEnabled) {
            isControllerAlive.value = false
            return
        }

        connectionJob = serviceScope.launch {
            Log.d("ArtNetService", "Inizio loop di rete verso $targetIp:$port")
            
            // --- JOB 1: STREAMING DMX (Priorità Alta) ---
            val dmxJob = launch {
                // Aspetta che il ConnectionManager stabilisca la connessione
                while (isActive && isConnectionEnabled) {
                    if (connectionManager.connectionState.value is ConnectionState.Connected) {
                        break
                    }
                    delay(200)
                }
                
                while (isActive && isConnectionEnabled) {
                    // BLACKOUT: forza tutti i 512 canali a 0
                    if (isBlackoutMode) {
                        artNetService.sendArtDmx(targetIp, universe, ByteArray(512), port)
                    } else {
                        // Applichiamo il Grand Master ai canali dimmer prima di inviare
                        val outputData = dmxData.copyOf()
                        val multiplier = grandMasterValue / 100f
                        
                        synchronized(dimmerAddresses) {
                            for (addr in dimmerAddresses) {
                                if (addr in outputData.indices) {
                                    val nominalValue = outputData[addr].toInt() and 0xFF
                                    outputData[addr] = (nominalValue * multiplier).toInt().toByte()
                                }
                            }
                        }
                        
                        artNetService.sendArtDmx(targetIp, universe, outputData, port)
                    }
                    delay(33) // Stream stabile a ~30Hz (1000ms / 33ms = ~30fps)
                }
            }

            // --- JOB 2: MONITORAGGIO STATO (Priorità Bassa) ---
            val monitoringJob = launch {
                while (isActive && isConnectionEnabled) {
                    val info = artNetService.checkControllerPing(targetIp, port)
                    if (info != null) {
                        Log.d("ArtNetService", "Handshake riuscito: ${info.shortName}")
                        isControllerAlive.value = true
                        controllerInfo.value = info
                    } else {
                        isControllerAlive.value = false
                        controllerInfo.value = null
                    }
                    delay(2500) // Controllo meno frequente per non saturare
                }
            }
            
            joinAll(dmxJob, monitoringJob)
            isControllerAlive.value = false
            controllerInfo.value = null
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "pdmx_channel")
            .setContentTitle("PDMX Regia Attiva")
            .setContentText("Monitoraggio e streaming DMX attivi...")
            .setSmallIcon(android.R.drawable.presence_online)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pdmx_channel", "PDMX Streaming Core",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionJob?.cancel()
        connectionManager.destroy()
        serviceScope.cancel()
        artNetService.close()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
