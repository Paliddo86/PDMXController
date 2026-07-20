package com.paliddo.pdmxcontroller.network

/**
 * State machine della connessione con il controller ESP32 P-DMX.
 * Ogni stato rappresenta una fase del processo di connessione.
 */
sealed class ConnectionState {
    /** Stato iniziale: nessuna connessione avviata */
    data object Idle : ConnectionState()

    /** In scansione per trovare il controller sulla rete */
    data object Scanning : ConnectionState()

    /** Indirizzo IP individuato, tentativo di handshake in corso */
    data class Connecting(val targetIp: String, val attempt: Int, val maxAttempts: Int) : ConnectionState()

    /** Handshake "P-DMX:START" inviato, in attesa di risposta "P-DMX:OK" */
    data class Handshaking(val targetIp: String, val attempt: Int) : ConnectionState()

    /** Connessione stabilita e attiva, streaming DMX in corso */
    data class Connected(
        val targetIp: String,
        val deviceName: String = "P-DMX Controller",
        val firmwareVersion: String = "",
        val developer: String = ""
    ) : ConnectionState()

    /** Connessione persa o fallita */
    data class Disconnected(val reason: String = "", val lastState: ConnectionState? = null) : ConnectionState()

    /** Errore di rete o timeout */
    data class Error(val message: String, val lastState: ConnectionState? = null) : ConnectionState()

    /** Discovery fallito: nessun controller trovato sulla rete */
    data object DiscoveryFailed : ConnectionState()

    // Helper properties
    val isActive: Boolean get() = this is Connected
    val isBusy: Boolean get() = this is Scanning || this is Connecting || this is Handshaking
    val ip: String get() = when (this) {
        is Connecting -> targetIp
        is Handshaking -> targetIp
        is Connected -> targetIp
        else -> ""
    }

    val description: String get() = when (this) {
        is Idle -> "Inattivo"
        is Scanning -> "Scansione rete in corso..."
        is Connecting -> "Connessione a $targetIp (tentativo $attempt/$maxAttempts)..."
        is Handshaking -> "Handshake con $targetIp (tentativo $attempt)..."
        is Connected -> "Connesso a $targetIp"
        is Disconnected -> when {
            reason.isNotEmpty() -> "Disconnesso: $reason"
            else -> "Disconnesso"
        }
        is Error -> "Errore: $message"
        is DiscoveryFailed -> "Nessun controller trovato"
    }
}