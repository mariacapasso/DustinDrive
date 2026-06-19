package com.example.bluetoothapp.db


class SensorDataRepository(private val sensorDataDao: SensorDataDAO) {
    suspend fun getAllData(): List<SensorDataEntity> = sensorDataDao.getAllData()
    suspend fun getDataAfter(lastId:Long): List<SensorDataEntity> = sensorDataDao.getDataAfter(lastId)
    suspend fun addData(data: SensorDataEntity){
        sensorDataDao.addData(data)
    }

    suspend fun clearDatabase() {
        sensorDataDao.clearDatabase()
    }
}