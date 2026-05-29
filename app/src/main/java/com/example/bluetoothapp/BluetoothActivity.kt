package com.example.bluetoothapp

import BluetoothConnectionManager
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothapp.Adapter.DeviceAdapter
import com.example.bluetoothapp.Model.SyncPacket
import com.example.bluetoothapp.databinding.BluetoothActivityBinding
import kotlinx.coroutines.launch

class BluetoothActivity: ComponentActivity() {

    lateinit var bluetoothManager: BluetoothConnectionManager
    private lateinit var binding: BluetoothActivityBinding
    private lateinit var deviceListAdapter: DeviceAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WindowCompat.enableEdgeToEdge(window)
        binding = BluetoothActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bluetoothManager = BluetoothConnectionManager.getInstance(this)

        initRicercaDispositivi()

    }

    fun initRicercaDispositivi() {
        val listaDispositivi = mutableListOf<String>()

        deviceListAdapter = DeviceAdapter(
            mutableListOf(),
            onConnectClick = { device -> connettiDispositivo(device) },
            onDisconnectClick = { device -> disconnettiDispositivo(device)}
        )
        binding.listaDispositivi.layoutManager = LinearLayoutManager(this)
        binding.listaDispositivi.adapter = deviceListAdapter


        binding.ricercaBtn.setOnClickListener {
            bluetoothManager.startDiscovery()
        }
        observeState()
    }

    private fun connettiDispositivo(nome: BluetoothDevice){
        bluetoothManager.connect(nome)
        val syncPacketModel = SyncPacket()
        val bytesToSend = syncPacketModel.costruisci_pacchetto()
        bluetoothManager.sendPacket(bytesToSend)
    }

    private fun disconnettiDispositivo(device: BluetoothDevice) {
        bluetoothManager.disconnect()
    }
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    bluetoothManager.discoveredDevices.collect { devices ->
                        deviceListAdapter.updateList(devices)

                    }
                }
            }
        }
    }

}

/*{ _, _, position, _ ->
    val label = deviceListAdapter.getItem(position) ?: return@setOnItemClickListener
    val device = deviceMap[label] ?: return@setOnItemClickListener
    bluetoothManager.connect(device)
    val syncPacketModel = SyncPacket()
    val bytesToSend = syncPacketModel.costruisci_pacchetto()
    bluetoothManager.sendPacket(bytesToSend)

}

binding.scanButton.setOnClickListener {
        bluetoothManager.startDiscovery()

}
}*/






/* adapter = DataAdapter(messages)
 binding.recyclerView.layoutManager = LinearLayoutManager(this)
 binding.recyclerView.adapter = adapter
 //bluetoothManager.startParsePacket()


 binding.inviabt.setOnClickListener {
   /*  val testo = binding.messagetxt.text.toString().trim()

     if (testo.isNotEmpty() && bluetoothManager.connectionState.value is ConnectionState.Connected) {
         bluetoothManager.sendMessage(testo)
         messages.add(Data(testo, true))
         val currentPos = messages.size - 1
         adapter.notifyItemInserted(messages.size - 1)
         binding.recyclerView.scrollToPosition(currentPos)
         binding.messagetxt.text.clear()
     } else if (testo.isNotEmpty()) {
         Toast.makeText(this, "Dispositivo non connesso", Toast.LENGTH_SHORT).show()
     }
 }

 bluetoothManager.onMessageReceived = { testoRicevuto ->
     messages.add(Data(testoRicevuto, false))
     adapter.notifyItemInserted(messages.size - 1)
     binding.recyclerView.scrollToPosition(messages.size - 1)
 }*/
    if (bluetoothManager.connectionState.value is ConnectionState.Connected) {
// 1. Chiedi al MODEL di costruire il pacchetto di sincronizzazione (13 byte)
        val syncPacketModel = SyncPacket()
        val bytesToSend = syncPacketModel.costruisci_pacchetto()
         bluetoothManager.sendPacket(bytesToSend)

         messages.add(Data("PACKET INVIATO", true))
         adapter.notifyItemInserted(messages.size - 1)
         binding.recyclerView.scrollToPosition(messages.size - 1)

     } else {
         Toast.makeText(this, "Dispositivo non connesso", Toast.LENGTH_SHORT).show()
     }
 }

     bluetoothManager.onPacketReceived = { packet ->

         val hex = packet.toList().joinToString(" ") { "%02X".format(it) }

         runOnUiThread {
             messages.add(Data(hex, false))
             adapter.notifyItemInserted(messages.size - 1)
             binding.recyclerView.scrollToPosition(messages.size - 1)
         }
     }

 binding.nextButton.setOnClickListener {
     val intent = Intent(this, Home::class.java)
     startActivity(intent)
 }
 observeState()

}

private fun observeState() {
 lifecycleScope.launch {
     repeatOnLifecycle(Lifecycle.State.STARTED) {
         launch {
             bluetoothManager.connectionState.collect { state ->
                 if (state is ConnectionState.Disconnected || state is ConnectionState.Error) {
                     Toast.makeText(
                         this@BluetoothActivity,
                         "Connessione persa!",
                         Toast.LENGTH_SHORT
                     ).show()
                     finish()
                 }

             }
         }
     }
 }
}
}*/