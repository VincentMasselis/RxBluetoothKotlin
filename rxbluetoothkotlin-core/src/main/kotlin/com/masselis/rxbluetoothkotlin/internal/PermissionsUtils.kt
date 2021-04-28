package com.masselis.rxbluetoothkotlin.internal

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal fun hasPermissions() = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED