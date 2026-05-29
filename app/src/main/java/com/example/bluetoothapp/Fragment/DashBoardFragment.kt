package com.example.bluetoothapp.Fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.bluetoothapp.R
import com.example.bluetoothapp.ViewModel.DashBoardViewModel
import com.example.bluetoothapp.databinding.FragmentDashBoardBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch

class DashBoardFragment : Fragment(R.layout.fragment_dash_board) {
    private var _binding: FragmentDashBoardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashBoardViewModel by viewModels()

    private var ultimoStatoTouch: Boolean? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashBoardBinding.bind(view)

        // Inizializzazione dei due grafici lineari
        setupChart(binding.chartPPG, "Dati PPG", resources.getColor(R.color.teal_700, null))
        setupChart(binding.chartTAC, "Dati TAC (Etanolo)", resources.getColor(R.color.purple_700, null))

        // 1. Osserva la Data/Ora convertita
        viewModel.formattedDate.observe(viewLifecycleOwner) { dataFormattata ->
            binding.txtLastUpdate.text = dataFormattata
        }

        // 2. Osserva il Touch del Volante (Caricamento GIF Dinamiche)
        viewModel.isVolanteToccato.observe(viewLifecycleOwner) { statoTouch ->
            val maniAppoggiate = statoTouch.first
            val testoDettaglio = statoTouch.second

            binding.txtElettrodiDettaglio.text = testoDettaglio

            // Controlliamo se lo stato è cambiato
            if (ultimoStatoTouch != maniAppoggiate) {
                ultimoStatoTouch = maniAppoggiate

                if (maniAppoggiate) {
                   binding.imgVolanteTouch.setImageResource(R.drawable.mani_on)
                } else {
                    binding.imgVolanteTouch.setImageResource(R.drawable.mani_off)
                }
            }
        }

        // 3. Osserva i Punti del Grafico PPG
        viewModel.ppgDataPoint.observe(viewLifecycleOwner) { point ->
            binding.txtPpgValue.text = "Frequenza Cardiaca: ${point.second.toInt()}"
            addEntryToChart(binding.chartPPG, point.first, point.second)
        }

        // 4. Osserva i Punti del Grafico TAC
        viewModel.tacDataPoint.observe(viewLifecycleOwner) { point ->
            binding.txtTacValue.text = "Livello Etanolo: ${point.second.toInt()} ppb"
            addEntryToChart(binding.chartTAC, point.first, point.second)
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

    private fun addEntryToChart(chart: LineChart, x: Float, y: Float) {
        val data = chart.data ?: return
        var set = data.getDataSetByIndex(0)

        if (set == null) {
            set = LineDataSet(ArrayList(), "Tracciato Real-Time")
            set.lineWidth = 3.0f
            set.setDrawCircles(false)
            set.setDrawValues(false)
            // Applichiamo le modifiche estetiche in base al grafico chiamante
            if (chart.id == R.id.chartPPG) {
                (set as LineDataSet).color = android.graphics.Color.RED
                set.mode = LineDataSet.Mode.LINEAR
                set.label = "Tracciato PPG"
            } else {
                (set as LineDataSet).color = android.graphics.Color.BLUE
                set.label = "Tracciato TAC"
                set.mode = LineDataSet.Mode.STEPPED
            }
            data.addDataSet(set)
        }

        data.addEntry(Entry(x, y), 0)
        data.notifyDataChanged()
        chart.notifyDataSetChanged()

        if (chart.id == R.id.chartPPG) {
            // Mostra 200 punti (2 secondi di storico).
            chart.setVisibleXRangeMaximum(200f)
            // Usiamo il posizionamento diretto sull'asse X per evitare lag.
            chart.moveViewToX(x - 200f)
        } else {
            // Per il TAC mostriamo una finestra più ampia
            chart.setVisibleXRangeMaximum(20f)
            chart.moveViewToX(x)
        }

        chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}