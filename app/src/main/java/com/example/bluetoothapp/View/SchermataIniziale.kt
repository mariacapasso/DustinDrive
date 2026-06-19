package com.example.bluetoothapp.View

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.bluetoothapp.databinding.ActivitySchermataInizialeBinding
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat




class SchermataIniziale : ComponentActivity() {

    private lateinit var binding: ActivitySchermataInizialeBinding
   // Registro il gestore per la richiesta permessi
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
    {  if (hasAllPermissions()) {
        Toast.makeText(
            this,
            "TUTTI PERMESSI CONCESSI!",
            Toast.LENGTH_LONG
        ).show()
        goToMain()
    } else {
        Toast.makeText(
            this,
            "Alcuni permessi NON concessi",
            Toast.LENGTH_LONG
        ).show()
    }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)

        binding = ActivitySchermataInizialeBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.btnInizia.setOnClickListener {
            if (hasAllPermissions()) {
                goToMain()
            } else {
                checkPermissions()
            }

        }

    }
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    private fun hasAllPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
 private fun checkPermissions() {
  val permissions = mutableListOf<String>()

   // Se siamo su Android 12 o superiore
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
       permissions.add(Manifest.permission.BLUETOOTH_SCAN)
       permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
      permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
   } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ){
       // Per Android 11 o inferiore serve solo la posizione
       permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
   }
   // Lanciamo la richiesta
   requestPermissionLauncher.launch(permissions.toTypedArray())
}


}