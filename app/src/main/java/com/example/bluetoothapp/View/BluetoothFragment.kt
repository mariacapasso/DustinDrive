package com.example.bluetoothapp.View

import com.example.bluetoothapp.Controller.BluetoothConnectionManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android .bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothapp.View.Adapter.DeviceAdapter
import com.example.bluetoothapp.Controller.BluetoothService
import com.example.bluetoothapp.Controller.ConnectionState
import com.example.bluetoothapp.Controller.SharedViewModel
import com.example.bluetoothapp.databinding.FragmentBluetoothBinding
import kotlinx.coroutines.launch
import kotlin.getValue


class BluetoothFragment : Fragment() {
    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!

    private lateinit var bluetoothManager: BluetoothConnectionManager
    private lateinit var deviceListAdapter: DeviceAdapter
    private val viewModel: SharedViewModel by activityViewModels()

    private var bluetoothService: BluetoothService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bluetoothService = (binder as BluetoothService.LocalBinder).getService()
            bluetoothManager = bluetoothService!!.manager
            // ora che il manager è pronto, inizializza la UI
            initRicercaDispositivi()
            observeState()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothService = null
            serviceBound = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val intent = Intent(requireContext(), BluetoothService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        serviceBound = requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        viewModel.exportResult.observe(viewLifecycleOwner){ msg ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }
       // bluetoothManager = BluetoothConnectionManager.getInstance(requireContext())

        //initRicercaDispositivi()
       // observeState()

    private fun initRicercaDispositivi() {

        deviceListAdapter = DeviceAdapter(
            mutableListOf(),
            onConnectClick = { device -> connettiDispositivo(device) },
            onDisconnectClick = { disconnettiDispositivo() }
        )

        binding.listaDispositivi.layoutManager = LinearLayoutManager(requireContext())
        binding.listaDispositivi.adapter = deviceListAdapter

        binding.ricercaBtn.setOnClickListener {
            if (bluetoothManager.isBluetoothEnabled() && bluetoothManager.isLocationEnabled()) {
                binding.emptyState.visibility = View.GONE
                bluetoothManager.startDiscovery()
            } else {
                checkBluetoothAndLocation()
            }

        }

    }
    private fun checkBluetoothAndLocation(){
        if (!isAdded) return
        val bluetooth_enabled=bluetoothManager.isBluetoothEnabled()
        val location_enabled=bluetoothManager.isLocationEnabled()


        if (!bluetooth_enabled) {
            val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            showPopup("Il Bluetooth è spento. Attivalo per cercare i dispositivi.",btIntent)
            return
        }

        if (!location_enabled) {
            val locIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
           showPopup("La geolocalizzazione è necessaria per la scansione Bluetooth.",locIntent)
            return
        }
    }

    fun showPopup(message: String, action: Intent) {
        if (!isAdded) return
       // context?.let{safeContext ->
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Operazione necessaria!")
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setPositiveButton("Vai in impostazioni") { dialog, _ ->
            startActivity(action)
            dialog.dismiss()
        }
        builder.setNegativeButton("Annulla") { dialog, _ ->
            dialog.dismiss()
            Toast.makeText(requireContext(), "Funzionalità limitate", Toast.LENGTH_SHORT).show()
        }
            builder.show()
    }
   // }

    private fun connettiDispositivo(nome: BluetoothDevice) {
        bluetoothManager.connect(nome)

    }

    private fun disconnettiDispositivo() {
        bluetoothManager.disconnect()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    bluetoothManager.discoveredDevices.collect { devices ->
                        deviceListAdapter.updateList(devices)
                        //binding.badgeCount.text = devices.size.toString()

                        // Mostra/nascondi empty state
                        val isEmpty = devices.isEmpty()
                        binding.emptyState.visibility =
                            if (isEmpty) View.VISIBLE else View.GONE
                        binding.listaDispositivi.visibility =
                            if (isEmpty) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    bluetoothManager.connectionState.collect { state ->
                       // val safeContext = context ?: return@collect
                        when (state) {
                            is ConnectionState.Connected -> {
                                deviceListAdapter.refreshConnectionState(state.device.address)
                                binding.ricercaBtn.isEnabled = true
                                binding.ricercaBtn.text = "Ricerca dispositivi"
                            }

                            is ConnectionState.Disconnected -> {
                              deviceListAdapter.refreshConnectionState(null)
                                binding.ricercaBtn.isEnabled = true
                                binding.ricercaBtn.text = "Ricerca dispositivi"

                            }
                            is ConnectionState.Connecting -> {
                                // Disabilita le interazioni utente per non sovrascrivere l'azione
                                binding.ricercaBtn.isEnabled = false
                                binding.ricercaBtn.text = "Connessione..."
                            }

                            is ConnectionState.Reconnecting -> {
                                // Cambia il testo del bottone principale e lo blocca temporaneamente
                                binding.ricercaBtn.isEnabled = false
                                binding.ricercaBtn.text = "Tentativo di riconnessione..."

                                Toast.makeText(requireContext(), "Connessione persa. Riconnessione automatica...", Toast.LENGTH_SHORT).show()
                            }

                            is ConnectionState.Error -> {
                                binding.ricercaBtn.isEnabled = true
                                binding.ricercaBtn.text = "Ricerca dispositivi"
                                Toast.makeText(requireContext(), "Errore: ${state.message}", Toast.LENGTH_LONG).show()
                            }
                        }

                    }

                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

    }


    override fun onDestroyView() {
        super.onDestroyView()
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        _binding = null
    }
}