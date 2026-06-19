package com.example.bluetoothapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities = [SensorDataEntity::class], version = 1, exportSchema = false)
abstract class SensorDataDb: RoomDatabase() {
    abstract fun sensorDataDao(): SensorDataDAO

    companion object{
        @Volatile
        private var INSTANCE: SensorDataDb? = null

        fun getDatabase(context: Context): SensorDataDb{
            val tempInstance = INSTANCE
            if (tempInstance != null){
                return tempInstance
            }
            synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SensorDataDb::class.java,
                    "Packet"


                )
                    .setJournalMode(JournalMode.TRUNCATE)
                    .build()
                INSTANCE=instance
                return instance
            }

        }
    }
}