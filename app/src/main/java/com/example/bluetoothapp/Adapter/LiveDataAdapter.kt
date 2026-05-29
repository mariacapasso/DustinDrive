package com.example.bluetoothapp.Adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothapp.Model.LiveDataModel
import com.example.bluetoothapp.databinding.ActivityHomeBinding
import com.example.bluetoothapp.databinding.MonitoraggioItemBinding

class LiveDataAdapter(val items: MutableList<LiveDataModel>):
    RecyclerView.Adapter<LiveDataAdapter.ViewHolder>() {
        lateinit var context: Context
    class ViewHolder(val binding: MonitoraggioItemBinding):
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        p0: ViewGroup,
        p1: Int
    ): ViewHolder {
        context=p0.context
        val binding= MonitoraggioItemBinding.inflate(LayoutInflater.from(context),p0,false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder:ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.txtTitolo.text = item.titolo
        holder.binding.txtValore.text = item.valore
        holder.binding.txtStato.text = item.stato
        holder.binding.imgIcona.setImageResource(item.icona)

        // Logica dinamica per il colore
        if (item.valore == "ATTIVO") {
            holder.binding.txtValore.setTextColor(Color.GREEN)
            // Aggiungi un piccolo effetto bagliore
            holder.binding.txtValore.setShadowLayer(10f, 0f, 0f, Color.GREEN)
        } else {
            holder.binding.txtValore.setTextColor(Color.BLACK)
            holder.binding.txtValore.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
    }

    override fun getItemCount(): Int = items.size
}