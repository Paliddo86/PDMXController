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

class ArtNetForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var artNetService = ArtNetService()

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null

    val dmxData = ByteArray(512)
    
    // Configurazione dinamica
    private var targetIp: String = "192.168.4.1"
    private var port: Int = 6454
    private var universe: Int = 0
    private var isConnectionEnabled: Boolean = true

    // Stato di handshake reale esposto al ViewModel
    val isControllerAlive = MutableStateFlow(false)
    val controllerInfo = MutableStateFlow<com.paliddo.pdmxcontroller.data.model.ControllerInfo?>(null)

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
            startNetworkLoop() 
        } else {
            isConnectionEnabled = enabled
            if (ipChanged && isConnectionEnabled) {
                connectionJob?.cancel()
                startNetworkLoop()
            }
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
        startNetworkLoop()
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

    fun sendUpdate(channelIndex: Int, value: Byte) {
        dmxData[channelIndex] = value
        if (isConnectionEnabled) {
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
            
            // --- JOB 1: STREAMING DMX E WAKE-UP (Priorità Alta) ---
            val dmxJob = launch {
                // Wake-up iniziale
                repeat(10) {
                    artNetService.sendWakeUpHandshake(targetIp, port)
                    delay(100)
                }
                
                while (isActive && isConnectionEnabled) {
                    artNetService.sendArtDmx(targetIp, universe, dmxData, port)
                    delay(40) 
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
        serviceScope.cancel()
        artNetService.close()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }
}
