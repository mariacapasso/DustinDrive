package com.example.bluetoothapp.Fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import android.graphics.Color
import android.graphics.Color.parseColor
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.lifecycleScope
import com.example.bluetoothapp.R
import com.example.bluetoothapp.databinding.FragmentMappeBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import kotlin.compareTo
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.text.toFloat


class MappeFragment : Fragment(R.layout.fragment_mappe), SensorEventListener {

    private var _binding: FragmentMappeBinding? = null
    private val binding get() = _binding!!

    private lateinit var  mSensorManager: SensorManager
    private var accelerometer: Sensor? = null

    /*val xValue= mutableStateOf("")
    val yValue= mutableStateOf("")
    val zValue= mutableStateOf("")*/

    var accelerationCurrenValue: Double = 0.0
    var accelerationPrevValue: Double= 0.0
    var changeInAcceleration: Double=0.0

    private lateinit var locationOverlay: MyLocationNewOverlay

    private val campionamentoTime = 50L //tempo di campionament ogni 0.05s
    private val updateTime=15000L //tempo di aggiornamento ogni 15s
    private var lastUpdateTime=0L //tempo ultimo aggiornamento

    data class AccelerometerData(private val x:Float, private val y: Float, private val z: Float, val forzaG: Double){}
    private val accelBuffer= mutableListOf<AccelerometerData>()
    private lateinit var timerJob: Job
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){ permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) enableMyLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = requireContext().applicationContext
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMappeBinding.bind(view)

        mSensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

        accelerometer =mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // setupChart(binding.chartAccel,"Analisi della forza G durante il tragitto",resources.getColor(R.color.teal_700, null))



        // Configurazione base della mappa
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK) // Mappa stradale standard
        binding.mapView.setMultiTouchControls(true)             // Abilita il pinch-to-zoom
        binding.mapView.controller.setZoom(16.5)

        // Controlla i permessi del GPS
        checkLocationPermissions()

        binding.chartAccel.description.isEnabled=false
        binding.chartAccel.setPinchZoom(true)
        binding.chartAccel.setDrawGridBackground(false)
        binding.chartAccel.isDragEnabled=true
        binding.chartAccel.setScaleEnabled(true)
        binding.chartAccel.setTouchEnabled(true)

        val xAxis: XAxis = binding.chartAccel.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity=1f
        xAxis.textColor=getColor(requireContext(),R.color.teal_700)
        xAxis.setDrawAxisLine(false)

        binding.chartAccel.axisRight.isEnabled=false

       /* val yAxis = binding.chartAccel.axisLeft
        yAxis.textColor=getColor(requireContext(),R.color.purple_500)

        binding.chartAccel.animateXY(1500,1500)*/

        val data = LineData()
        binding.chartAccel.data=data
    }

    private fun checkLocationPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocation == PackageManager.PERMISSION_GRANTED && coarseLocation == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun enableMyLocation() {
        // Crea il provider GPS e l'overlay della posizione grafica
        val provider = GpsMyLocationProvider(requireContext())
        locationOverlay = MyLocationNewOverlay(provider, binding.mapView)

        // Attiva la localizzazione e mostra l'icona dell'utente sulla mappa
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation() // Segue l'utente al centro dello schermo mentre cammina/guida

        // Opzionale: Mostra la freccia della direzione basata sulla bussola del telefono
        // locationOverlay.enableCompass()

        // Aggiunge l'overlay alla mappa
        binding.mapView.overlays.add(locationOverlay)

        // Sposta temporaneamente la mappa su una coordinata iniziale fissa (es. Roma)
        // finché il GPS non aggancia la posizione reale
        binding.mapView.controller.setCenter(GeoPoint(41.9028, 12.4964))
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this,accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        binding.mapView.onResume() // Necessario per il ciclo di vita di osmdroid

        // AVVIO DEL LOOP TEMPORALE CICLICO (15 SECONDI)
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(updateTime) // Aspetta 15 secondi in modo asincrono
                refreshUI()       // Aggiorna i testi
            }
        }

        if (::locationOverlay.isInitialized) {
            locationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()

        mSensorManager.unregisterListener(this)
        binding.mapView.onPause() // Necessario per il ciclo di vita di osmdroid
        // INTERRUZIONE DEL LOOP TEMPORALE PER EVITARE MEMORY LEAK IN BACKGROUND
        timerJob.cancel()
        if (::locationOverlay.isInitialized) {
            locationOverlay.disableMyLocation()
            //locationOverlay.disableCompass()
        }
    }
    /* private fun setupChart(chart: LineChart, label: String, coloreLinea: Int) {
         chart.description.isEnabled = false
         chart.setTouchEnabled(false)
         chart.isDragEnabled = false
         chart.setScaleEnabled(false)
         chart.setDrawGridBackground(false)

         val data = LineData()
         chart.data = data

         chart.setHardwareAccelerationEnabled(true)

         chart.xAxis.isEnabled = false
         chart.axisLeft.setDrawGridLines(true)
         chart.axisRight.isEnabled = false
     }*/


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val currTime= System.currentTimeMillis()

        if (currTime - lastUpdateTime >= campionamentoTime) {
            lastUpdateTime = currTime

            val xValue = event.values[0]
            val yValue = event.values[1]
            val zValue = event.values[2]


            accelerationCurrenValue = sqrt((xValue * xValue) + (yValue * yValue) + (zValue * zValue)).toDouble()
            changeInAcceleration = abs(accelerationCurrenValue - accelerationPrevValue)
            accelerationPrevValue = accelerationCurrenValue

            accelBuffer.add(AccelerometerData(xValue, yValue, zValue, changeInAcceleration))
            updateChart(changeInAcceleration)
        }
        /*binding.txtAccel.text = "Acceleration: ${changeInAcceleration.toInt()}"
        binding.txtCurrAccel.text = "Current Acceleration: ${accelerationCurrenValue.toInt()}"
        binding.txtPrevAccel.text ="Prev Acceleration: ${accelerationPrevValue.toInt()}"

        if(changeInAcceleration > 14){
            //color RED
        }else if (changeInAcceleration > 5){
            //color ORANGE
        }else if (changeInAcceleration > 2){
            //color YELLOW
        }else{
            //color DEFAULT
        }*/

      /******  val mediaAcc= accelBuffer.map { it.forzaG }.average()
        val maxG= accelBuffer.map { it.forzaG }.maxOrNull() ?: 0.0

        binding.txtAcceleration.text= String.format("%.2f m/s²", mediaAcc)
        binding.txtMaxG.text=String.format("%.2f m/s²", maxG)*****/

       /* binding.txtAsseX.text=String.format("%.2f m/s²", xValue)
        binding.txtAsseY.text=String.format("%.2f m/s²", yValue)
        binding.txtAsseZ.text=String.format("%.2f m/s²", zValue)*/
/*  when{
            maxG > 14.0 -> {
                binding.txtStatusDrive.text="GUIDA PERICOLOSA"
                binding.txtStatusDrive.setTextColor(Color.RED)
            }
            maxG > 5.0 -> {
                binding.txtStatusDrive.text="FRENATA BRUSCA"
                binding.txtStatusDrive.setTextColor(parseColor("#C98F3E"))
            }
            maxG > 1.5 -> {
                binding.txtStatusDrive.text="GUIDA MODERATA"
                binding.txtStatusDrive.setTextColor(parseColor("#FCB045"))
            }
            else -> {
            binding.txtStatusDrive.text="GUIDA SICURA"
            binding.txtStatusDrive.setTextColor(Color.GREEN)
        }

        }
        val lineData = binding.chartAccel.data ?: return

        if(lineData != null){
            var set: ILineDataSet? = lineData.getDataSetByIndex(0)
           // var set2: ILineDataSet? = lineData.getDataSetByIndex(1)
          //  var set3: ILineDataSet? = lineData.getDataSetByIndex(2)

            if (set == null) /*|| set2 == null || set3 == null)*/{
                set = createSet (getColor(requireContext(),R.color.purple_700))
                lineData.addDataSet(set)

                /*set2 = createSet (getColor(requireContext(),R.color.blue))
                lineData.addDataSet(set2)

                set3 = createSet (getColor(requireContext(),R.color.teal_200))
                lineData.addDataSet(set3)*/

            }

            lineData.addEntry(Entry(set!!.entryCount.toFloat(), maxG.toFloat()),0)
           /* lineData.addEntry(Entry(set2!!.entryCount.toFloat(),yValue),1)
            lineData.addEntry(Entry(set3!!.entryCount.toFloat(),zValue),2)*/

            if(set.entryCount > 25){
                set.removeFirst()
                for (i in 0 until set.entryCount){
                    val entry=set.getEntryForIndex(i)
                    entry.x = entry.x - 1

                }
            }

            /*if(set2.entryCount > 25){
                set2.removeFirst()
                for (i in 0 until set2.entryCount){
                    val entry=set2.getEntryForIndex(i)
                    entry.x = entry.x - 1

                }
            }

            if(set3.entryCount > 25){
                set3.removeFirst()
                for (i in 0 until set3.entryCount){
                    val entry=set3.getEntryForIndex(i)
                    entry.x = entry.x - 1

                }
            }*/

            lineData.notifyDataChanged()
            binding.chartAccel.notifyDataSetChanged()
            binding.chartAccel.invalidate()

            accelBuffer.clear()
        }*/




    }
    private fun updateChart(currValue: Double){
        val lineData = binding.chartAccel.data ?: return

        // if(lineData != null){
        var set: ILineDataSet? = lineData.getDataSetByIndex(0)
        // var set2: ILineDataSet? = lineData.getDataSetByIndex(1)
        //  var set3: ILineDataSet? = lineData.getDataSetByIndex(2)

        if (set == null) /*|| set2 == null || set3 == null)*/{
            set = createSet (getColor(requireContext(),R.color.purple_700))
            lineData.addDataSet(set)

            /*set2 = createSet (getColor(requireContext(),R.color.blue))
            lineData.addDataSet(set2)

            set3 = createSet (getColor(requireContext(),R.color.teal_200))
            lineData.addDataSet(set3)*/

        }

        lineData.addEntry(Entry(set!!.entryCount.toFloat(), currValue.toFloat()),0)
        /* lineData.addEntry(Entry(set2!!.entryCount.toFloat(),yValue),1)
         lineData.addEntry(Entry(set3!!.entryCount.toFloat(),zValue),2)*/

        if(set.entryCount > 25){
            set.removeFirst()
            for (i in 0 until set.entryCount){
                val entry=set.getEntryForIndex(i)
                entry.x = entry.x - 1

            }
        }

        /*if(set2.entryCount > 25){
            set2.removeFirst()
            for (i in 0 until set2.entryCount){
                val entry=set2.getEntryForIndex(i)
                entry.x = entry.x - 1

            }
        }

        if(set3.entryCount > 25){
            set3.removeFirst()
            for (i in 0 until set3.entryCount){
                val entry=set3.getEntryForIndex(i)
                entry.x = entry.x - 1

            }
        }*/

        lineData.notifyDataChanged()
        binding.chartAccel.notifyDataSetChanged()
        binding.chartAccel.invalidate()

    }

    private fun refreshUI(){
        if (_binding == null || accelBuffer.isEmpty()) return

        val mediaAcc= accelBuffer.map { it.forzaG }.average()
        val maxG= accelBuffer.map { it.forzaG }.maxOrNull() ?: 0.0

        binding.txtAcceleration.text= String.format("%.2f m/s²", mediaAcc)
        binding.txtMaxG.text=String.format("%.2f m/s²", maxG)

        when{
            maxG > 14.0 -> {
                binding.txtStatusDrive.text="GUIDA PERICOLOSA"
                binding.txtStatusDrive.setTextColor(Color.RED)
            }
            maxG > 5.0 -> {
                binding.txtStatusDrive.text="FRENATA BRUSCA"
                binding.txtStatusDrive.setTextColor(parseColor("#C98F3E"))
            }
            maxG > 1.5 -> {
                binding.txtStatusDrive.text="GUIDA MODERATA"
                binding.txtStatusDrive.setTextColor(parseColor("#FCB045"))
            }
            else -> {
                binding.txtStatusDrive.text="GUIDA SICURA"
                binding.txtStatusDrive.setTextColor(Color.GREEN)
            }

        }


            accelBuffer.clear()



    }

    private fun createSet(color: Int): ILineDataSet? {
        val lineDataSet = LineDataSet(null, "")
        lineDataSet.mode= LineDataSet.Mode.CUBIC_BEZIER
        lineDataSet.cubicIntensity=0.4f
        lineDataSet.setDrawFilled(false)
        lineDataSet.setDrawCircles(false)
        lineDataSet.lineWidth=1.8f
        lineDataSet.circleRadius=4f
        lineDataSet.setCircleColor(getColor(requireContext(),R.color.teal_700))
        lineDataSet.highLightColor= Color.rgb(244,117,117)
        lineDataSet.color =color
        lineDataSet.fillAlpha=100
        lineDataSet.setDrawHorizontalHighlightIndicator(false)
        lineDataSet.setDrawValues(false)
        return lineDataSet

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}