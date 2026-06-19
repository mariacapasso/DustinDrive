package com.example.bluetoothapp.Model

object TimeStampSync {
    private var espMillis: Long = 0L
    private var isSynced: Boolean = false

    // chiamato una volta sola quando invii il SyncPacket
    fun onSyncSent(espTimestampSync: Long) {
            espMillis = System.currentTimeMillis() - espTimestampSync
            isSynced = true

    }

    // chiamato su ogni pacchetto ricevuto
    fun computeTimestamp(espTimestamp: Long): Long {
        if (!isSynced) return System.currentTimeMillis()
        return espMillis + espTimestamp
    }

    fun reset() {
        espMillis = 0L
        isSynced = false
    }


}