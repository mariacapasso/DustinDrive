package com.example.bluetoothapp.Model

import java.io.Serializable


data class LiveDataModel(
    var titolo: String,
    var valore: String,
    var stato:String,
    var icona: Int
):Serializable{

}
