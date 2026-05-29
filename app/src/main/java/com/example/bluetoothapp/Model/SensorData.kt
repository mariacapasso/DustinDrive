package com.example.bluetoothapp.Model

data class SensorData(
    val type: SensorTYPE,
    val value:Float,
    val label: String,
    val stato: String,
    val timestamp:Long)
