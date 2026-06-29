package com.example.bluetoothapp.Model



class SensorPacket (private val rawBytes: ByteArray) {
    init {
        require(rawBytes.size == 12) { "Pacchetto incompleto" }
    }

    val type: Int = rawBytes[1].toInt() and 0xFF
    val sensor_type= SensorTYPE.from(type)

    // Estraiamo il timestamp (byte 2,3,4,5)
    val ts = ((rawBytes[2].toLong() and 0xFF) shl 24) or
            ((rawBytes[3].toLong() and 0xFF) shl 16) or
            ((rawBytes[4].toLong() and 0xFF) shl 8) or
            (rawBytes[5].toLong() and 0xFF)

    // Dati grezzi a 24 bit (Offset 6-8)
    val sensorValue: Int = (
            ((rawBytes[6].toInt() and 0xFF) shl 16) or
                    ((rawBytes[7].toInt() and 0xFF) shl 8) or
                    (rawBytes[8].toInt() and 0xFF)
            )


     // Verifica la validità del pacchetto confrontando il CRC ricevuto

    fun isValid(): Boolean {
        if (rawBytes[0] != 0xA5.toByte() || rawBytes[11] != 0x0A.toByte()) return false

        // Estrai il CRC ricevuto (Offset 9-10)
        val receivedCrc = ((rawBytes[9].toInt() and 0xFF) shl 8) or (rawBytes[10].toInt() and 0xFF)

        // Calcola il CRC sui byte 1-8 (Type + Timestamp + Data)
        val calculatedCrc = Utility.calcola_crc16(rawBytes.copyOfRange(1, 9))

        return receivedCrc == calculatedCrc
    }

}
enum class SensorTYPE(val value: Int) {
    PPG(0x01),
    Touch(0X02),
    TAC(0X03),
    Batteria(0X04);
    companion object {
        fun from(value: Int): SensorTYPE? {
            return entries.find { it.value == value }
        }
    }
}
