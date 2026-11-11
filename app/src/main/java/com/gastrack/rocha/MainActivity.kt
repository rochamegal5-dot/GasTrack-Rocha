package com.gastrack.rocha

import 【entity-android¦canonical_name=Android】.Manifest
import 【entity-android¦canonical_name=Android】.content.Context
import 【entity-android¦canonical_name=Android】.content.Intent
import 【entity-android¦canonical_name=Android】.content.pm.PackageManager
import 【entity-android¦canonical_name=Android】.os.Build
import 【entity-android¦canonical_name=Android】.os.Bundle
import 【entity-android¦canonical_name=Android】.webkit.JavascriptInterface
import 【entity-android¦canonical_name=Android】.webkit.WebView
import 【entity-android¦canonical_name=Android】.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var repartidorSeleccionado by mutableStateOf("7")
    private var estaRastreando by mutableStateOf(false)

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultados ->
        val gpsOk = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (gpsOk) pedirPermisoFondo()
    }

    private val permisoFondoLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // 👇 PUENTE: la App Gas llama a esto con Javascript
    inner class PuenteGPS {
        @JavascriptInterface
        fun startTracking(repartidorId: String) {
            runOnUiThread { startTrackingNativo(repartidorId) }
        }

        @JavascriptInterface
        fun stopTracking() {
            runOnUiThread { stopTrackingNativo() }
        }

        @JavascriptInterface
        fun getRepartidor(): String {
            return repartidorSeleccionado
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        repartidorSeleccionado = prefs.getString("repartidor_id", "7") ?: "7"
        estaRastreando = prefs.getBoolean("was_running", false)

        pedirPermisos()

        setContent {
            MaterialTheme {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            // 👇 Conecta la web con el celular
                            addJavascriptInterface(PuenteGPS(), "【entity-AndroidGPS¦canonical_name=Android】")
                            webViewClient = WebViewClient()
                            loadUrl("https://megalweb.vercel.app/app-gas")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (estaRastreando) {
            startTrackingNativo(repartidorSeleccionado)
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

    private fun pedirPermisoFondo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permisoFondoLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    // GPS - IGUAL QUE ANTES, NO SE TOCA EL SERVICIO
    private fun startTrackingNativo(repartidorId: String) {
        repartidorSeleccionado = repartidorId
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

    private fun stopTrackingNativo() {
        val prefs = getSharedPreferences("gastrack_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_running", false).apply()

        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)
        estaRastreando = false
    }
}
