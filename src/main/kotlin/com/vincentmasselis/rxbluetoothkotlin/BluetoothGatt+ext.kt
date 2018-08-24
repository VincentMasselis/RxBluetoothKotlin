package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.SimpleDeviceDisconnected
import io.reactivex.Completable
import io.reactivex.Maybe
import java.util.*

/**
 * Emit [Unit] when the connection is ready or immediately if the connection is available at the
 * subscription.
 *
 * @return
 * onComplete if the disconnection was closed by the user
 *
 * onError with [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected]
 *
 * @see rxLivingConnection
 */
fun BluetoothGatt.rxWhenConnectionIsReady(): Maybe<Unit> =
    rxLivingConnection()
        .firstElement()

/**
 * Start a disconnection and completes when it's done.
 *
 * @return
 * onComplete If the disconnection was successful
 *
 * onError with [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected] if the device was disconnect
 * with an error.
 *
 * @see rxLivingConnection
 */
fun BluetoothGatt.rxDisconnect(): Completable =
    rxLivingConnection()
        .doOnSubscribe { Handler(Looper.getMainLooper()).post { disconnect() } }
        .ignoreElements()

/**
 * Listen [BluetoothGatt] disconnections.
 *
 * @return
 * onComplete If the disconnection was excepted
 *
 * onError with [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected] if the device was disconnected
 * with an error.
 *
 * @see rxLivingConnection
 */
fun BluetoothGatt.rxListenDisconnection(): Completable =
    rxLivingConnection()
        .ignoreElements()

/**
 * Returns a [BluetoothGattCharacteristic] if [this] contains a [BluetoothGattCharacteristic]
 * matching with the filled [uuid]
 */
@Throws(LookingForCharacteristicButServicesNotDiscovered::class)
fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
    if (services.isEmpty())
        throw LookingForCharacteristicButServicesNotDiscovered(device, uuid)
    else {
        services.forEach { service -> service.characteristics.forEach { if (it.uuid == uuid) return it } }
        return null
    }
}

/**
 * @return true if [this] changes can be used with indication instead of notification.
 *
 * @see rxEnableNotification
 * @see rxListenChanges
 * @see BluetoothGattCharacteristic.PROPERTY_INDICATE
 */
fun BluetoothGattCharacteristic.hasIndication() = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0