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
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

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

    private var repartidorId: String = "7"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastUploadedLocation: Location? = null
    private val httpClient = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GasTrack::LocationWakeLock")
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { uploadLocation(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                repartidorId = intent.getStringExtra(EXTRA_REPARTIDOR_ID) ?: "7"
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
        val notification = createNotification("Seguimiento activo - Device $repartidorId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
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
        if (location.accuracy > 30f) return
        serviceScope.launch {
            try {
                val speedMs = if (location.hasSpeed() && location.speed > 0) location.speed.toDouble()
                else if (lastUploadedLocation != null) {
                    val distance = location.distanceTo(lastUploadedLocation!!)
                    val timeDiff = (location.time - lastUploadedLocation!!.time) / 1000.0
                    if (timeDiff > 0) distance / timeDiff else 0.0
                } else 0.0

                // CORREGIDO: antes mandaba velocidad en m/s, ahora en km/h como espera la web
                val speedKmh = speedMs * 3.6
                val fechaLocal = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("America/Montevideo")
                }.format(Date())

                // CORREGIDO: antes GET a /api/gps (Turso) - ahora POST a /api/postgres
                val json = JSONObject().apply {
                    put("repartidor_id", repartidorId)
                    put("device_id", repartidorId)
                    put("lat", location.latitude)
                    put("lng", location.longitude)
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("velocidad", speedKmh)
                    put("speed", speedKmh)
                    put("bateria", 0)
                    put("fecha_local", fechaLocal)
                    put("hoy", fechaLocal)
                }

                val body = json
