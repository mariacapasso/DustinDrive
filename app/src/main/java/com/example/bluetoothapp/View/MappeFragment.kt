package com.example.bluetoothapp.View

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.bluetoothapp.Controller.SharedViewModel
import com.example.bluetoothapp.R
import com.example.bluetoothapp.databinding.FragmentMappeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.Locale
import kotlin.collections.firstOrNull


class MappeFragment : Fragment(R.layout.fragment_mappe) {

    private var _binding: FragmentMappeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModels()

    private var marker: Marker?=null


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) checkLocationPermissions()//enableMyLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = requireContext().applicationContext
        Configuration.getInstance().userAgentValue=ctx.packageName
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMappeBinding.bind(view)

        // Configurazione base della mappa
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK) // Mappa stradale standard
        binding.mapView.setMultiTouchControls(true)             // Abilita il pinch-to-zoom
        binding.mapView.controller.setZoom(16.5)

        binding.mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
            binding.mapView.controller.setCenter(GeoPoint(41.9028, 12.4964)) // Roma di default
        }

        // Controlla i permessi del GPS
        checkLocationPermissions()

        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            val userPoint = GeoPoint(location.latitude, location.longitude)

            // Muove la mappa fluidamente
            binding.mapView.controller.animateTo(userPoint)

            // Gestione Marker
            marker?.let { binding.mapView.overlays.remove(it) }
            marker = Marker(binding.mapView).apply {
                position = userPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "La mia posizione!"
            }
            binding.mapView.overlays.add(marker)
            binding.mapView.invalidate()

            // Aggiorna l'indirizzo di testo (Geocoder)
            updateAddressText(location.latitude, location.longitude)
        }

    }
    private fun updateAddressText(lat: Double, lng: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        val line = addresses.firstOrNull()?.getAddressLine(0)
                        _binding?.addressLocation?.text = "La mia posizione: $line"
                    }
                }
            } catch (e: Exception) {
                binding.addressLocation.text = "Attivare Dati Mobili o Wi-Fi"
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                val addressLine = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        addresses?.firstOrNull()?.getAddressLine(0) ?: "Indirizzo non trovato"
                    } catch (e: Exception) {
                        "Attivare Dati Mobili o Wi-Fi"
                    }
                }
                _binding?.addressLocation?.text = "La mia posizione: $addressLine"
            }
        }
    }
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }


    private fun checkLocationPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)

        if (fineLocation != PackageManager.PERMISSION_GRANTED ) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}