package com.example.bluetoothapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import com.example.bluetoothapp.databinding.ActivitySchermataInizialeBinding
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch



class SchermataIniziale : ComponentActivity() {

    private lateinit var binding: ActivitySchermataInizialeBinding
   // Registro il gestore per la richiesta permessi
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
    { permissions ->
        checkPermissions()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
       /* WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT*/

        binding = ActivitySchermataInizialeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Chiedo i permessi all'avvio
        /* Handler(Looper.getMainLooper()).postDelayed({
              checkPermissions()
          }, 500)*/

        binding.btnInizia.setOnClickListener {
           val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()//quando si torna indietro l'app si chiude
        }
        //observeState()
    }

   /* private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

            }
        }*/



 private fun checkPermissions() {
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
  }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissions()
        }, 500)
    }

}