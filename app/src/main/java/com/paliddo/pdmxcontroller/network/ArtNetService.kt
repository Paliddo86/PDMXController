package com.paliddo.pdmxcontroller.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ArtNetService {
    private var socket: DatagramSocket? = null
    private val buffer = ByteArray(542)
    private val pollBuffer = ByteArray(14)

    init {
        try {
            socket = DatagramSocket()
            socket?.soTimeout = 1000 // Timeout di 1 secondo sulla ricezione per non bloccare il thread
            setupArtNetHeader()
            setupPollHeader()
        } catch (e: Exception) {
            Log.e("ArtNetService", "Errore nell'apertura del socket UDP", e)
        }
    }

    private fun setupArtNetHeader() {
        System.arraycopy("Art-Net".toByteArray(), 0, buffer, 0, 8)
        buffer[8] = 0x00  // OpCode LSB (ArtDmx = 0x5000)
        buffer[9] = 0x50  // OpCode MSB
        buffer[10] = 0x00 // Protocol Version MSB
        buffer[11] = 14   // Protocol Version LSB
        buffer[12] = 0x00 // Sequence
        buffer[13] = 0x00 // Physical port
    }

    private fun setupPollHeader() {
        System.arraycopy("Art-Net".toByteArray(), 0, pollBuffer, 0, 8)
        pollBuffer[8] = 0x00  // OpCode LSB (ArtPoll = 0x2000)
        pollBuffer[9] = 0x20  // OpCode MSB
        pollBuffer[10] = 0x00 // Protocol Version MSB
        pollBuffer[11] = 14   // Protocol Version LSB
        pollBuffer[12] = 0x00 // TalkToMe (0)
        pollBuffer[13] = 0x00 // Priority
    }

    fun sendArtDmx(ipAddress: String, universe: Int, dmxData: ByteArray) {
        if (dmxData.size != 512) return
        try {
            buffer[14] = (universe and 0xFF).toByte()
            buffer[15] = ((universe shr 8) and 0xFF).toByte()
            buffer[16] = 0x02 // Length MSB (512)
            buffer[17] = 0x00 // Length LSB

            System.arraycopy(dmxData, 0, buffer, 18, 512)

            val address = InetAddress.getByName(ipAddress)
            val packet = DatagramPacket(buffer, buffer.size, address, 6454)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e("ArtNetService", "Errore di trasmissione DMX", e)
        }
    }

    // Invia una richiesta di Handshake (ArtPoll) e verifica se il WROOM risponde (ArtPollReply)
    fun checkControllerPing(ipAddress: String): Boolean {
        try {
            val address = InetAddress.getByName(ipAddress)
            val sendPacket = DatagramPacket(pollBuffer, pollBuffer.size, address, 6454)
            socket?.send(sendPacket)

            // Buffer di ricezione per la risposta del controller
            val receiveBuffer = ByteArray(200)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            socket?.receive(receivePacket)

            // Verifica che la risposta inizi con "Art-Net"
            val responseHeader = String(receiveBuffer, 0, 7)
            return responseHeader == "Art-Net"
        } catch (e: Exception) {
            // Se va in timeout o l'host non è raggiungibile, restituisce false
            return false
        }
    }

    fun close() {
        socket?.close()
    }
}