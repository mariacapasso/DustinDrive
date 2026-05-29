package com.example.bluetoothapp.ViewModel

import BluetoothConnectionManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bluetoothapp.Model.ItemHomeCard
import com.example.bluetoothapp.Model.LiveDataModel
import com.example.bluetoothapp.Model.PacketParser
import com.example.bluetoothapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel (application: Application) : AndroidViewModel(application){
    private val btManager = BluetoothConnectionManager.getInstance(application)
    // Archivio dati grezzi: tiene sempre l'ultimo valore ricevuto per ogni tipo
    private val latestDataMap = mutableMapOf<Int, LiveDataModel>()

    // La LiveData che l'Adapter osserva
    private val _monitorItems = MutableLiveData<List<LiveDataModel>>()
    val monitorItems: LiveData<List<LiveDataModel>> = _monitorItems


    init {
        // 1. Inizializzazione base delle card
        _monitorItems.value = listOf(
            LiveDataModel("Touch", "--", "In attesa...", R.drawable.fingerprint_black),
            LiveDataModel("Batteria", "--", "In attesa...", R.drawable.batteria),
            LiveDataModel("Etanolo", "--", "Sicuro", R.drawable.ethinol),
            LiveDataModel("PPG", "--", "---", R.drawable.beat_heart)
        )

        // 2. Ascolto continuo: salva i dati nella mappa alla massima velocità
        viewModelScope.launch {
            btManager.processedDataFlow.collect { packet ->
                val parsedData = PacketParser.startParse(packet)

                parsedData?.let {
                    latestDataMap[packet.type] = it
                }
            }
        }

        // 3. TIMER UI: Aggiorna l'interfaccia ogni 500ms (o il tempo che preferisci)
        // Questo distacca la ricezione dati dalla visualizzazione
        viewModelScope.launch {
            while (isActive) {
                refreshUiFromMap()
                delay(500) // Aggiornamento fluido 2 volte al secondo
            }
        }

    }
    private fun refreshUiFromMap() {
        val currentList = _monitorItems.value?.toMutableList() ?: return
        var changed = false

        // Mappatura Pacchetto -> Indice Card
        val typeToIndex = mapOf(0x02 to 0, 0x04 to 1, 0x03 to 2, 0x01 to 3)

        latestDataMap.forEach { (type, data) ->
            typeToIndex[type]?.let { index ->
                if (index < currentList.size) {
                    currentList[index] = currentList[index].copy(valore = data.valore, stato = data.stato)
                    changed = true
                }
            }
        }

        if (changed) {
            _monitorItems.postValue(currentList)
        }
    }

}