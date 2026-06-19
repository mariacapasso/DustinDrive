package com.example.bluetoothapp.Model



sealed class ParsedPacket(open val formattedTimestamp : String,
                          open val rawTimeStamp : Long,
                          val type: SensorTYPE) {

    data class Battery(override val formattedTimestamp: String,
                       override val rawTimeStamp: Long,
                       val percentage: Int,
                       val voltageFormatted:String
                         ): ParsedPacket(formattedTimestamp,rawTimeStamp, SensorTYPE.Batteria)



    data class Touch(override val formattedTimestamp: String,
                     override val rawTimeStamp: Long,
                     val isActive:Boolean,
                     val elettrodiCount: Int
        ): ParsedPacket(formattedTimestamp,rawTimeStamp, SensorTYPE.Touch)

    data class TAC(override val formattedTimestamp: String,
                   override val rawTimeStamp: Long,
                    val ppbValue: Int,
                    val isSafe: Boolean
    ): ParsedPacket(formattedTimestamp, rawTimeStamp, SensorTYPE.TAC)

    data class PPG(override val formattedTimestamp: String,
                   override val rawTimeStamp: Long,
                   val bpmValue: Int
    ): ParsedPacket(formattedTimestamp, rawTimeStamp,SensorTYPE.PPG)








}