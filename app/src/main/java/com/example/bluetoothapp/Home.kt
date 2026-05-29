package com.example.bluetoothapp

import BluetoothConnectionManager
import ConnectionState
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothapp.Adapter.ItemHomeAdapter
import com.example.bluetoothapp.Adapter.LiveDataAdapter
import com.example.bluetoothapp.Model.ItemHomeCard
import com.example.bluetoothapp.ViewModel.HomeViewModel
import com.example.bluetoothapp.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch


class Home : ComponentActivity() {
    private lateinit var binding: ActivityHomeBinding
    /*private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: LiveDataAdapter
    private lateinit var adapter1: ItemHomeAdapter
    lateinit var cardList: ArrayList<ItemHomeCard>
    lateinit var bluetoothManager: BluetoothConnectionManager*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)

        //binding.progressBar2.visibility= View.GONE
        setContentView(binding.root)
/*
        bluetoothManager = BluetoothConnectionManager.getInstance(this)
        observeState()
        initMonitoraggioLive()
        initHomeCard()

        openBluetooth()
        openDashBoard()

        openHome()
*/

    }
/*
   fun initMonitoraggioLive() {
        //Configurazione iniziale (si fa una volta sola)
        adapter = LiveDataAdapter(mutableListOf()) // Lista vuota all'inizio
        binding.dataView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.dataView.adapter = adapter

       // binding.progressBar.visibility = View.GONE

        // Osserva i cambiamenti dei dati
        viewModel.monitorItems.observe(this) { items ->
            // 1. Pulire la lista attuale dell'adapter
            adapter.items.clear()
            binding.progressBar.visibility = View.GONE
            // 2. Aggiungere tutti i nuovi elementi
            adapter.items.addAll(items)
            // 3. Notificare il cambiamento totale
            adapter.notifyDataSetChanged()

        }
    }

    fun initHomeCard(){
        //Card "Scan Bluetooth, Dashboard, Mappe"
        cardList = ArrayList()

        adapter1= ItemHomeAdapter(cardList, { itemCliccato -> gestisciNavigazione(itemCliccato) })
        binding.impostazioni.layoutManager= GridLayoutManager(this,2)
        // GridLayoutManager(this,2, GridLayoutManager.HORIZONTAL,false)
        binding.impostazioni.adapter= adapter1
        cardList.add(ItemHomeCard("Scan Bluetooth","Cerca Dispositivi", R.drawable.b_home))
        cardList.add(ItemHomeCard("DashBoard","Dati in tempo reale", R.drawable.dash_home))
        cardList.add(ItemHomeCard("Mappe","Localizza Veicolo", R.drawable.map_home))
        adapter1.notifyDataSetChanged()
        // binding.progressBar.visibility=View.GONE

    }

    private fun gestisciNavigazione(item: ItemHomeCard){
        val intent = when (item.titolo){
            "Scan Bluetooth" -> Intent(this, MainActivity::class.java)
            /* DA CAMBIARE*/
            "DashBoard" -> Intent(this, Home::class.java)
            "Mappe" ->Intent(this, SchermataIniziale::class.java)
            else -> throw
            IllegalArgumentException("Tipo di Activity non supportato!")

        }
        startActivity(intent)
    }





    fun openBluetooth(){
        binding.bluetooth.setOnClickListener {
        val intent= Intent(this, BluetoothActivity::class.java)
        startActivity(intent)
        }

    }

    fun openDashBoard(){
        binding.dashboard.setOnClickListener {
           // val intent=Intent(this,DashBoardActivity::class.java)
            startActivity(intent)
        }
    }

    fun openHome() {
        binding.home.setOnClickListener {
            /*binding.swipeRefresh.setOnClickListener {
                binding.background.setBackgroundColor(Color.parseColor("#555754"))

            }*/
        val intent= Intent(this, Home::class.java)
        startActivity(intent)
        }



        }


    private fun observeState() {

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bluetoothManager.connectionState.collect { state ->
                    // Aggiorna il testo della TextView in base allo stato
                    binding.connDevice.text = when (state) {
                        is ConnectionState.Connected -> "Connesso"
                        is ConnectionState.Disconnected -> "Non Connesso"
                        else -> "In connessione..."
                    }
                }
            }
        }
    }

*/
}