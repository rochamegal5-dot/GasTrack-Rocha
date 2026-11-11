package com.gastrack.rocha

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class UbicacionInsert(
    // ID en Postgres es TEXT: "7" Antonio, "8" Jose
    val repartidor_id: String,
    val lat: Double,
    val lng: Double,
    // Duplicados para que la web los lea como lat/lng o latitude/longitude
    val latitude: Double = lat,
    val longitude: Double = lng,
    val velocidad: Double,
    val speed: Double = velocidad,
    val bateria: Int,
    val battery: Int = bateria,
    val precision_gps: Float = 0f,
    val en_movimiento: Boolean = velocidad > 3,
    val fecha_local: String = hoyUY(),
    val hoy: String = fecha_local,
    // CORREGIDO: timestamp con hora, no solo fecha
    val timestamp: String = ahoraUY()
) {
    companion object {
        fun hoyUY(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale("es", "UY"))
            sdf.timeZone = TimeZone.getTimeZone("America/Montevideo")
            return sdf.format(Date())
        }

        // NUEVO: fecha + hora para el timestamp
        fun ahoraUY(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("es", "UY"))
            sdf.timeZone = TimeZone.getTimeZone("America/Montevideo")
            return sdf.format(Date())
        }

        fun crear(
            repartidorId: String,
            lat: Double,
            lng: Double,
            velocidadKmh: Double,
            bateria: Int,
            precision: Float
        ): UbicacionInsert {
            val fecha = hoyUY()
            return UbicacionInsert(
                repartidor_id = repartidorId,
                lat = lat,
                lng = lng,
                latitude = lat,
                longitude = lng,
                velocidad = velocidadKmh,
                speed = velocidadKmh,
                bateria = bateria,
                battery = bateria,
                precision_gps = precision,
                en_movimiento = velocidadKmh > 3,
                fecha_local = fecha,
                hoy = fecha,
                timestamp = ahoraUY()
            )
        }
    }
}
