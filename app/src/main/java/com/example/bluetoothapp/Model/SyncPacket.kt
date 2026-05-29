package com.example.bluetoothapp.Model

import android.util.Log
import java.time.Instant

class SyncPacket {

    fun costruisci_pacchetto(): ByteArray {
        Log.d("BT_DEBUG","Entra in costruisciPaccetto")

        val packet = ByteArray(13)
        packet[0]=0xA5.toByte()
        packet[1]=0xFF.toByte()

        val timestampMillis = Instant.now().toEpochMilli()

        packet[2] = (timestampMillis shr 56).toByte()
        packet[3] = (timestampMillis shr 48).toByte()
        packet[4] = (timestampMillis shr 40).toByte()
        packet[5] = (timestampMillis shr 32).toByte()
        packet[6] = (timestampMillis shr 24).toByte()
        packet[7] = (timestampMillis shr 16).toByte()
        packet[8] = (timestampMillis shr 8).toByte()
        packet[9] = (timestampMillis).toByte()

        // CRC su byte 1–9 (TYPE + TIMESTAMP)
        val crc = Utility.calcola_crc16(packet.copyOfRange(1, 10))

        packet[10] = (crc shr 8).toByte()
        packet[11] = (crc).toByte()

        // END
        packet[12] = 0x0A


        return packet

    }
}