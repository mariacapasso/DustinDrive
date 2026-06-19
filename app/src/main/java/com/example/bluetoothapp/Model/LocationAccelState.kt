package com.example.bluetoothapp.Model

object LocationAccelState {
    @Volatile var latitude: Double? = null
    @Volatile var longitude: Double? = null
    @Volatile var avgAcceleration: Double? = null
    @Volatile var lastAvgAcceleration: Double? = 0.0
}