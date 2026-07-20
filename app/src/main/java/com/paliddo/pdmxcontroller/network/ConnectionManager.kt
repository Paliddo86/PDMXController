package com.paliddo.pdmxcontroller.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext

/**
 * Gestisce la state machine di connessione con il controller ESP32 P-DMX.
 *
 * Usa un supervisorScope interno per evitare che la cancellazione di un job
 * propaghi eccezioni al padre.
 */
class ConnectionManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val DEFAULT_PORT = 6454
        private const val HANDSHAKE_TIMEOUT_MS = 3000L
        private const val KEEPALIVE_INTERVAL_MS = 2500L
        private const val MAX_HANDSHAKE_RETRIES = 5
        private const val SCAN_TIMEOUT_MS = 4000L
        private const val PDMX_SUBNET = "192.168.4"
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var mainJob: Job? = null
    private var keepAliveJob: Job? = null
    private var socket: DatagramSocket? = null
    private var currentTargetIp: String = ""

    /** Usiamo un lock per prevenire chiamate concorrenti a connect/disconnect */
    private val opLock = Any()

    // Callback per notificare quando lo stato cambia
    var onStateChanged: ((ConnectionState) -> Unit)? = null

    private fun setState(newState: ConnectionState) {
        _connectionState.value = newState
        // Notifica SOLO se siamo ancora nel contesto attivo
        try {
            onStateChanged?.invoke(newState)
        } catch (_: Exception) {
            // Ignora eccezioni nel callback
        }
    }

    // =============================================
    // API PUBBLICHE
    // =============================================

    /**
     * Avvia la connessione. Se [targetIp] è vuoto, fa discovery.
     */
    fun connect(targetIp: String = "") {
        synchronized(opLock) {
            // Se già connesso o in corso, ignora
            val currentState = _connectionState.value
            if (currentState.isActive) {
                Log.d(TAG, "Già connesso a ${currentState.ip}, ignoro")
                return
            }
            if (currentState.isBusy) {
                Log.d(TAG, "Connessione già in corso, ignoro")
                return
            }

            // Cancella job precedenti solo se sono stati completati o falliti
            mainJob?.cancel()
            mainJob = null
            keepAliveJob?.cancel()
            keepAliveJob = null
        }

        mainJob = scope.launch {
            Log.d(TAG, "Avvio connessione targetIp='$targetIp'")

            // Stato iniziale
            if (targetIp.isNotBlank()) {
                currentTargetIp = targetIp
                setState(ConnectionState.Connecting(targetIp, 1, 1))
            } else {
                setState(ConnectionState.Scanning)
            }

            try {
                // Crea un nuovo socket in un contesto non cancellabile per la creazione
                val newSocket = withContext(NonCancellable) {
                    val s = DatagramSocket()
                    s.soTimeout = 800
                    s
                }
                socket?.close()
                socket = newSocket

                if (targetIp.isNotBlank()) {
                    executeConnectSequence(targetIp)
                } else {
                    executeDiscovery()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Connessione cancellata: ${e.message}")
                // Non propagare - la cancellazione è intenzionale
            } catch (e: Exception) {
                Log.e(TAG, "Errore connessione: ${e.message}")
                setState(ConnectionState.Error(
                    message = e.message ?: "Errore sconosciuto",
                    lastState = _connectionState.value
                ))
            }
        }
    }

    fun disconnect() {
        synchronized(opLock) {
            mainJob?.cancel()
            mainJob = null
            keepAliveJob?.cancel()
            keepAliveJob = null
        }

        // Chiudi socket in contesto non cancellabile
        scope.launch(NonCancellable) {
            socket?.close()
            socket = null
        }
        currentTargetIp = ""
        setState(ConnectionState.Disconnected("Disconnessione manuale"))
    }

    fun reconnect() {
        val lastIp = when (val state = _connectionState.value) {
            is ConnectionState.Connecting -> state.targetIp
            is ConnectionState.Handshaking -> state.targetIp
            is ConnectionState.Connected -> state.targetIp
            is ConnectionState.Disconnected -> state.lastState?.ip ?: ""
            is ConnectionState.Error -> state.lastState?.ip ?: ""
            else -> ""
        }
        val ip = if (lastIp.isNotBlank()) lastIp else currentTargetIp
        if (ip.isNotBlank()) {
            connect(ip)
        } else {
            connect()
        }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    // =============================================
    // SEQUENZA DI CONNESSIONE
    // =============================================

    private suspend fun executeConnectSequence(ip: String) {
        Log.d(TAG, "Sequenza connessione verso $ip:$DEFAULT_PORT")

        // Handshake loop
        for (attempt in 1..MAX_HANDSHAKE_RETRIES) {
            if (!isActive()) return

            setState(ConnectionState.Handshaking(ip, attempt))
            Log.d(TAG, "Handshake tentativo $attempt/$MAX_HANDSHAKE_RETRIES")

            // Invia "P-DMX:START"
            sendHandshake(ip)

            // Aspetta risposta "P-DMX:OK"
            val response = waitForHandshakeResponse(ip, HANDSHAKE_TIMEOUT_MS)

            if (response != null) {
                Log.d(TAG, "Handshake riuscito con $ip: $response")
                // Formato: P-DMX:OK|device_name|firmware_version|mac_address|developer
                val parts = response.split('|')
                val deviceName = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "P-DMX"
                val firmwareVersion = if (parts.size > 2 && parts[2].isNotBlank()) parts[2] else ""
                val developer = if (parts.size > 4 && parts[4].isNotBlank()) parts[4] else ""
                setState(ConnectionState.Connected(
                    targetIp = ip,
                    deviceName = deviceName,
                    firmwareVersion = firmwareVersion,
                    developer = developer
                ))
                startKeepAlive(ip)
                return
            }

            Log.w(TAG, "Handshake fallito, tentativo $attempt")
            if (!isActive()) return
        }

        // Tutti i tentativi falliti
        Log.e(TAG, "Connessione fallita dopo $MAX_HANDSHAKE_RETRIES tentativi")
        setState(ConnectionState.Disconnected(
            reason = "Timeout handshake dopo $MAX_HANDSHAKE_RETRIES tentativi"
        ))
    }

    // =============================================
    // DISCOVERY
    // =============================================

    private suspend fun executeDiscovery() {
        Log.d(TAG, "Discovery P-DMX sulla subnet $PDMX_SUBNET.x")

        val foundIp = scanBroadcast()

        if (foundIp != null) {
            Log.d(TAG, "Controller trovato a $foundIp")
            currentTargetIp = foundIp
            setState(ConnectionState.Connecting(foundIp, 1, 1))
            executeConnectSequence(foundIp)
        } else {
            Log.e(TAG, "Nessun controller P-DMX trovato")
            setState(ConnectionState.DiscoveryFailed)
        }
    }

    /**
     * Invia broadcast "P-DMX:START" e ascolta risposte.
     */
    private suspend fun scanBroadcast(): String? {
        return try {
            withContext(Dispatchers.IO) {
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val payload = "P-DMX:START".toByteArray(Charsets.US_ASCII)

                // Invia broadcast
                val sendPacket = DatagramPacket(payload, payload.size, broadcastAddr, DEFAULT_PORT)
                socket?.send(sendPacket)

                // Invia anche subnet broadcast
                val subnetBroadcast = InetAddress.getByName("$PDMX_SUBNET.255")
                val subnetPacket = DatagramPacket(payload, payload.size, subnetBroadcast, DEFAULT_PORT)
                socket?.send(subnetPacket)

                // Ascolta risposte
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < SCAN_TIMEOUT_MS) {
                    if (!isActive()) return@withContext null
                    try {
                        val buf = ByteArray(256)
                        val packet = DatagramPacket(buf, buf.size)
                        socket?.receive(packet)

                        val response = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                        Log.d(TAG, "Broadcast risposta da ${packet.address.hostAddress}: '$response'")

                        if (response == "P-DMX:OK" || response.startsWith("P-DMX:")) {
                            return@withContext packet.address.hostAddress
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout normale
                    }
                }
                null
            }
        } catch (e: CancellationException) {
            throw e // Rilancia per gestione esterna
        } catch (e: Exception) {
            Log.e(TAG, "Errore scan: ${e.message}")
            null
        }
    }

    // =============================================
    // HANDSHAKE
    // =============================================

    private suspend fun sendHandshake(ip: String) {
        try {
            withContext(Dispatchers.IO) {
                val address = InetAddress.getByName(ip)
                val payload = "P-DMX:START".toByteArray(Charsets.US_ASCII)
                val packet = DatagramPacket(payload, payload.size, address, DEFAULT_PORT)

                repeat(3) {
                    if (!isActive()) return@repeat
                    socket?.send(packet)
                    delay(50)
                }
            }
            Log.d(TAG, "Handshake inviato a $ip")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Errore invio handshake: ${e.message}")
        }
    }

    private suspend fun waitForHandshakeResponse(ip: String, timeoutMs: Long): String? {
        return try {
            withContext(Dispatchers.IO) {
                val targetAddress = try {
                    InetAddress.getByName(ip)
                } catch (e: UnknownHostException) {
                    return@withContext null
                }

                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (!isActive()) return@withContext null
                    try {
                        val buf = ByteArray(256)
                        val packet = DatagramPacket(buf, buf.size)
                        socket?.receive(packet)

                        // Verifica IP mittente
                        if (packet.address.hostAddress == ip ||
                            packet.address.hostAddress == targetAddress.hostAddress) {
                            val response = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                            Log.d(TAG, "Risposta handshake da $ip: '$response'")

                            // Accetta sia "P-DMX:OK" che qualsiasi risposta che inizia con "P-DMX:"
                            if (response == "P-DMX:OK" || response.startsWith("P-DMX:")) {
                                return@withContext response
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout, continua
                    }
                }
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Errore wait handshake: ${e.message}")
            null
        }
    }

    // =============================================
    // KEEPALIVE
    // =============================================

    private fun startKeepAlive(ip: String) {
        synchronized(opLock) {
            keepAliveJob?.cancel()
            keepAliveJob = scope.launch {
                while (isActive) {
                    delay(KEEPALIVE_INTERVAL_MS)

                    // Se non siamo più connessi, esci
                    if (_connectionState.value !is ConnectionState.Connected) break

                    try {
                        withContext(Dispatchers.IO) {
                            // Invia "P-DMX:START" come keepalive ping
                            val payload = "P-DMX:START".toByteArray(Charsets.US_ASCII)
                            val address = InetAddress.getByName(ip)
                            val packet = DatagramPacket(payload, payload.size, address, DEFAULT_PORT)
                            socket?.send(packet)

                            // Aspetta risposta breve
                            val startTime = System.currentTimeMillis()
                            var received = false
                            while (System.currentTimeMillis() - startTime < 800) {
                                if (!isActive()) return@withContext
                                try {
                                    val buf = ByteArray(256)
                                    val p = DatagramPacket(buf, buf.size)
                                    socket?.receive(p)

                                    if (p.address.hostAddress == ip) {
                                        val response = String(p.data, 0, p.length, Charsets.US_ASCII)
                                        if (response.startsWith("P-DMX:") || response.startsWith("Art-Net")) {
                                            received = true
                                            break
                                        }
                                    }
                                } catch (_: SocketTimeoutException) {
                                    break
                                }
                            }

                            if (!received) {
                                Log.w(TAG, "Keepalive fallito per $ip")
                                throw RuntimeException("Controller non risponde")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Keepalive: controller $ip perso")
                        setState(ConnectionState.Disconnected(
                            reason = "Keepalive fallito: ${e.message}",
                            lastState = ConnectionState.Connected(ip)
                        ))
                        break
                    }
                }
            }
        }
    }

    // =============================================
    // UTILITY
    // =============================================

    private fun isActive(): Boolean {
        return scope.isActive && (mainJob?.isActive != false || keepAliveJob?.isActive != false)
    }
}