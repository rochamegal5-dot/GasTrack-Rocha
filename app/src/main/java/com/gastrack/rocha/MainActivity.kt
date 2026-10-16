package com.gastrack.rocha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkPermissions()
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GasTrackScreen()
                }
            }
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.WAKE_LOCK
        )
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GasTrackScreen() {
        var selectedDevice by remember { mutableStateOf("7") }
        var isTracking by remember { mutableStateOf(LocationTrackingService.isRunning) }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🔥 GasTrack Rocha", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Turso megal-erp - Rocha", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(text = "Seleccioná repartidor:")
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedDevice == "7",
                    onClick = { selectedDevice = "7" },
                    label = { Text("Antonio (7)") }
                )
                FilterChip(
                    selected = selectedDevice == "8",
                    onClick = { selectedDevice = "8" },
                    label = { Text("Jose (8)") }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (isTracking) {
                        stopTracking()
                        isTracking = false
                    } else {
                        startTracking(selectedDevice)
                        isTracking = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isTracking) "🛑 Detener Seguimiento" else "📍 Iniciar Seguimiento - Device $selectedDevice")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isTracking) "✅ Enviando GPS a https://megalweb.vercel.app/api/gps?device_id=$selectedDevice" 
                       else "⏸️ Detenido",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Instrucciones:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("1. Seleccioná 7 para Antonio o 8 para Jose", style = MaterialTheme.typography.bodySmall)
                    Text("2. Dale Iniciar Seguimiento", style = MaterialTheme.typography.bodySmall)
                    Text("3. Dejá la app en segundo plano", style = MaterialTheme.typography.bodySmall)
                    Text("4. Verificá en megalweb.vercel.app", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    private fun startTracking(deviceId: String) {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_REPARTIDOR_ID, deviceId)
        }
        startForegroundService(intent)
        Toast.makeText(this, "Seguimiento iniciado Device $deviceId", Toast.LENGTH_SHORT).show()
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Seguimiento detenido", Toast.LENGTH_SHORT).show()
    }
}

