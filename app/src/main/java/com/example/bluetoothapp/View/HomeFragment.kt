package com.example.bluetoothapp.View

import com.example.bluetoothapp.Controller.BluetoothConnectionManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothapp.View.Adapter.ItemHomeAdapter
import com.example.bluetoothapp.View.Adapter.LiveDataAdapter
import com.example.bluetoothapp.Controller.ConnectionState
import com.example.bluetoothapp.Model.ItemHomeCard
import com.example.bluetoothapp.R
import com.example.bluetoothapp.Controller.SharedViewModel
import com.example.bluetoothapp.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    private lateinit var adapter: LiveDataAdapter
    private lateinit var adapter1: ItemHomeAdapter

    private lateinit var cardList: ArrayList<ItemHomeCard>

    private lateinit var bluetoothManager: BluetoothConnectionManager


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bluetoothManager = BluetoothConnectionManager.getInstance(requireContext())

        initMonitoraggioLive()
        initHomeCard()
        observeState()

        viewModel.exportResult.observe(viewLifecycleOwner){ msg ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }

    }

    // ---------------- LIVE DATA ----------------

    private fun initMonitoraggioLive() {
        adapter = LiveDataAdapter(mutableListOf())

        binding.dataView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.dataView.adapter = adapter

        viewModel.monitorItems.observe(viewLifecycleOwner) { items ->
            adapter.items.clear()
            adapter.items.addAll(items)
            adapter.notifyDataSetChanged()

            binding.progressBar.visibility = View.GONE
        }
    }

    // ---------------- HOME CARDS ----------------

    private fun initHomeCard() {
        cardList = arrayListOf()

        adapter1 = ItemHomeAdapter(cardList) { item ->
            gestisciClick(item)
        }

        binding.impostazioni.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.impostazioni.adapter = adapter1

        cardList.add(ItemHomeCard("Scan Bluetooth", "Cerca Dispositivi", R.drawable.b_home))
        cardList.add(ItemHomeCard("DashBoard", "Dati in tempo reale", R.drawable.dash_home))
        cardList.add(ItemHomeCard("Mappe", "Localizza Veicolo", R.drawable.map_home))

        adapter1.notifyDataSetChanged()
    }

    // ---------------- CLICK CARDS ----------------

    private fun gestisciClick(item: ItemHomeCard) {
        when (item.titolo) {
            "Scan Bluetooth" -> {
                (requireActivity() as MainActivity)
                    .replaceFragment(BluetoothFragment())
            }

            "DashBoard" -> {
                (requireActivity() as MainActivity)
                    .replaceFragment(DashBoardFragment())
            }

            "Mappe" -> {
                (requireActivity() as MainActivity)
                    .replaceFragment(MappeFragment())
            }
        }
    }

    // ---------------- BLUETOOTH STATE ----------------

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                bluetoothManager.connectionState.collect { state ->
                    binding.connDevice.text = when (state) {
                        is ConnectionState.Connected -> "Connesso"
                        is ConnectionState.Disconnected -> "Non Connesso"
                        is ConnectionState.Connecting -> "Connessione..."
                        is ConnectionState.Reconnecting -> "Riconnessione..."
                        is ConnectionState.Error -> "Errore: ${state.message}"
                    }
                    if (state is ConnectionState.Connecting || state is ConnectionState.Reconnecting) {
                        binding.progressBar.visibility = View.VISIBLE
                    } else {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}