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
import com.example.bluetoothapp.Model.LiveDataModel
import com.example.bluetoothapp.Model.SensorPacket
import com.example.bluetoothapp.Packet
import com.example.bluetoothapp.TimeStampSync
import com.example.bluetoothapp.ViewModel.HomeViewModel
import com.google.type.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.text.toDouble

class BluetoothConnectionManager (private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectedSocket: BluetoothSocket? = null

    // 1. Aggiungi questo Flow per i pacchetti processati
    private val _processedDataFlow = kotlinx.coroutines.flow.MutableSharedFlow<SensorPacket>(extraBufferCapacity = 100)
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
    var onPacketReceived: ((String) -> Unit)? = null
//private var espMillis: Long = 0L //millisecondi da quando si è acceso l'ESP32
   // private val packetChannel = Channel<Packet>(Channel.UNLIMITED)

    @Volatile
    private var isReconnecting = false
    private var disconnected=false

    private var lastDevice: BluetoothDevice? = null


    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private val receiver = object : BroadcastReceiver() {
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

                device?.let { addDevice(it) }
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

            // Qui salviamo il risultato del comando
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
    private fun addDevice(device: BluetoothDevice) {
        val type = when(device.type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL MODE"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "Dispositivo rilevato: ${device.name} | Indirizzo: ${device.address} | Tipo: $type")
        val current = _discoveredDevices.value.toMutableList()
        if (current.none { it.address == device.address }) {
            current.add(device)
            _discoveredDevices.value = current
        }
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
                //startParsePacket()
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

        scope.launch{
            delay(2000)
            // Chiudiamo la vecchia socket in modo sicuro
            try { connectedSocket?.close() } catch (e: Exception) {}
            try {
                val device = lastDevice ?: return@launch

                connectedSocket?.close()

                val socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                socket.connect()

                connectedSocket = socket
                _connectionState.value = ConnectionState.Connected(device)

                //espMillis=0L
                //startParsePacket()
                 listen(socket)

            }catch (e: IOException){
                _connectionState.value= ConnectionState.Error("Reconnecting failed!")
                Log.w("RECONNECT","Tentativo di riconnessione!")
            }finally {
                // Finito il tentativo (successo o errore), sblocco la funzione
                isReconnecting = false
            }
        }
    }

    // LISTEN PER PACCHETTI
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
                           // triggerReconnect()
                            break
                        }

                        bytesInBuffer += bytesRead
                        var pos = 0
                        // Analizziamo il buffer cercando il pattern A5 ... 0A
                        while (pos <= bytesInBuffer - packetSize) {

                            // Controllo start A5 e end 0A
                            if (buffer[pos] == 0xA5.toByte() && buffer[pos + 11] == 0x0A.toByte()) {

                                val potentialBytes = buffer.copyOfRange(pos, pos + packetSize)
                                val packet = SensorPacket(potentialBytes)

                                if (packet.isValid()) {
                                    _processedDataFlow.tryEmit(packet)
                                    pos += packetSize // Salto al prossimo pacchetto
                                    continue
                                }
                            }
                            pos++
                        }

                        // Spostiamo i byte rimanenti all'inizio per la prossima lettura
                        if (pos < bytesInBuffer) {
                            System.arraycopy(buffer, pos, buffer, 0, bytesInBuffer - pos)
                            bytesInBuffer -= pos
                        } else {
                            bytesInBuffer = 0
                        }

                    } catch (e: IOException) {
                        if (!disconnected) reconnect()
                        break
                    }

            }
        }
    }

    fun sendPacket(bytes: ByteArray) {
        scope.launch(Dispatchers.IO) {
            try {
                connectedSocket?.outputStream?.let { out ->
                    out.write(bytes)
                    out.flush()
                    Log.d("BT_CONTROLLER", "Pacchetto inviato con successo")
                }
            } catch (e: IOException) {
                Log.e("BT_CONTROLLER", "Errore durante l'invio: ${e.message}")
            }
        }
    }

  /* fun startParsePacket() {
       //return
       scope.launch(Dispatchers.Default) {
           var lastTimeStamp = 0L
           var ppgCounter = 0
           var touchCounter = 0

           for (packet in packetChannel) {
               //val parsed = parsePacket(packet)
               // if (packet.timestamp < lastTimeStamp){
                    Log.w("BT_RECOVERY", "Reset rilevato! Nuovo timestamp: ${packet.timestamp} < Vecchio: $lastTimeStamp")
                    espMillis = System.currentTimeMillis() - packet.timestamp
                }
                lastTimeStamp = packet.timestamp.toLong()//
               //if(parsed != null){
               val timeStamp = packet.timestamp

               // Se il timestamp ricevuto è molto minore del precedente, l'ESP si è resettato
               if (lastTimeStamp != 0L && timeStamp < lastTimeStamp - 1000) {
                   Log.w("BT_RECOVERY", "Reset hardware rilevato! Risincronizzo il tempo...")
                   espMillis = System.currentTimeMillis() - timeStamp
               }

               lastTimeStamp = timeStamp.toLong()
               if (espMillis == 0L) {

                   //sottraggo i millesecondi da quando si è acceso l'esp dall'ora del cell per avere il momento in cui si è acceso l'esp32
                   espMillis = System.currentTimeMillis() - timeStamp

               }
               val timestampEffettivo = espMillis + timeStamp
               //val date= convertTimeStamp(timestampEffettivo)
               //val data= (packet.d1 shl 16) or (packet.d2 shl 8) or packet.d3
               when (packet.type) {
                   0x01 -> {
                       // logga PPG solo ogni 100 campioni(1 al secondo)
                       if (timeStamp % 1000 < 10) {
                           val date = convertTimeStamp(timestampEffettivo)
                           val data = (packet.d1 shl 16) or (packet.d2 shl 8) or packet.d3
                           val valoreBPM = "$data bpm"
                           val statoBpm= "buono"
                               _processedDataFlow.tryEmit(ProcessedData(0x01,valoreBPM,statoBpm))

                           Log.d("SENSOR", "PPG: $date, $data")
                       }
                       // ppgCounter++
                   }

                   0x02 -> {
                       val date = convertTimeStamp(timestampEffettivo)
                       val data = (packet.d1 shl 16) or (packet.d2 shl 8) or packet.d3
                       val numElettrodiToccati = numeroElettrodiAttivi(data)
                       val testoTouch = if (numElettrodiToccati > 0) "ATTIVO" else "NON ATTIVO"
                       val statoTouch = if (numElettrodiToccati > 0)  "Rilevato" else "Nessun tocco"
                       // EMETTI il dato nel Flow
                       _processedDataFlow.tryEmit(ProcessedData(0x02, testoTouch, statoTouch))

                       //  Log.d("SENSOR","Touch: $date, $data")
                   } //touchCounter++
                   0x03 -> {
                       val data = (packet.d1 shl 16) or (packet.d2 shl 8) or packet.d3
                       val date = convertTimeStamp(espMillis + packet.timestamp)
                       val valorePpb = "$data ppb"
                       val statoAlcol = if (data < 100) "Sicuro" else "Attenzione"

                       _processedDataFlow.tryEmit(ProcessedData(0x03, valorePpb, statoAlcol))
                       //Log.d("SENSOR","TAC(Alcol): $date, $data ppb")
                   }

                   0x04 -> {
                       val data = (packet.d1 shl 16) or (packet.d2 shl 8) or packet.d3
                       val date = convertTimeStamp(espMillis + packet.timestamp)
                       val tenVolt = data.toDouble() / 1000.0
                       val percentuale = calcolaPercentualeBatteria(tenVolt)
                   _processedDataFlow.tryEmit(ProcessedData(0x04, "$percentuale %", "$tenVolt V"))
                       //Log.d("SENSOR","Batteria: $date, $percentuale % ($tenVolt V)")
                   }
                   //  else -> Log.d("SENSOR","Tipo Sconosciuto: $packet.type")

               }
               // Log di controllo ogni minuto per vedere se il PPG fluisce
               // val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime >= 60000) {
                        val dateLog = convertTimeStamp(currentTime)
                        Log.i("DIAGNOSTIC", "[$dateLog] PPG: $ppgCounter/min | Touch: $touchCounter/min")
                        touchCounter=0
                        ppgCounter = 0
                        lastLogTime = currentTime
                    }//
               // }
           }
       }
   }

    private fun numeroElettrodiAttivi( bit: Int): Int{
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

                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            } else {
                val sdf= SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.getDefault())
                sdf.format(java.util.Date(timeStamp))

            }
        }catch (e: Exception){
            "Invalid timestamp"

        }
    }
        fun sendPacket(){
        val socket = connectedSocket

        if(socket?.isConnected == true) {

            scope.launch(Dispatchers.IO) {
                try {
                    val packet= costruisci_pacchetto()
                    socket.outputStream.apply {
                        write(packet)
                        flush()

                    }

                    Log.d(
                        "BT_SEND",
                        packet.joinToString(" ") { "%02X".format(it) }
                    )


                    val timestamp=
                        ((packet[2].toLong() and 0xFFL) shl 56) or
                                ((packet[3].toLong() and 0xFFL) shl 48 ) or
                                ((packet[4].toLong() and 0xFFL) shl 40) or
                                ((packet[5].toLong() and 0xFFL) shl 32) or
                                ((packet[6].toLong() and 0xFFL) shl 24) or
                                ((packet[7].toLong() and 0xFFL) shl 16) or
                                ((packet[8].toLong() and 0xFFL) shl 8 ) or
                                ((packet[9].toLong() and 0xFFL))
                    //Log.d("TIMESTAMP_SEND", "TimeStamp Inviato: $timestamp")
                    val date= convertTimeStamp(timestamp)// isSeconds = false)
                    Log.d("DATETIME_SEND","DateTime: $date")




                } catch (e: IOException) {
                    Log.e("BT_LOG", "Invio fallito: ${e.message}")
                }
            }
        }else {
            Log.e("BT_ERROR","Impossibile inviare: Non sei connesso a nessun dispositivo")
        }

    }*/

  /*   fun sendMessage (message: String) {
         //controllare che il dispositivo è connesso
        // Log.d("BT_LOG", "Entrato in sendMessage")
        val socket = connectedSocket
         if(socket?.isConnected == true) {
             //Log.d("BT_LOG","socket è connessa")
             scope.launch(Dispatchers.IO) {
                try {
                     val messaggioCompleto = message + "\r\n"
                     socket.outputStream.write(messaggioCompleto.toByteArray())
                     socket.outputStream.flush()



                     Log.d("BT_LOG", "Invio: $message")

                 } catch (e: IOException) {
                     Log.e("BT_LOG", "Invio fallito: ${e.message}")
                 }
             }
         }else {
             Log.e("BT_ERROR","Impossibile inviare: Non sei connesso a nessun dispositivo")
         }

    }*/


   /*fun costruisci_pacchetto(): ByteArray {
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
       val crc = crc16(packet.copyOfRange(1, 10))

       packet[10] = (crc shr 8).toByte()
       packet[11] = (crc).toByte()

       // END
       packet[12] = 0x0A


       return packet

   }*/

    /*fun crc16(data: ByteArray): Int {
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
    }*/

    fun disconnect() {
        disconnected = true
        isReconnecting= false
        scope.launch {
            closeConnection()
            _connectionState.value = ConnectionState.Disconnected
        }
        TimeStampSync.reset()
    }

    private fun closeConnection() {
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
            }else{
                if (permission == Manifest.permission.ACCESS_FINE_LOCATION){
                    return true
                }
            }
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

sealed class ConnectionState {
    object Disconnected: ConnectionState()
    object Connecting: ConnectionState()

    data class Connected(val device: BluetoothDevice):ConnectionState()
    data class Error(val message: String): ConnectionState()

}