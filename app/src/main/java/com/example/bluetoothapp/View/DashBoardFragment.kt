package com.example.bluetoothapp.View


import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import androidx.fragment.app.activityViewModels
import com.example.bluetoothapp.R
import com.example.bluetoothapp.Controller.SharedViewModel
import com.example.bluetoothapp.databinding.FragmentDashBoardBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.Locale


class DashBoardFragment : Fragment(R.layout.fragment_dash_board) {
    private var _binding: FragmentDashBoardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()// DashBoardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashBoardBinding.bind(view)

        // Inizializzazione grafico
        setupChart(binding.chartPPG, "Dati PPG", resources.getColor(R.color.teal_700, null))
        // Osserva l'accelerazione calcolata in background dal ViewModel
        viewModel.currentAvgAcceleration.observe(viewLifecycleOwner) { mediaAcc ->
            updateAccelerationUI(mediaAcc)
        }
        viewModel.formattedDate.observe(viewLifecycleOwner) { dataFormattata ->
            binding.txtLastUpdate.text = dataFormattata.substringBeforeLast(" ")
        }

        viewModel.ppgDataPoint.observe(viewLifecycleOwner) { point ->
            binding.txtPpgValue.text = "Frequenza Cardiaca: ${point.second.toInt()}"
            addEntryPPG(point.first, point.second)
        }
        viewModel.currentMaxG.observe(viewLifecycleOwner) { maxG ->
            binding.txtMaxG.text = String.format(Locale.ITALY, "%.2f m/s²", maxG)
        }
    }

    private fun setupChart(chart: LineChart, label: String, coloreLinea: Int) {

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

    }

    private fun addEntryPPG(x: Float, y: Float) {
        val data = binding.chartPPG.data ?: return
        var set = data.getDataSetByIndex(0)
        if (set == null) {
            set = LineDataSet(ArrayList(), "Tracciato PPG")
            set.lineWidth = 3.0f
            set.setDrawCircles(false)
            set.setDrawValues(false)
            set.color = Color.RED
            set.mode = LineDataSet.Mode.LINEAR
            data.addDataSet(set)
        }
        data.addEntry(Entry(x, y), 0)
        data.notifyDataChanged()
        binding.chartPPG.notifyDataSetChanged()
        // Mostra 200 punti (2 secondi di storico).
        binding.chartPPG.setVisibleXRangeMaximum(200f)
        // Usiamo il posizionamento diretto sull'asse X per evitare lag.
        binding.chartPPG.moveViewToX(x - 200f)

    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateAccelerationUI(mediaAcc: Double) {
        binding.txtAcceleration.text = String.format(Locale.ITALY, "%.2f m/s²", mediaAcc)

        // Semplificato il controllo basandoci sulla media direttamente
        when {
            mediaAcc > 14.0 -> {
                binding.txtStatusDrive.text = "GUIDA PERICOLOSA"
                binding.txtStatusDrive.setTextColor(Color.RED)
            }

            mediaAcc > 5.0 -> {
                binding.txtStatusDrive.text = "FRENATA BRUSCA"
                binding.txtStatusDrive.setTextColor(Color.parseColor("#C98F3E"))
            }

            mediaAcc > 1.5 -> {
                binding.txtStatusDrive.text = "GUIDA MODERATA"
                binding.txtStatusDrive.setTextColor(Color.parseColor("#FCB045"))
            }

            else -> {
                binding.txtStatusDrive.text = "GUIDA SICURA"
                binding.txtStatusDrive.setTextColor(Color.GREEN)
            }
        }
    }
}



