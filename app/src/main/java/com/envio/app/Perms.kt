package com.envio.app

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

object Perms {
    /** Permisos necesarios para el modo directo (sin internet). */
    fun required(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT <= 32) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Se piden todos juntos en la pantalla de bienvenida (una sola vez). */
    fun all(): List<String> = required() + Manifest.permission.RECORD_AUDIO

    fun allGranted(ctx: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }
}

object Radios {
    fun bluetoothOn(ctx: Context): Boolean =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    fun wifiOn(ctx: Context): Boolean =
        ctx.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

    /** Android 12 o menor exige la ubicacion encendida para buscar dispositivos cercanos. */
    fun needsLocation(): Boolean = Build.VERSION.SDK_INT <= 32

    fun locationOn(ctx: Context): Boolean {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    @Suppress("DEPRECATION")
    fun openWifi(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.startActivity(Intent(Settings.Panel.ACTION_WIFI))
        } else {
            ctx.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled = true
        }
    }
}
