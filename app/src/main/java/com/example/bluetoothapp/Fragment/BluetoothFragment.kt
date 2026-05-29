package com.example.bluetoothapp.Fragment

import BluetoothConnectionManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android .bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothapp.Adapter.DeviceAdapter
import com.example.bluetoothapp.Model.SyncPacket
import com.example.bluetoothapp.R
import com.example.bluetoothapp.databinding.FragmentBluetoothBinding
import kotlinx.coroutines.launch


class BluetoothFragment : Fragment() {
    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!

    private lateinit var bluetoothManager: BluetoothConnectionManager
    private lateinit var deviceListAdapter: DeviceAdapter

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

        bluetoothManager = BluetoothConnectionManager.getInstance(requireContext())

        //init()
        initRicercaDispositivi()
        observeState()
    }

   /* private fun init(){
        connectedAdapter = DeviceAdapter(mutableListOf(),{},{bluetoothManager.disconnect()})

       /* binding.listaConnesso.layoutManager = LinearLayoutManager(requireContext())
        binding.listaConnesso.adapter = connectedAdapter*/
    }*/
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
            .show()
    }

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
                        binding.badgeCount.text = devices.size.toString()

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
                        when (state) {
                            is ConnectionState.Connected -> {
                               // deviceListAdapter.connectedAddress = state.device.address
                                deviceListAdapter.refreshConnectionState(state.device.address)
                                val syncPacketModel = SyncPacket()
                                val bytesToSend = syncPacketModel.costruisci_pacchetto()
                                bluetoothManager.sendPacket(bytesToSend)

                            }

                            is ConnectionState.Disconnected -> {
                              deviceListAdapter.refreshConnectionState(null)
                            }

                            else -> {}
                        }

                        //deviceListAdapter.notifyDataSetChanged()
                    }

                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            checkBluetoothAndLocation()
        }, 800)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}