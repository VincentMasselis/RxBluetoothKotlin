package com.masselis.rxbluetoothkotlin.internal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun missingPermission() =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && hasPermission(Manifest.permission.BLUETOOTH_CONNECT).not() -> Manifest.permission.BLUETOOTH_CONNECT
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && hasPermission(Manifest.permission.BLUETOOTH_SCAN).not() -> Manifest.permission.BLUETOOTH_SCAN
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION).not() -> Manifest.permission.ACCESS_FINE_LOCATION
        else -> null
    }

private fun hasPermission(permission: String) = ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED