package com.gastrack.rocha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UbicacionInsert(
    @SerialName("repartidor_id")
    val repartidor_id: String,

    // La web acepta lat/lng y tambien latitude/longitude, mandamos los 2 nombres
    @SerialName("lat")
    val lat: Double,
    @SerialName("lng")
    val lng: Double,
    @SerialName("latitude")
    val latitude: Double = lat,
    @SerialName("longitude")
    val longitude: Double = lng,

    @SerialName("velocidad")
    val velocidad: Double, // en km/h
    @SerialName("speed")
    val speed: Double = velocidad,

    @SerialName("bateria")
    val bateria: Int,
    @SerialName("battery")
    val battery: Int = bateria,

    @SerialName("precision_gps")
    val precision_gps: Double = 0.0,

    @SerialName("en_movimiento")
    val en_movimiento: Boolean = velocidad > 3,

    @SerialName("fecha_local")
    val fecha_local: String, // yyyy-MM-dd en America/Montevideo
    @SerialName("hoy")
    val hoy: String = fecha_local,

    @SerialName("timestamp")
    val timestamp: String = fecha_local
)
