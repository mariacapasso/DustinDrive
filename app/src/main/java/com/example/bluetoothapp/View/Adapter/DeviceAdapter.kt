package com.example.bluetoothapp.View.Adapter

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothapp.R
import com.google.android.material.button.MaterialButton


class DeviceAdapter (
    val devices: MutableList<  BluetoothDevice>,
    private var onConnectClick: (BluetoothDevice) -> Unit,
    private val onDisconnectClick: (BluetoothDevice) -> Unit):
    RecyclerView.Adapter<DeviceAdapter.ViewHolder>()  {

    var connectedAddress: String? = null

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
           val name: TextView = itemView.findViewById(R.id.nomeDispositivo)
           val disconnetti: MaterialButton = itemView.findViewById(R.id.disconnettibtn)
           val stato: TextView = itemView.findViewById(R.id.dispositivo_connesso_txt)
       }


    override fun onCreateViewHolder(
        p0: ViewGroup,
        p1: Int
    ): ViewHolder {
        val view = LayoutInflater.from(p0.context).inflate(R.layout.device_item,p0,false)
        return ViewHolder(view)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val deviceName = devices[position]
        holder.name.text = deviceName.name ?: deviceName.address

        val isConnected = deviceName.address == connectedAddress
        if (isConnected){

            holder.disconnetti.visibility =  View.VISIBLE
            holder.stato.visibility=  View.VISIBLE


        holder.itemView.setOnClickListener (null)
        holder.disconnetti.setOnClickListener {
            onDisconnectClick(deviceName)

        }
        } else{
            holder.disconnetti.visibility =  View.GONE
            holder.stato.visibility=  View.GONE


            holder.itemView.setOnClickListener {
                onConnectClick(deviceName)
            }
        }


    }

    override fun getItemCount(): Int = devices.size
    //aggiorna lista dispositivi e mette il dispositivo connesso in cima
    fun updateList(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

//aggiorna lo stato di connessione
    fun refreshConnectionState(address: String?){
        connectedAddress = address
        val sorted = devices.sortedByDescending { it.address == connectedAddress }
        devices.clear()
        devices.addAll(sorted)
        notifyDataSetChanged()
    }

}



