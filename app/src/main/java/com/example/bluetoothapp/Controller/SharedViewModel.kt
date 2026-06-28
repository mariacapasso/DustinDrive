package com.example.bluetoothapp.Controller

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bluetoothapp.Model.LiveDataModel
import com.example.bluetoothapp.Model.PacketParser
import com.example.bluetoothapp.Model.ParsedPacket
import com.example.bluetoothapp.Model.SensorTYPE
import com.example.bluetoothapp.R
import com.example.bluetoothapp.db.SensorDataDb
import com.example.bluetoothapp.db.SensorDataEntity
import com.example.bluetoothapp.db.SensorDataRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class SharedViewModel (application: Application) : AndroidViewModel(application), SensorEventListener {
    private val context = application.applicationContext

    // Riferimenti ai sensori dello smartphone(Accelerometro e GPS)

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    // LiveData per esporre i dati in tempo reale ai Fragment
    private val _currentLocation = MutableLiveData<Location>()
    val currentLocation: LiveData<Location> get() = _currentLocation

    private val _currentAvgAcceleration = MutableLiveData<Double>()
    val currentAvgAcceleration: LiveData<Double> get() = _currentAvgAcceleration

    // Variabili di calcolo dell'accelerometro
    private var accelerationCurrenValue = 0.0
    private var accelerationPrevValue = 0.0
    private val campionamentoTime = 50L
    private var lastUpdateTime = 0L
    private val accelBuffer = mutableListOf<Double>()
    private val btManager = BluetoothConnectionManager.getInstance(application)

    // Archivio dati grezzi: tiene sempre l'ultimo valore ricevuto per ogni tipo
    private val latestDataMap = mutableMapOf<SensorTYPE, ParsedPacket>()

    private val _monitorItems = MutableLiveData<List<LiveDataModel>>()
    val monitorItems: LiveData<List<LiveDataModel>> = _monitorItems

    private val repository: SensorDataRepository
    private val _ppgDataPoint = MutableLiveData<Pair<Float, Float>>()
    val ppgDataPoint: LiveData<Pair<Float, Float>> get() = _ppgDataPoint
    private val _formattedDate = MutableLiveData<String>()
    val formattedDate: LiveData<String> get() = _formattedDate
    private var xIndexPPG = 0f
    private val _currentMaxG = MutableLiveData<Double>()
    val currentMaxG: LiveData<Double> get() = _currentMaxG
    private val _exportResult = MutableLiveData<String>()
    val exportResult: LiveData<String> = _exportResult
    private var isEverConnected = false
    @Volatile private var currentLatitude: Double = 0.0
    @Volatile private var currentLongitude: Double = 0.0
    @Volatile private var currentAvgAcc: Double = 0.0

    init {
        val sensorDataDAO = SensorDataDb.getDatabase(application).sensorDataDao()
        repository = SensorDataRepository(sensorDataDAO)

        // Inizializzazione base delle card dove vengono mostrati i dati in tempo reale
        _monitorItems.value = listOf(
            LiveDataModel("Touch", "--", "In attesa...", R.drawable.fingerprint_black),
            LiveDataModel("Batteria", "100%", "In attesa...", R.drawable.batteria),
            LiveDataModel("Etanolo", "--", "Sicuro", R.drawable.ethinol),
            LiveDataModel("PPG", "--", "---", R.drawable.beat_heart)
        )

        // TIMER UI: Aggiorna l'interfaccia ogni 500ms
        // Questo distacca la ricezione dati dalla visualizzazione
        viewModelScope.launch {
            while (isActive) {
                refreshUiFromMap()
                delay(500) // Aggiornamento fluido 2 volte al secondo
            }
        }

        // Avvia l'ascolto dell'accelerometro subito
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Avvia il loop asincrono per calcolare la media ogni 15 secondi
        startAccelerationTimer()

        // Avvia il tracking continuo del GPS ogni secondo
        startLocationUpdates()

        // Avvia la ricezione dei dati via Bluetooth e il salvataggio immediato
        startBluetoothDataCollection()

        viewModelScope.launch {
            btManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        isEverConnected = true
                    }

                    is ConnectionState.Disconnected -> {
                        if (isEverConnected) {
                            exportDataToCsvFile()

                            isEverConnected = false // evita doppio export se Disconnected si ripete
                        }
                    }

                    else -> {}

                }
            }

        }
    }
    fun addData(data: SensorDataEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addData(data)
        }
    }

    private fun refreshUiFromMap() {
        val currentList = _monitorItems.value?.toMutableList() ?: return
        var changed = false

        // Mappatura Pacchetto -> Indice Card
        val typeToIndex = mapOf(
            SensorTYPE.Touch to 0,
            SensorTYPE.Batteria to 1,
            SensorTYPE.TAC to 2,
            SensorTYPE.PPG to 3
        )

        latestDataMap.forEach { (type, data) ->
            val index = typeToIndex[type] ?: return@forEach

            val liveDataModel = when (data) {
                is ParsedPacket.Battery -> LiveDataModel(
                    titolo = "Batteria",
                    valore = "${data.percentage}%",
                    stato = data.voltageFormatted,
                    icona = R.drawable.batteria
                )

                is ParsedPacket.Touch -> LiveDataModel(
                    titolo = "Touch",
                    valore = if (data.isActive) "ATTIVO" else "NON ATTIVO",
                    stato = if (data.isActive) "${data.elettrodiCount} elettrodi rilevati" else "Nessun tocco",
                    icona = R.drawable.fingerprint_black
                )

                is ParsedPacket.TAC -> LiveDataModel(
                    titolo = "Etanolo",
                    valore = "${data.ppbValue} ppb",
                    stato = if (data.isSafe) "Sicuro" else "Attenzione",
                    icona = R.drawable.ethinol
                )

                is ParsedPacket.PPG -> LiveDataModel(
                    titolo = "PPG",
                    valore = "${data.bpmValue} ",
                    stato = "Stabile",
                    icona = R.drawable.beat_heart
                )
            }

            if (index < currentList.size) {
                currentList[index] = liveDataModel
                changed = true
            }
        }

        if (changed) {
            _monitorItems.postValue(currentList)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val currTime = System.currentTimeMillis()
        if (currTime - lastUpdateTime >= campionamentoTime) {
            lastUpdateTime = currTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            accelerationCurrenValue = sqrt((x * x) + (y * y) + (z * z)).toDouble()
            val changeInAcceleration = abs(accelerationCurrenValue - accelerationPrevValue)
            accelerationPrevValue = accelerationCurrenValue

            synchronized(accelBuffer) {
                accelBuffer.add(changeInAcceleration)
            }
        }
    }

    private fun startAccelerationTimer() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(15000L) // 15 secondi
                synchronized(accelBuffer) {
                    if (accelBuffer.isNotEmpty()) {
                        val mediaAcc = accelBuffer.average()
                        currentAvgAcc=mediaAcc
                       // LocationAccelState.avgAcceleration = mediaAcc
                        val maxG = accelBuffer.maxOrNull() ?: 0.0

                        // Notifica la UI
                        _currentAvgAcceleration.postValue(mediaAcc)
                        _currentMaxG.postValue(maxG)
                        accelBuffer.clear()
                    }
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            val locationRequest =
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // 1 secondo
                    .setMinUpdateIntervalMillis(1000)
                    .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val lastLoc = locationResult.lastLocation ?: return
                    currentLatitude = lastLoc.latitude
                    currentLongitude = lastLoc.longitude

                    // Passa l'oggetto alla mappa (UI)
                    _currentLocation.postValue(lastLoc)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
        }
    }

    private fun startBluetoothDataCollection() {
        viewModelScope.launch(Dispatchers.IO) {
            btManager.processedDataFlow.collect { packet ->
                val parsedData = PacketParser.startParse(packet)
                parsedData?.let { data ->
                    _formattedDate.postValue("Ultimo Aggiornamento: ${data.formattedTimestamp}")
                    if (data is ParsedPacket.PPG) {
                        _ppgDataPoint.postValue(Pair(xIndexPPG++, data.bpmValue.toFloat()))
                    }
                    latestDataMap[data.type] = data

                    val entity = SensorDataEntity(
                        type = data.type,
                        value = packet.sensorValue,
                        timestamp = data.rawTimeStamp,
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        avgAcceleration = String.format(Locale.US, "%.2f", currentAvgAcc)
                    )

                    if (entity.type != SensorTYPE.PPG) {
                        repository.addData(entity)
                    }

                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Rilascia i sensori quando l'app si chiude definitivamente
        sensorManager.unregisterListener(this)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    fun exportDataToCsvFile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs= getApplication<Application>().getSharedPreferences("export_prefs", Context.MODE_PRIVATE)
               val lastId = prefs.getLong("last_exported_id", 0L)

                val records = repository.getDataAfter(lastId)
                if (records.isEmpty()) {
                    _exportResult.postValue("Nessun nuovo dato da esportare")
                    return@launch
                }


                val header = "idPacket,type,value,timestamp,latitude,longitude,avgAcceleration\n"
                val rows = records.joinToString("\n") { r ->
                    val lat = String.format(Locale.US, "%.6f", r.latitude)
                    val lng = String.format(Locale.US, "%.6f", r.longitude)
                    "${r.idPacket},${r.type},${r.value},${r.timestamp},$lat,$lng,${r.avgAcceleration}"

                }
                val csvContent = header + rows
                val fileName = "dustin_export_${System.currentTimeMillis()}.csv"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = getApplication<Application>().contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        resolver.openOutputStream(it)
                            ?.use { out -> out.write(csvContent.toByteArray()) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(it, values, null, null)
                    }
                } else {
                    val file = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    file.writeText(csvContent)
                }
                prefs.edit().putLong("last_exported_id", records.last().idPacket).apply()

                _exportResult.postValue("CSV salvato in Downloads: $fileName")
            } catch (e: Exception) {
                _exportResult.postValue("Errore export: ${e.message}")
            }

        }

    }
}

