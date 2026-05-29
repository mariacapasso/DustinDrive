package com.example.bluetoothapp.Model

object Utility {

     fun calcola_crc16(data: ByteArray): Int {
        var crc = 0xFFFF

        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021

                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}