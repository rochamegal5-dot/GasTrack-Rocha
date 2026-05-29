package com.gastrack.rocha

import kotlinx.serialization.Serializable

@Serializable
data class UbicacionInsert(
    val repartidor_id: String,
    val latitud: Double,
    val longitud: Double,
    val velocidad: Double,
    val bateria: Int,
    val precision_gps: Double,
    val en_movimiento: Boolean,
    val timestamp: String
)
