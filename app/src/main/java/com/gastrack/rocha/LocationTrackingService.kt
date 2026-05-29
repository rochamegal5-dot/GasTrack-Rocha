package com.gastrack.rocha

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import java.time.Instant

class LocationTrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "gastrack_tracking"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.gastrack.rocha.START_TRACKING"
        const val ACTION_STOP = "com.gastrack.rocha.STOP_TRACKING"
        const val EXTRA_REPARTIDOR_ID = "repartidor_id"

        var isRunning = false
            private set
    }

    private var repartidorId: String = ""
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUploadedLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GasTrack::LocationWakeLock")

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    uploadLocation(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                repartidorId = intent.getStringExtra(EXTRA_REPARTIDOR_ID) ?: ""
                startTracking()
            }
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTracking() {
        isRunning = true
        if (!wakeLock.isHeld) wakeLock.acquire(12 * 60 * 60 * 1000L)

        val notification = createNotification("Seguimiento activo")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val locationRequest = LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (e: SecurityException) { stopTracking() }
    }

    private fun stopTracking() {
        isRunning = false
        try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        if (wakeLock.isHeld) wakeLock.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun uploadLocation(location: Location) {
        serviceScope.launch {
            try {
                val speed = if (location.hasSpeed() && location.speed > 0) location.speed.toDouble()
                else if (lastUploadedLocation != null) {
                    val distance = location.distanceTo(lastUploadedLocation!!)
                    val timeDiff = (location.time - lastUploadedLocation!!.time) / 1000.0
                    if (timeDiff > 0) distance / timeDiff else 0.0
                } else 0.0

                val bateria = try {
                    val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                    bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                } catch (_: Exception) { 100 }

                val data = UbicacionInsert(
                    repartidor_id = repartidorId,
                    latitud = location.latitude,
                    longitud = location.longitude,
                    velocidad = speed,
                    bateria = bateria,
                    precision_gps = location.accuracy.toDouble(),
                    en_movimiento = speed > 1.0,
                    timestamp = Instant.now().toString()
                )

                SupabaseClient.client.postgrest["ubicaciones"].insert(data)
                lastUploadedLocation = location

                val speedKmh = (speed * 3.6).toInt()
                updateNotification("En vivo - ${speedKmh} km/h")

            } catch (e: Exception) {
                println("Error subiendo ubicación: ${e.message}")
            }
        }
    }

    private fun createNotification(text: String): Notification {
        createNotificationChannel()
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GasTrack Rocha")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val notification = createNotification(text)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Seguimiento GPS", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_REPARTIDOR_ID, repartidorId)
        }
        val pending = PendingIntent.getService(this, 1, restartIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.set(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 1000, pending)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopTracking()
        serviceScope.cancel()
        super.onDestroy()
    }
}
