package com.paliddo.pdmxcontroller.network

import android.util.Log
import com.paliddo.pdmxcontroller.data.model.ControllerInfo
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ArtNetService {
    private var socket: DatagramSocket? = null
    // Buffer impostato a 530 byte per compatibilità esatta con ESP32
    private val buffer = ByteArray(530)
    private val pollBuffer = ByteArray(14)

    init {
        try {
            socket = DatagramSocket()
            socket?.soTimeout = 800
            setupArtNetHeader()
            setupPollHeader()
        } catch (e: Exception) {
            Log.e("ArtNetService", "Errore apertura socket", e)
        }
    }

    private fun setupArtNetHeader() {
        val header = byteArrayOf('A'.code.toByte(), 'r'.code.toByte(), 't'.code.toByte(), '-'.code.toByte(), 'N'.code.toByte(), 'e'.code.toByte(), 't'.code.toByte(), 0)
        System.arraycopy(header, 0, buffer, 0, 8)
        buffer[8] = 0x00  // OpCode ArtDmx LSB
        buffer[9] = 0x50  // OpCode ArtDmx MSB
        buffer[10] = 0x00 // Version MSB
        buffer[11] = 14   // Version LSB
        buffer[12] = 0x00 // Sequence
        buffer[13] = 0x00 // Physical
    }

    private fun setupPollHeader() {
        val header = byteArrayOf('A'.code.toByte(), 'r'.code.toByte(), 't'.code.toByte(), '-'.code.toByte(), 'N'.code.toByte(), 'e'.code.toByte(), 't'.toByte(), 0)
        System.arraycopy(header, 0, pollBuffer, 0, 8)
        pollBuffer[8] = 0x00  // ArtPoll
        pollBuffer[9] = 0x20
        pollBuffer[10] = 0x00
        pollBuffer[11] = 14
    }

    fun sendArtDmx(ipAddress: String, universe: Int, dmxData: ByteArray, port: Int = 6454) {
        if (dmxData.size != 512 || socket?.isClosed == true) return
        try {
            buffer[14] = (universe and 0xFF).toByte()
            buffer[15] = ((universe shr 8) and 0xFF).toByte()
            buffer[16] = 0x02 // Length 512 MSB
            buffer[17] = 0x00 // Length 512 LSB

            System.arraycopy(dmxData, 0, buffer, 18, 512)

            val address = InetAddress.getByName(ipAddress)
            // Inviamo esattamente 530 byte
            val packet = DatagramPacket(buffer, 530, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e("ArtNetService", "DMX Error: ${e.message}")
        }
    }

    fun sendWakeUpHandshake(ipAddress: String, port: Int = 6454) {
        if (socket?.isClosed == true) return
        try {
            val payload = "P-DMX:START".toByteArray(Charsets.US_ASCII)
            val address = InetAddress.getByName(ipAddress)
            val packet = DatagramPacket(payload, payload.size, address, port)
            socket?.send(packet)
            Log.d("ArtNetService", "Wake-up START inviato")
        } catch (e: Exception) {
            Log.e("ArtNetService", "Wake-up Error")
        }
    }

    fun checkControllerPing(ipAddress: String, port: Int = 6454): ControllerInfo? {
        if (socket?.isClosed == true) return null
        try {
            val address = InetAddress.getByName(ipAddress)
            val sendPacket = DatagramPacket(pollBuffer, pollBuffer.size, address, port)
            socket?.send(sendPacket)

            val receiveBuffer = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket?.receive(receivePacket)

            val header = String(receiveBuffer, 0, 8, Charsets.US_ASCII)
            if (!header.startsWith("Art-Net")) return null
            
            val opCode = (receiveBuffer[8].toInt() and 0xFF) or ((receiveBuffer[9].toInt() and 0xFF) shl 8)
            if (opCode == 0x2100) {
                val cIp = "${receiveBuffer[10].toInt() and 0xFF}.${receiveBuffer[11].toInt() and 0xFF}.${receiveBuffer[12].toInt() and 0xFF}.${receiveBuffer[13].toInt() and 0xFF}"
                val firm = "${receiveBuffer[16].toInt() and 0xFF}.${receiveBuffer[17].toInt() and 0xFF}"
                val sName = String(receiveBuffer, 26, 18, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val lName = String(receiveBuffer, 44, 64, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val stat = String(receiveBuffer, 108, 64, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                return ControllerInfo(sName, lName, cIp, firm, stat)
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    fun close() {
        socket?.close()
    }
}
