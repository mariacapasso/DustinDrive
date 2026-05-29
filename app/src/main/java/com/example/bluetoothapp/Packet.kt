package com.example.bluetoothapp

data class Packet (val type: Int,
val timestamp: Int,
val d1: Int,
val d2: Int,
val d3: Int) {
}