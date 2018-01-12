package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.os.Handler
import android.os.Looper
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.SimpleDeviceDisconnected
import io.reactivex.Completable
import io.reactivex.Maybe

/**
 * Emit [Unit] when the connection is ready or immediately if the connection is available at the
 * subscription.
 *
 * It completes if the disconnection was closed by the user
 *
 * It emit can emit [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected]
 */
fun BluetoothGatt.rxWhenConnectionIsReady(): Maybe<Unit> =
    rxLivingConnection()
        .firstElement()

/**
 * Start a disconnection and completes when it's done.
 *
 * If the disconnection was successful, this [Completable] completes
 *
 * It can emit [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected] if the device was disconnect
 * with an error.
 */
fun BluetoothGatt.rxDisconnect(): Completable =
    rxLivingConnection()
        .doOnSubscribe { Handler(Looper.getMainLooper()).post { disconnect() } }
        .ignoreElements()

/**
 * Listen [BluetoothGatt] disconnections.
 *
 * If the disconnection was excepted, this [Completable] completes
 *
 * it can emit [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected] if the device was disconnected
 * with an error.
 */
fun BluetoothGatt.rxListenDisconnection(): Completable =
    rxLivingConnection()
        .ignoreElements()