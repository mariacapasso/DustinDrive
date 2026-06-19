package com.example.bluetoothapp.db


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface SensorDataDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addData(data: SensorDataEntity)

    @Query("SELECT * FROM packet ORDER BY idPacket ASC")
    suspend  fun getAllData(): List<SensorDataEntity>

    @Query("SELECT * FROM packet WHERE idPacket > :lastId ORDER BY idPacket ASC")
    suspend fun getDataAfter(lastId: Long): List<SensorDataEntity>

    @Query("DELETE FROM packet")
    suspend fun clearDatabase()
}