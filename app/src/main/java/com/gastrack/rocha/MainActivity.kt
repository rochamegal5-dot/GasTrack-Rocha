package com.gastrack.rocha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnStartStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var etRepartidorId: EditText
    private var repartidorId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        tvStatus = TextView(this).apply {
            text = "GasTrack Rocha - Seguimiento GPS"
            textSize = 20f
            setPadding(0, 0, 0, 48)
        }
        layout.addView(tvStatus)

        etRepartidorId = EditText(this).apply {
            hint = "ID del repartidor (UUID de Supabase)"
            textSize = 14f
        }
        layout.addView(etRepartidorId)

        btnStartStop = Button(this).apply {
            text = "Iniciar Seguimiento"
            setOnClickListener { toggleTracking() }
        }
        layout.addView(btnStartStop)

        setContentView(layout)
    }

    private fun toggleTracking() {
        if (LocationTrackingService.isRunning) {
            stopTracking()
        } else {
            repartidorId = etRepartidorId.text.toString().trim()
            if (repartidorId.isEmpty()) {
                Toast.makeText(this, "Ingresá el ID del repartidor", Toast.LENGTH_SHORT).show()
                return
            }
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        } else {
            startTracking()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startTracking()
        } else {
            Toast.makeText(this, "Permisos necesarios para funcionar", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_REPARTIDOR_ID, repartidorId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        btnStartStop.text = "Detener Seguimiento"
        tvStatus.text = "Seguimiento activo"
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        btnStartStop.text = "Iniciar Seguimiento"
        tvStatus.text = "Seguimiento detenido"
    }
}
