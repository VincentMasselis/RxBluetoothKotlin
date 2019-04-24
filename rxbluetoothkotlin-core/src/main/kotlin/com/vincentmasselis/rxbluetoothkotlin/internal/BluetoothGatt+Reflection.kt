package com.vincentmasselis.rxbluetoothkotlin.internal

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService

/**
 * Returns system interface which communicate with the bluetooth chip or the exception while reading
 * the property
 */
internal fun BluetoothGatt.internalService() =
    try {
        this.javaClass.getDeclaredField("mService")
            .apply { this.isAccessible = true }
            .get(this)
    } catch (e: Throwable) {
        e
    }

/**
 * Returns the [Int] that represents the "mClientIf" field or the exception while reading the
 * property
 */
internal fun BluetoothGatt.clientIf() =
    try {
        this.javaClass.getDeclaredField("mClientIf")
            .apply { this.isAccessible = true }
            .get(this)
    } catch (e: Throwable) {
        e
    }

/**
 * Returns the [android.bluetooth.BluetoothDevice] that represents the "mDevice" field or the
 * exception while reading the property
 */
internal fun BluetoothGattService.device() =
    try {
        this.javaClass.getDeclaredMethod("getDevice")
            .apply { this.isAccessible = true }
            .invoke(this)
    } catch (e: Throwable) {
        e
    }


/**
 * Returns the [Boolean] that represents the "mDeviceBusy" field or the exception while reading the
 * property
 */
internal fun BluetoothGatt.isDeviceBusy() =
    try {
        this.javaClass.getDeclaredField("mDeviceBusy")
            .apply { this.isAccessible = true }
            .get(this)
    } catch (e: Throwable) {
        e
    }