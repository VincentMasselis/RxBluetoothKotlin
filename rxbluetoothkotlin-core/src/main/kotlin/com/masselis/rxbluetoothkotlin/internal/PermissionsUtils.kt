package com.masselis.rxbluetoothkotlin.internal

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.core.content.ContextCompat

@SuppressLint("InlinedApi")
internal fun missingConnectPermission(): String? = when (SDK_INT) {
    in VERSION_CODES.S..Int.MAX_VALUE ->
        if (hasPermission(BLUETOOTH_CONNECT).not()) BLUETOOTH_CONNECT
        else null
    else -> null
}

@SuppressLint("InlinedApi")
internal fun missingScanPermission(): String? = when (SDK_INT) {
    in VERSION_CODES.M until VERSION_CODES.Q ->
        if (hasPermission(ACCESS_COARSE_LOCATION).not()) ACCESS_COARSE_LOCATION
        else null
    in VERSION_CODES.Q until VERSION_CODES.S ->
        if (hasPermission(ACCESS_FINE_LOCATION).not()) ACCESS_FINE_LOCATION
        else null
    in VERSION_CODES.S..Int.MAX_VALUE ->
        if (hasPermission(BLUETOOTH_SCAN).not()) BLUETOOTH_SCAN
        else null
    else -> null
}

private fun hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED