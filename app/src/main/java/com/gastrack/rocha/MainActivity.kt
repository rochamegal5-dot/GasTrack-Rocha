package com.gastrack.rocha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var repartidorId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Pedir permisos primero (GPS, Notificaciones, etc.)
        checkPermissionsAndStart()
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
            // Si ya tenemos los permisos, iniciamos la interfaz
            setupWebView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // Sean o no concedidos, iniciamos la vista. El GPS Fallará si no hay permisos, pero la app arranca.
            setupWebView()
        }
    }

    private fun setupWebView() {
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true // Vital para que Supabase y Tailwind funcionen
        webView.settings.domStorageEnabled = true // Vital para el localStorage (que recuerde el login)
        webView.webViewClient = WebViewClient() // Para que abra los links dentro de la app y no en Chrome
        
        // Cargamos nuestro archivo HTML desde la carpeta assets
        webView.loadUrl("file:///android_asset/index.html")
        
        setContentView(webView)
    }

    // Mantenemos esto por si necesitamos iniciar el servicio desde la web en el futuro
    fun startGpsService(id: String) {
        repartidorId = id
        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_REPARTIDOR_ID, repartidorId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
