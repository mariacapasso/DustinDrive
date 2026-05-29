package com.example.bluetoothapp

object TimeStampSync {
    private var espMillis: Long = 0L

    fun computeTimestamp(espTimestamp: Long): Long {
        if (espMillis == 0L) {
            espMillis = System.currentTimeMillis() - espTimestamp
        }
        return espMillis + espTimestamp
    }

    fun reset() {
        espMillis = 0L
    }


}