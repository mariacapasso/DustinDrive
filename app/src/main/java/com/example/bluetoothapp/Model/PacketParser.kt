package com.example.bluetoothapp.Model

import android.os.Build
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object PacketParser {

    fun startParse(packet: SensorPacket): ParsedPacket? /*LiveDataModel?*/ {
        val rawTimeStamp=TimeStampSync.computeTimestamp(packet.ts)
        val timestamp = convertTimeStamp(rawTimeStamp)
        return when (packet.sensor_type) {
            SensorTYPE.Batteria -> {
                val tensione = packet.sensorValue.toDouble() / 1000.0
                ParsedPacket.Battery(
                    timestamp,
                    rawTimeStamp,
                    calcolaPercentualeBatteria(tensione),
                    String.format("%.2f", tensione) + "V"
                )

            }

            SensorTYPE.Touch -> {
                val numElettrodi = numeroElettrodiAttivi(packet.sensorValue)
                ParsedPacket.Touch(
                    timestamp,
                    rawTimeStamp,
                    numElettrodi > 0,
                    numElettrodi
                )

            }

            SensorTYPE.TAC -> ParsedPacket.TAC(
                timestamp,
                rawTimeStamp,
                packet.sensorValue,
                packet.sensorValue < 100
            )

            SensorTYPE.PPG -> ParsedPacket.PPG(
                timestamp,
                rawTimeStamp,
                packet.sensorValue
            )

                null -> null
        }
}      /* SensorTYPE.Batteria -> {
                val tensione = packet.sensorValue.toDouble() / 1000.0
             val percentuale = calcolaPercentualeBatteria(tensione)
                    LiveDataModel("Livello di Batteria", "$percentuale %", "Tensione: ${String.format("%.2f", tensione)}V", R.drawable.batteria)
            }

            SensorTYPE.Touch -> {
                val numElettrodi = numeroElettrodiAttivi(packet.sensorValue)
                val stato = if (numElettrodi > 0) "ATTIVO" else "NON ATTIVO"
                val dettaglio = if (numElettrodi > 0) "$numElettrodi rilevati" else "Nessun tocco"
                LiveDataModel("Touch", stato, dettaglio, R.drawable.fingerprint_black)
            }

            SensorTYPE.TAC -> {
                val ppb = packet.sensorValue
                val stato = if (ppb < 100) "Sicuro" else "Attenzione"
                LiveDataModel("Etanolo", "$ppb ppb", stato, R.drawable.ethinol)
            }

            SensorTYPE.PPG -> {
                // Esempio per PPG (Battito Cardiaco)
                LiveDataModel("PPG", "${packet.sensorValue} BPM", "Stabile", R.drawable.beat_heart)
            }

        null -> null
    }
}*/

   fun numeroElettrodiAttivi( bit: Int): Int{
        var count = 0
        for(i in 0 until 16){
            if((bit shr i) and 1 == 1){
                count++
            }
        }
        return count

    }

    fun calcolaPercentualeBatteria(tensione: Double): Int {
        val percentuale = (-198.92 * tensione * tensione) + (1657.1 * tensione ) -3354.3

        return when {
            percentuale > 100 -> 100
            percentuale < 0 -> 0
            else -> percentuale.toInt()
        }
    }
    fun convertTimeStamp(timeStamp: Long): String{
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val instant = Instant.ofEpochMilli(timeStamp)

                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            } else {
                val sdf= SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS",Locale.getDefault())
                sdf.format(java.util.Date(timeStamp))

            }
        }catch (e: Exception){
            "Invalid timestamp"

        }
    }
}