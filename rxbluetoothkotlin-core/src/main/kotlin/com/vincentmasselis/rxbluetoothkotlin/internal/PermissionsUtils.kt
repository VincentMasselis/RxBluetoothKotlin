package com.vincentmasselis.rxbluetoothkotlin.internal

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

internal fun hasPermissions() = ContextCompat.checkSelfPermission(ContextHolder.context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED