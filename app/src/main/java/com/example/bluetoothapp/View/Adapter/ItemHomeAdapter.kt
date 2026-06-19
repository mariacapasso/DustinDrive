package com.example.bluetoothapp.View.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothapp.Model.ItemHomeCard
import com.example.bluetoothapp.databinding.HomeItemBinding
import androidx.core.graphics.toColorInt

class ItemHomeAdapter (val items: MutableList<ItemHomeCard>,
                       private val onItemClick:(ItemHomeCard)-> Unit):
    RecyclerView.Adapter<ItemHomeAdapter.ViewHolder>() {
    lateinit var context: Context


    class ViewHolder(val binding: HomeItemBinding):
        RecyclerView.ViewHolder(binding.root)


    override fun onCreateViewHolder(
        p0: ViewGroup,
        p1: Int
    ): ViewHolder {
        context=p0.context
        val binding= HomeItemBinding.inflate(LayoutInflater.from(context),p0,false)
        return ViewHolder(binding)

    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val item = items[position]
        holder.binding.txtTitolo.text = item.titolo
        holder.binding.txtUso.text = item.uso
        holder.binding.imgIcona.setImageResource(item.icona)


         holder.binding.clickCard.setOnClickListener{
                 holder.binding.clickCard.setBackgroundColor("#299E38".toColorInt())
             onItemClick(item)


         }

    }

    override fun getItemCount(): Int = items.size
    }




