package com.example.bluetoothapp.View

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.example.bluetoothapp.R
import com.example.bluetoothapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.enableEdgeToEdge(window)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        removeLightStatusBar()
        binding.swipeRefreshLayout.setOnRefreshListener {
            // 1. Recupera il fragment che l'utente sta guardando in questo momento
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_layout)

            if (currentFragment != null) {
                // 2. Crea una nuova istanza pulita dello STESSO tipo di fragment
                val newInstance = currentFragment::class.java.getDeclaredConstructor().newInstance()

                // 3. Ricaricalo da capo
                replaceFragment(newInstance)
            }

            // 4. Spegne subito l'animazione del cerchio che gira
            binding.swipeRefreshLayout.isRefreshing = false
        }
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
          // Se l'utente va sulla mappa disattiviamo il refresh, negli altri casi lo attiviamo
        binding.swipeRefreshLayout.isEnabled = fragment !is MappeFragment

    }

    private fun removeLightStatusBar(){
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // Set light status bar (dark icons)
         controller.isAppearanceLightStatusBars= true
        controller.isAppearanceLightNavigationBars= true


    }


}

