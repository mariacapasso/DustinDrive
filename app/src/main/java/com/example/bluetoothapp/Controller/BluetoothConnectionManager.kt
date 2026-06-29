package com.example.bluetoothapp.Controller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.bluetoothapp.Model.SensorPacket
import com.example.bluetoothapp.Model.SyncPacket
import com.example.bluetoothapp.Model.TimeStampSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class BluetoothConnectionManager (private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectedSocket: BluetoothSocket? = null

    // per i pacchetti processati
    private val _processedDataFlow = MutableSharedFlow<SensorPacket>(extraBufferCapacity = 100)
    val processedDataFlow = _processedDataFlow.asSharedFlow()
    companion object {

        // Standard SerialPortService ID
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        @Volatile
        private var INSTANCE: BluetoothConnectionManager? = null

        fun getInstance(context: Context): BluetoothConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BluetoothConnectionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    // ---------------- STATE ----------------

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    @Volatile
    private var isReconnecting = false
    private var disconnected=false

    private var lastDevice: BluetoothDevice? = null

    //per invio pacchetto di sincronizzazione ogni 30 secondi
    private var syncJob: Job? = null
    private val SYNC_INTERVAL_MS = 30_000L// 30 secondi
    private val MAX_RECONNECT_ATTEMPTS = 5// numero max di tentativi per la riconnessione
    private val RECONNECT_DELAY_MS = 3000L // 3 secondi tra un tentativo e l'altro


    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {

                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = device?.name ?: "Dispositivo Sconosciuto"
                if (deviceName.contains("DUSTIN")) {
                      addDevice(device)
                }
            }
        }
    }
    // ---------------- SCAN ----------------
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
            Log.d("BT_LOG","Ricerca")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                Log.e("BT_LOG", "Manca il permesso BLUETOOTH_SCAN richiesto su Android 12+")
                return
            }
        }else{
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.e("BT_LOG", "Manca il permesso ACCESS_FINE_LOCATION richiesto su Android < 12")
                return
            }
        }
        // Se sta già cercando, fermalo per ricominciare
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }

        _discoveredDevices.value = emptyList()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try {
            context.registerReceiver(receiver, filter)

            val success = bluetoothAdapter?.startDiscovery() ?: false

            if (success) {
                Log.d("BT_LOG", "Scansione avviata con successo!")
            } else {
                Log.d("BT_LOG", "Impossibile avviare la scansione (Bluetooth spento o errore interno)")
            }
        } catch (e: Exception) {
            Log.d("BT_LOG", "Errore nella registrazione del receiver: ${e.message}")
        }
    }
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if(!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }
        bluetoothAdapter?.cancelDiscovery()

        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice?) {
        val type = when(device?.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL MODE"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "Dispositivo rilevato: ${device?.name} | Indirizzo: ${device?.address} | Tipo: $type")
        val current = _discoveredDevices.value.toMutableList()
        if (current.none { it.address == device?.address }) {
            if (device != null) {
                current.add(device)
            }
            _discoveredDevices.value = current
        }
    }
   // funzione per inviare pacchetto di sincronizzazione ogni 30 secondi
    fun startPeriodicSync(intervalMs: Long = SYNC_INTERVAL_MS) {
        syncJob?.cancel() // cancella eventuale job precedente
        syncJob = scope.launch(Dispatchers.IO) {
            while (true) {
                sendPacket()
                delay(intervalMs)
            }
        }
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)){
            return
        }
        lastDevice = device
        scope.launch {
            _connectionState.value = ConnectionState.Connecting

            bluetoothAdapter?.cancelDiscovery()

            try {
                connectedSocket?.close()

                val socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                socket.connect()

                connectedSocket = socket
                _connectionState.value = ConnectionState.Connected(device)
                startPeriodicSync()
                listen(socket)

            }catch (se: SecurityException){
                Log.e("BT_ERROR", "Permesso negato dall'utente durante la connessione")

            } catch (e: IOException) {
                _connectionState.value = ConnectionState.Error("Connection failed")
                reconnect()
                closeConnection()
            }
        }
    }
    private fun reconnect (){
        if (isReconnecting || disconnected) return

        Log.w("RECONNECT", "Inizio Riconnessione in corso...")
        isReconnecting = true
        _connectionState.value = ConnectionState.Reconnecting

        scope.launch(Dispatchers.IO){
            var attempt = 0 // inizializzo numero tentativi a 0
            var success = false // se ha avuto successo il tentativo

            while (attempt < MAX_RECONNECT_ATTEMPTS && !disconnected && !success) {
                attempt++
                Log.w("RECONNECT", "Tentativo $attempt di $MAX_RECONNECT_ATTEMPTS")


                try {
                connectedSocket?.close()
                val device = lastDevice ?: return@launch

                val socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                socket.connect()

                connectedSocket = socket
                _connectionState.value = ConnectionState.Connected(device)
                 sendPacket()
                    listen(socket)
                    success = true

            }catch (e: IOException){
                    Log.w("RECONNECT", "Tentativo $attempt fallito: ${e.message}")
                    if (attempt < MAX_RECONNECT_ATTEMPTS) {
                        delay(RECONNECT_DELAY_MS)
                    }
                }
            }
            if (!success) {
            _connectionState.value = ConnectionState.Error("Reconnecting failed!")
            Log.w("RECONNECT", "Riconnessione fallita dopo $attempt tentativi")
            closeConnection()
            _connectionState.value = ConnectionState.Disconnected
        }

            isReconnecting = false
        }
    }

    private fun listen(socket: BluetoothSocket)
     {
    scope.launch(Dispatchers.IO) {
            val input = socket.inputStream
            val buffer = ByteArray(4096) // buffer di lettura
            var bytesInBuffer = 0
            val packetSize = 12

            while (socket.isConnected) {
                    try {
                        // Leggiamo i dati dal Bluetooth
                        val bytesRead = input.read(buffer, bytesInBuffer, buffer.size - bytesInBuffer)
                       if (bytesRead == -1){
                            break
                        }

                        bytesInBuffer += bytesRead
                        var pos = 0
                        // Analizziamo il buffer
                        while (pos <= bytesInBuffer - packetSize) {

                                val potentialBytes = buffer.copyOfRange(pos, pos + packetSize)
                                val packet = SensorPacket(potentialBytes)

                                if (packet.isValid()) {
                                    _processedDataFlow.tryEmit(packet)
                                    pos += packetSize // Salto al prossimo pacchetto
                                    continue
                                }else {

                                    pos++
                                }
                        }

                        // Spostiamo i byte rimanenti all'inizio per la prossima lettura
                        if (pos < bytesInBuffer) {
                            System.arraycopy(buffer, pos, buffer, 0, bytesInBuffer - pos)
                            bytesInBuffer -= pos
                        } else {
                            bytesInBuffer = 0
                        }

                    } catch (e: IOException) {
                        scope.launch(Dispatchers.Main) {
                            if (!disconnected) reconnect()
                        }
                        break
                    }

            }
        }
    }

    fun sendPacket() {
        scope.launch(Dispatchers.IO) {
            try {
                connectedSocket?.outputStream?.let { out ->
                    out.write(SyncPacket.costruisci_pacchetto())
                    out.flush()
                    TimeStampSync.onSyncSent(0L)
                    Log.d("BT_CONTROLLER", "Pacchetto inviato con successo")
                }
            } catch (e: IOException) {
                Log.e("BT_CONTROLLER", "Errore durante l'invio: ${e.message}")
            }
        }
    }


    fun disconnect() {
        disconnected = true
        isReconnecting= false
        stopPeriodicSync()
        scope.launch {
            closeConnection()
            _connectionState.value = ConnectionState.Disconnected
        }
        TimeStampSync.reset()
    }

    private fun closeConnection() {
        stopPeriodicSync()
        try {
            connectedSocket?.outputStream?.close()
            connectedSocket?.inputStream?.close()
            connectedSocket?.close()
            Log.d("BT_DEBUG", "Socket chiuso correttamente")
        } catch (e: Exception) {
            Log.e("BT_DEBUG", "Errore in chiusura: ${e.message}")
        }finally {
            connectedSocket = null
        }
        TimeStampSync.reset()

    }

    fun release() {
        stopDiscovery()
        scope.cancel()
        closeConnection()
    }
    private fun hasPermission(permission: String): Boolean {

        // Se siamo su Android 11 o inferiore, i nuovi permessi BLUETOOTH_SCAN/CONNECT non servono
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (permission == Manifest.permission.BLUETOOTH_SCAN ||
                permission == Manifest.permission.BLUETOOTH_CONNECT) {
                return true // Considerali sempre concessi su vecchie versioni
            }
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

sealed class ConnectionState {
    object Disconnected: ConnectionState()
    object Connecting: ConnectionState()
    object Reconnecting: ConnectionState()

    data class Connected(val device: BluetoothDevice):ConnectionState()
    data class Error(val message: String): ConnectionState()

}