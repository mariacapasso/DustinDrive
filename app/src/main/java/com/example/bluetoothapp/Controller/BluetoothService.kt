package com.example.bluetoothapp.Controller

import android.R
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BluetoothService : Service() {

    private val binder = LocalBinder()
    lateinit var manager : BluetoothConnectionManager
        private set

    inner class LocalBinder : Binder(){
        fun getService() = this@BluetoothService
    }

    override fun onCreate() {
        super.onCreate()
        manager = BluetoothConnectionManager.getInstance(this)
        createNotificationChannel()
    }


    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this,"bt_channel")
            .setContentTitle("DUSTIN connesso")
            .setContentText("Connessione Bluetooth Attiva")
            .setSmallIcon(R.drawable.stat_sys_data_bluetooth)
            .setSilent(true)
            .build()

        startForeground(1,notification)
        return  START_STICKY //super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("bt_channel","Bluetooth", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

    }



}