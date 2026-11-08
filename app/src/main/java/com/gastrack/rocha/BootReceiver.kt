package com.gastrack.rocha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("gastrack", Context.MODE_PRIVATE)
        val repartidorId = prefs.getString("repartidor_id", null)
        val wasRunning = prefs.getBoolean("was_running", false)

        // Si no estaba corriendo antes de apagar, no arrancar solo
        if (!wasRunning || repartidorId == null) return

        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_REPARTIDOR_ID, repartidorId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
