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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // IDs reales en Postgres (tabla repartidores) - SON TEXTO, no número
    // Antonio = "7" | Jose = "8"
    private var repartidorSeleccionado by mutableStateOf("7")
    private var estaRastreando by mutableStateOf(false)

    // --- PARTE 1: JORNADA ---
    private var stock11 by mutableStateOf("0")
    private var stock13 by mutableStateOf("0")
    private var stock45 by mutableStateOf("0")
    private var cambioInicial by mutableStateOf("0")
    private var jornadaId by mutableStateOf("")
    private var horaSalida by mutableStateOf("")

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (gpsOk) {
            pedirPermisoFondo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        repartidorSeleccionado = prefs.getString("repartidor_id", "7") ?: "7"
        estaRastreando = prefs.getBoolean("was_running", false)
        // Recuperar jornada si quedó abierta
        jornadaId = prefs.getString("jornada_id", "") ?: ""
        horaSalida = prefs.getString("hora_salida", "") ?: ""
        stock11 = prefs.getString("stock_11", "0") ?: "0"
        stock13 = prefs.getString("stock_13", "0") ?: "0"
        stock45 = prefs.getString("stock_45", "0") ?: "0"
        cambioInicial = prefs.getString("cambio_inicial", "0") ?: "0"

        pedirPermisos()

        setContent {
            MaterialTheme {
                GasTrackScreen(
                    repartidorSeleccionado = repartidorSeleccionado,
                    estaRastreando = estaRastreando,
                    stock11 = stock11,
                    stock13 = stock13,
                    stock45 = stock45,
                    cambioInicial = cambioInicial,
                    horaSalida = horaSalida,
                    onSeleccionarRepartidor = { id ->
                        repartidorSeleccionado = id
                        prefs.edit().putString("repartidor_id", id).apply()
                    },
                    onStock11 = { stock11 = it },
                    onStock13 = { stock13 = it },
                    onStock45 = { stock45 = it },
                    onCambio = { cambioInicial = it },
                    onIniciar = { iniciarJornada() },
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

    // --- PARTE 1: LOGIN = INICIO JORNADA ---
    private fun iniciarJornada() {
        val s11 = stock11.toIntOrNull() ?: 0
        val s13 = stock13.toIntOrNull() ?: 0
        val s45 = stock45.toIntOrNull() ?: 0
        val cambio = cambioInicial.toDoubleOrNull() ?: 0.0
        if (s11 == 0 && s13 == 0 && s45 == 0) {
            // Si no cargó nada, no lo dejamos salir sin stock
            // Podés sacar este if si querés permitir salir en 0
            return
        }
        val ahora = Date()
        val formatoHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("es", "UY"))
        val formatoDia = SimpleDateFormat("yyyy-MM-dd", Locale("es", "UY"))
        val idJornada = "JOR_" + System.currentTimeMillis()
        val hora = formatoHora.format(ahora)
        val dia = formatoDia.format(ahora)

        jornadaId = idJornada
        horaSalida = hora

        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("jornada_id", idJornada)
            .putString("hora_salida", hora)
            .putString("stock_11", stock11)
            .putString("stock_13", stock13)
            .putString("stock_45", stock45)
            .putString("cambio_inicial", cambioInicial)
            .apply()

        // Guardar jornada en la web - tabla jornadas
        // Esto NO toca GPS, es solo un POST aparte
        Thread {
            try {
                val url = URL("https://megalweb.vercel.app/api/postgres?table=jornadas")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val json = JSONObject().apply {
                    put("id", idJornada)
                    put("repartidor_id", repartidorSeleccionado)
                    put("fecha", dia)
                    put("hora_salida", hora)
                    put("stock_11_inicial", s11)
                    put("stock_13_inicial", s13)
                    put("stock_45_inicial", s45)
                    put("cambio_inicial", cambio)
                    put("estado", "abierta")
                }
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        // GPS - IGUAL QUE ANTES, NO SE TOCA
        startTracking(repartidorSeleccionado)
    }

    private fun startTracking(repartidorId: String) {
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("repartidor_id", repartidorId)
            .putBoolean("was_running", true)
            .apply()

        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
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
    stock11: String,
    stock13: String,
    stock45: String,
    cambioInicial: String,
    horaSalida: String,
    onSeleccionarRepartidor: (String) -> Unit,
    onStock11: (String) -> Unit,
    onStock13: (String) -> Unit,
    onStock45: (String) -> Unit,
    onCambio: (String) -> Unit,
    onIniciar: () -> Unit,
    onDetener: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

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

        if (estaRastreando && horaSalida.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📋 Jornada activa", fontWeight = FontWeight.Bold)
                    Text("Salida: $horaSalida", fontSize = 13.sp)
                    Text("Camioneta: $stock11 x 11kg | $stock13 x 13kg | $stock45 x 45kg", fontSize = 13.sp)
                    Text("Cambio inicial: $$cambioInicial", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "¿Quién está repartiendo?",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        // --- SOLO SE MUESTRA SI NO ESTÁ RASTREANDO ---
        if (!estaRastreando) {
            Text(
                text = "📦 Stock inicial camioneta - todas llenas",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Esto se descuenta del depósito al salir.", fontSize = 12.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = stock11, onValueChange = onStock11,
                    label = { Text("11kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = stock13, onValueChange = onStock13,
                    label = { Text("13kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = stock45, onValueChange = onStock45,
                    label = { Text("45kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "💵 Cambio inicial de caja",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cambioInicial, onValueChange = onCambio,
                label = { Text("Ej: 2000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Login = inicio del día. Al apretar INICIAR se guarda hora de salida y se activa el seguimiento.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onIniciar,
            enabled = !estaRastreando,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
        ) {
            Text("▶ INICIAR JORNADA", fontWeight = FontWeight.Black, fontSize = 16.sp)
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
