package com.example.bluetoothapp.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothapp.Model.LiveDataModel
import com.example.bluetoothapp.Model.PacketParser
import com.example.bluetoothapp.Model.SensorPacket
import com.example.bluetoothapp.Model.SensorTYPE
import com.example.bluetoothapp.TimeStampSync
import kotlinx.coroutines.launch

class DashBoardViewModel(application: Application) : AndroidViewModel(application){
    private val connectionManager = BluetoothConnectionManager.getInstance(application)
    private val _formattedDate = MutableLiveData<String>()
    val formattedDate: LiveData<String> get() = _formattedDate

    // Flusso specifico Touch (true = mani appoggiate, false = staccate)
    private val _isVolanteToccato = MutableLiveData<Pair<Boolean, String>>()
    val isVolanteToccato: LiveData<Pair<Boolean, String>> get() = _isVolanteToccato

    // Flussi dati per i grafici (Valore X del grafico basato sull'orologio di sistema o l'indice)
    private val _ppgDataPoint = MutableLiveData<Pair<Float, Float>>()
    val ppgDataPoint: LiveData<Pair<Float, Float>> get() = _ppgDataPoint

    private val _tacDataPoint = MutableLiveData<Pair<Float, Float>>()
    val tacDataPoint: LiveData<Pair<Float, Float>> get() = _tacDataPoint
    private var ultimoValoreTAC: Float = 0f
    private var xIndexPPG = 0f
    private var xIndexTAC = 0f

    init {
        // Avvia l'ascolto del flusso di pacchetti provenienti dal Bluetooth
        viewModelScope.launch {
            connectionManager.processedDataFlow.collect { packet ->
                parseAndProcess(packet)
            }
        }
    }

        private fun parseAndProcess(packet: SensorPacket) {
            val dataStringa = PacketParser.convertTimeStamp(TimeStampSync.computeTimestamp(packet.ts.toLong()))
            _formattedDate.postValue("Ultimo Aggiornamento: $dataStringa")

            // 2. Smistiamo i dati in base al tipo di sensore presente nel pacchetto
            when (packet.sensor_type) {
                SensorTYPE.Touch -> {
                    val numElettrodi = PacketParser.numeroElettrodiAttivi(packet.sensorValue)
                    val dettaglio = if (numElettrodi > 0) "$numElettrodi elettrodi rilevati" else "Nessun tocco"
                    _isVolanteToccato.postValue(Pair(numElettrodi > 0, dettaglio))
                }
                SensorTYPE.PPG -> {
                    _ppgDataPoint.postValue(Pair(xIndexPPG++, packet.sensorValue.toFloat()))
                    if (xIndexPPG % 100 == 0f) {
                        _tacDataPoint.postValue(Pair(xIndexTAC++, ultimoValoreTAC))
                    }
                }
                SensorTYPE.TAC -> {
                    ultimoValoreTAC = packet.sensorValue.toFloat()
                    _tacDataPoint.postValue(Pair(xIndexTAC++, ultimoValoreTAC))
                }
               else -> {}

            }
        }
    }