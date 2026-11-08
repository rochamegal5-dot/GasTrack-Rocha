package com.gastrack.rocha

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    // IDs reales en Postgres (tabla repartidores) - SON TEXTO, no número
    // Antonio = "7" | Jose = "8"
    private var repartidorSeleccionado by mutableStateOf("7")
    private var estaRastreando by mutableStateOf(false)
   private val permisosLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { resultados ->
    val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
    if (gpsOk) {
        pedirPermisoFondo() // <-- AGREGAR ESTA LÍNEA
    }
}

  

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recuperar estado guardado
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        repartidorSeleccionado = prefs.getString("repartidor_id", "7") ?: "7"
        estaRastreando = prefs.getBoolean("was_running", false)

        pedirPermisos()

        setContent {
            MaterialTheme {
                GasTrackScreen(
                    repartidorSeleccionado = repartidorSeleccionado,
                    estaRastreando = estaRastreando,
                    onSeleccionarRepartidor = { id ->
                        repartidorSeleccionado = id
                        prefs.edit().putString("repartidor_id", id).apply()
                    },
                    onIniciar = { startTracking(repartidorSeleccionado) },
                    onDetener = { stopTracking() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        estaRastreando = prefs.getBoolean("was_running", false)
    }

private fun pedirPermisos() {
    val permisos = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permisos.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val faltantes = permisos.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }
    if (faltantes.isNotEmpty()) {
        permisosLauncher.launch(faltantes.toTypedArray())
    }
}
private val permisoFondoLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { _ -> }

private fun pedirPermisoFondo() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permisoFondoLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}

    private fun startTracking(repartidorId: String) {
        // Guardar para BootReceiver y para que no se pierda al reiniciar
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("repartidor_id", repartidorId)
            .putBoolean("was_running", true)
            .apply()

        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            // DESPUÉS - BIEN
            putExtra(LocationTrackingService.EXTRA_REPARTIDOR_ID, repartidorId)
           
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        estaRastreando = true
    }

    private fun stopTracking() {
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_running", false).apply()

        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        estaRastreando = false
    }
}

@Composable
fun GasTrackScreen(
    repartidorSeleccionado: String,
    estaRastreando: Boolean,
    onSeleccionarRepartidor: (String) -> Unit,
    onIniciar: () -> Unit,
    onDetener: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🔥 GasTrack Rocha",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF0EA5E9)
        )
        Text(
            text = "GPS en vivo - Megal ERP",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Estado
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (estaRastreando) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (estaRastreando) "🟢 EN VIVO" else "🟡 DETENIDO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (repartidorSeleccionado == "7") "Antonio" else "Jose",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "¿Quién está repartiendo?",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Chips repartidores - IDs TEXT de Postgres
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = repartidorSeleccionado == "7",
                onClick = { onSeleccionarRepartidor("7") },
                label = { Text("Antonio (7)") },
                modifier = Modifier.weight(1f),
                enabled = !estaRastreando
            )
            FilterChip(
                selected = repartidorSeleccionado == "8",
                onClick = { onSeleccionarRepartidor("8") },
                label = { Text("Jose (8)") },
                modifier = Modifier.weight(1f),
                enabled = !estaRastreando
            )
        }

        Text(
            text = if (estaRastreando) "Para cambiar de repartidor, primero detené el seguimiento."
            else "Elegí quién sos antes de iniciar.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Botones
        Button(
            onClick = onIniciar,
            enabled = !estaRastreando,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
        ) {
            Text("▶ INICIAR SEGUIMIENTO", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onDetener,
            enabled = estaRastreando,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
            Text("⏹ DETENER SEGUIMIENTO", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Manda cada 5 seg a:\nmegalweb.vercel.app / gps_locations",
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}
