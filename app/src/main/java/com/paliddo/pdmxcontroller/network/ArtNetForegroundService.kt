package com.paliddo.pdmxcontroller.network

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class ArtNetForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val artNetService = ArtNetService()

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectionJob: Job? = null

    val dmxData = ByteArray(512)
    var targetIp: String = "192.168.4.1"
    var universe: Int = 0

    // Stato di handshake reale esposto al ViewModel
    val isControllerAlive = MutableStateFlow(false)

    inner class LocalBinder : Binder() {
        fun getService(): ArtNetForegroundService = this@ArtNetForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PDMXController::LiveWakeLock").apply {
            acquire()
        }

        startNetworkLoop()
    }

    fun sendUpdate(channelIndex: Int, value: Byte) {
        dmxData[channelIndex] = value
        artNetService.sendArtDmx(targetIp, universe, dmxData)
    }

    private fun startNetworkLoop() {
        connectionJob = serviceScope.launch {
            while (isActive) {
                // 1. Invia lo stream dmx di allineamento
                artNetService.sendArtDmx(targetIp, universe, dmxData)

                // 2. Esegue l'handshake reale (Ping-Pong UDP)
                val isAlive = artNetService.checkControllerPing(targetIp)
                isControllerAlive.value = isAlive

                delay(1500) // Ripete ogni 1.5 secondi
            }
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