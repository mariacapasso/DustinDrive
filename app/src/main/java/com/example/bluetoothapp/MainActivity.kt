package com.example.bluetoothapp

import BluetoothConnectionManager
import ConnectionState
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bluetoothapp.Fragment.BluetoothFragment
import com.example.bluetoothapp.Fragment.DashBoardFragment
import com.example.bluetoothapp.Fragment.HomeFragment
import com.example.bluetoothapp.Fragment.MappeFragment
import com.example.bluetoothapp.Model.SyncPacket
import com.example.bluetoothapp.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    //lateinit var bluetoothManager: BluetoothConnectionManager
   // private lateinit var deviceListAdapter: DeviceAdapter
   // private val deviceMap = mutableMapOf<String, BluetoothDevice>()
   // private var currentDialog: AlertDialog? = null
    //private var userHasCancelled = false
    private lateinit var binding: ActivityMainBinding

    // Registro il gestore per la richiesta permessi
   /* private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            //checkBluetoothAndLocation()
        }*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // WindowCompat.enableEdgeToEdge(window)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        replaceFragment(HomeFragment())

        binding.bottomNavigationView2.setOnItemSelectedListener {

            when(it.itemId){

                R.id.nav_home -> replaceFragment(HomeFragment())
                R.id.nav_bluetooth -> replaceFragment(BluetoothFragment())
                R.id.nav_dashboard -> replaceFragment(DashBoardFragment())
                R.id.nav_mappe-> replaceFragment(MappeFragment())

                else ->{



                }

            }

            true

        }



    }
    fun replaceFragment(fragment : Fragment){

        supportFragmentManager.beginTransaction()
       .replace(R.id.frame_layout,fragment)
       .commit()


    }

}
/*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       // WindowCompat.enableEdgeToEdge(window)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothManager = BluetoothConnectionManager.getInstance(this)

        // Chiedo i permessi all'avvio
      /*  Handler(Looper.getMainLooper()).postDelayed({
            checkPermissions()
        }, 500)*/


        deviceListAdapter = DeviceAdapter(this, mutableListOf())
        binding.deviceList.adapter = deviceListAdapter

        binding.deviceList.setOnItemClickListener { _, _, position, _ ->
            val label = deviceListAdapter.getItem(position) ?: return@setOnItemClickListener
            val device = deviceMap[label] ?: return@setOnItemClickListener
            bluetoothManager.connect(device)
            val syncPacketModel = SyncPacket()
            val bytesToSend = syncPacketModel.costruisci_pacchetto()
            bluetoothManager.sendPacket(bytesToSend)

        }

        binding.scanButton.setOnClickListener {
            userHasCancelled = false
            if (bluetoothManager.isBluetoothEnabled() && bluetoothManager.isLocationEnabled()) {
                bluetoothManager.startDiscovery()
            } else {
                checkBluetoothAndLocation()
            }
        }
 
        val nxtbutton = findViewById<Button>(R.id.nextButton)

        nxtbutton.setOnClickListener {
             val stato = bluetoothManager.connectionState.value
            if(stato is ConnectionState.Connected){
                val intent = Intent(this, BluetoothActivity::class.java)
                intent.putExtra("EXTRA_DEVICE",stato.device)
                startActivity(intent)
            }else {
                Toast.makeText(this,"Connettiti prima ad un dispositivo!", Toast.LENGTH_SHORT).show()
            }
        }

        observeState()
    }




   /* private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        // Se siamo su Android 12 o superiore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Per Android 11 o inferiore serve solo la posizione
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Lanciamo la richiesta
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }*/

    private fun checkBluetoothAndLocation(){
        val bluetooth_enabled=bluetoothManager.isBluetoothEnabled()
        val location_enabled=bluetoothManager.isLocationEnabled()


        if (!bluetooth_enabled) {
            val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            showPopup("Il Bluetooth è spento. Attivalo per cercare i dispositivi.", btIntent)
            return // Esci per non sovrapporre i popup
        }

        if (!location_enabled) {
            val locIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            showPopup("La geolocalizzazione è necessaria per la scansione Bluetooth.", locIntent)
            return
        }
    }

    fun showPopup(message: String, action: Intent) {
     
        if (currentDialog?.isShowing == true) return

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Operazione necessaria!")
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setPositiveButton("Vai in impostazioni") { dialog, _ ->
            userHasCancelled = false
            startActivity(action)
            dialog.dismiss()
        }
        builder.setNegativeButton("Annulla") { dialog, _ ->
            userHasCancelled = true
            dialog.dismiss()
            Toast.makeText(this, "Funzionalità limitate", Toast.LENGTH_SHORT).show()
        }

        currentDialog = builder.create()
        currentDialog?.show()
    }
        @SuppressLint("MissingPermission")
        private fun observeState() {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        bluetoothManager.discoveredDevices.collect { devices ->
                            deviceMap.clear()
                            val names = devices.map { device ->
                                @SuppressLint("MissingPermission")
                                val label = "${device.name ?: "Sconosciuto"} - ${device.address}"
                                deviceMap[label] = device
                                label
                            }
                            deviceListAdapter.clear()
                            deviceListAdapter.addAll(names)
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }
                    launch {
                        bluetoothManager.connectionState.collect { state ->
                            binding.connectionStatus.text = when (state) {
                                is ConnectionState.Disconnected -> "Stato: Disconnesso"
                                is ConnectionState.Connecting -> "Stato: Connessione in corso..."
                                is ConnectionState.Connected -> "Stato: Connesso a ${state.device.name}"
                                is ConnectionState.Error -> "Errore: ${state.message}"

                            }
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }

                }
            }
        }

            override fun onDestroy() {
                super.onDestroy()
                bluetoothManager.stopDiscovery()
            }

            override fun onResume() {
                super.onResume()
                if (!userHasCancelled && currentDialog?.isShowing != true) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Ricontrolla se nel frattempo non è apparso un dialogo
                        if (currentDialog?.isShowing != true) {
                            checkBluetoothAndLocation()
                        }
                    }, 800)
                }

            }*/

           /* inner class DeviceAdapter(context: Context, devices: MutableList<String>) :
                ArrayAdapter<String>(context, R.layout.list_item, devices) {

                @SuppressLint("MissingPermission")
                override fun getView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    // Riutilizziamo la vista se esiste, altrimenti la creiamo
                    val view =
                        convertView ?: layoutInflater.inflate(R.layout.list_item, parent, false)

                    val disconnectBtn = view.findViewById<Button>(R.id.disconnetti_bt)
                    val nameText = view.findViewById<TextView>(R.id.deviceNameText)

                    val label = getItem(position) ?: ""
                    nameText.text = label

                    val device = deviceMap[label]
                    val currentState = bluetoothManager.connectionState.value

                    // Logica visibilità bottone
                    if (currentState is ConnectionState.Connected && currentState.device.address == device?.address) {
                        disconnectBtn.visibility = View.VISIBLE
                    } else {
                        disconnectBtn.visibility = View.GONE
                    }

                    disconnectBtn.setOnClickListener {
                        bluetoothManager.disconnect()

                    }

                    return view
                }
            }
        }*/


