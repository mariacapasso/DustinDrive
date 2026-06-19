package com.example.bluetoothapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.bluetoothapp.Model.SensorTYPE

@Entity(tableName = "Packet")
data class SensorDataEntity(
    @PrimaryKey(autoGenerate = true)
    val idPacket: Long = 0,
    val type: SensorTYPE?,
    val value: Int,
    val timestamp: Long,
    val latitude: Double? = 0.0,
    val longitude: Double? = 0.0,
    val avgAcceleration: String?= null
)