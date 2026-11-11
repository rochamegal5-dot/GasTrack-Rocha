package com.gastrack.rocha

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var repartidorId: String = "7"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val notif = perms[Manifest.permission.POST_NOTIFICATIONS] == true
        if (!fine) {
            Toast.makeText(this, "Sin permiso de GPS la app no puede seguir el reparto", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // Permisos
        val toAsk = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            toAsk.add(Manifest.permission.ACCESS_FINE_LOCATION)
            toAsk.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                toAsk.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (toAsk.isNotEmpty()) permissionLauncher.launch(toAsk.toTypedArray())

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        // WebChromeClient es OBLIGATORIO para que anden los prompt() de los cierres parciales
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(AndroidGPS(), "AndroidGPS")

        // CORREGIDO: en tu repo el archivo se llama index.html, no app-gas.html
        // Antes apuntaba a https://megalweb.vercel.app/app-gas -> 404
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidGPS {
        @JavascriptInterface
        fun startGPS(repartidor: String) {
            repartidorId = repartidor.ifEmpty { "7" }
            val prefs = getSharedPreferences("gastrack_prefs", MODE_PRIVATE)
            prefs.edit().putString("repartidor_id", repartidorId).putBoolean("was_running", true).apply()
            val intent = Intent(this@MainActivity, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_START
                putExtra(LocationTrackingService.EXTRA_REPARTIDOR_ID, repartidorId)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, "GPS activado - Repartidor $repartidorId", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun stopGPS() {
            val intent = Intent(this@MainActivity, LocationTrackingService::class.java).apply {
                action = LocationTrackingService.ACTION_STOP
            }
            startService(intent)
        }

        @JavascriptInterface
        fun getRepartidor(): String {
            return getSharedPreferences("gastrack_prefs", MODE_PRIVATE).getString("repartidor_id", "7") ?: "7"
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
