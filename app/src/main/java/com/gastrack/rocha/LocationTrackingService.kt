package com.gastrack.rocha

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_REPARTIDOR_ID = "extra_repartidor_id"
        const val CHANNEL_ID = "gastrack_gps_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "GasTrackService"
        private const val API_URL = "https://megalweb.vercel.app/api/postgres?table=gps_locations"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // repartidor_id como String - IGUAL que en Postgres: "7" Antonio, "8" Jose
    private var repartidorId: String = "7"
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        crearCanalNotificacion()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    onNuevaUbicacion(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Viene de MainActivity como String
                // DESPUÉS - BIEN
val id = intent?.getStringExtra(EXTRA_REPARTIDOR_ID)
if (!id.isNullOrEmpty()) {
    repartidorId = id
                
               } else {
                    // Si no vino, lo recupero de lo guardado (para BootReceiver)
                    val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
                    repartidorId = prefs.getString("repartidor_id", "7") ?: "7"
                }
                // Guardo siempre para que BootReceiver pueda retomar
                getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("repartidor_id", repartidorId)
                    .putBoolean("was_running", true)
                    .apply()

                startTracking()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isTracking) return
        isTracking = true

        // WakeLock para que no se duerma
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GasTrack::LocationWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 min, se renueva con cada location
        }

        startForeground(NOTIFICATION_ID, crearNotificacion("GasTrack en vivo - Repartidor $repartidorId"))

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // cada 5 segundos
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setMaxUpdateDelayMillis(10000L)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )
            Log.d(TAG, "Tracking iniciado repartidor=$repartidorId")
        } catch (e: SecurityException) {
            Log.e(TAG, "Sin permiso de ubicación", e)
            stopTracking()
        }
    }

    private fun onNuevaUbicacion(location: Location) {
        // Filtro: si la precisión es mala, no mandar basura a la web
        if (location.accuracy > 30) {
            Log.d(TAG, "Descartada por precisión: ${location.accuracy}")
            return
        }

        // Velocidad en km/h
        val velocidadKmh = if (location.hasSpeed()) {
            (location.speed * 3.6).toDouble()
        } else {
            0.0
        }

        val bateria = obtenerBateria()
        val fechaLocal = fechaHoyUY()

        val ubicacion = UbicacionInsert(
            repartidor_id = repartidorId, // String "7" o "8"
            lat = location.latitude,
            lng = location.longitude,
            latitude = location.latitude,
            longitude = location.longitude,
            velocidad = velocidadKmh,
            speed = velocidadKmh,
            bateria = bateria,
            battery = bateria,
            precision_gps = location.accuracy,
            fecha_local = fechaLocal,
            hoy = fechaLocal,
            timestamp = fechaLocal
        )

        // Actualizar notificación con velocidad
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            NOTIFICATION_ID,
            crearNotificacion("🚀 ${velocidadKmh.toInt()} km/h - 🔋 $bateria% - Repartidor $repartidorId")
        )

        // Renovar WakeLock
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error", e)
        }

        enviarAlServidor(ubicacion)
    }

    private fun enviarAlServidor(ubicacion: UbicacionInsert) {
        serviceScope.launch {
            try {
                val bodyJson = json.encodeToString(ubicacion)
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Error POST gps: ${response.code} ${response.body?.string()}")
                    } else {
                        Log.d(TAG, "GPS enviado: ${ubicacion.lat}, ${ubicacion.lng} vel=${ubicacion.velocidad}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando GPS", e)
            }
        }
    }

    private fun obtenerBateria(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun fechaHoyUY(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("America/Montevideo")
        return sdf.format(Date())
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS GasTrack",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Seguimiento GPS en vivo"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun crearNotificacion(texto: String): Notification {
        // Botón Detener en la notificación
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Al tocar la notificación abre la app
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GasTrack Rocha - En vivo")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending)
            .build()
    }

    private fun stopTracking() {
        isTracking = false
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error quitando updates", e)
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando WakeLock", e)
        }

        getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("was_running", false)
            .apply()

        stopForeground(true)
        stopSelf()
        Log.d(TAG, "Tracking detenido")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Si el usuario barre la app, intentar revivir si estaba corriendo
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("was_running", false)
        if (wasRunning && isTracking) {
            val restartIntent = Intent(this, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REPARTIDOR_ID, repartidorId)
                }
            val pending = PendingIntent.getService(
                this, 1, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                pending
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
