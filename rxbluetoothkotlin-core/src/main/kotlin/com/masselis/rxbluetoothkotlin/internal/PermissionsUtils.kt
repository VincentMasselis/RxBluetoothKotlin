package com.masselis.rxbluetoothkotlin.internal

import android.Manifest.permission.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun missingConnectPermission(): String? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPermission(BLUETOOTH_CONNECT).not() ->
            BLUETOOTH_CONNECT
        else -> null
    }

internal fun missingScanPermission(): String? =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPermission(BLUETOOTH_SCAN).not() ->
            BLUETOOTH_SCAN
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && hasPermission(ACCESS_FINE_LOCATION).not() ->
            ACCESS_FINE_LOCATION
        hasPermission(ACCESS_COARSE_LOCATION).not() ->
            ACCESS_COARSE_LOCATION
        else -> null
    }

private fun hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED