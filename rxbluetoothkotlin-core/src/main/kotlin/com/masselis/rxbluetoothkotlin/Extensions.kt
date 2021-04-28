package com.masselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import io.reactivex.rxjava3.annotations.CheckReturnValue
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
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
 * @see RxBluetoothGatt.livingConnection
 */
@CheckReturnValue
fun RxBluetoothGatt.whenConnectionIsReady(): Maybe<Unit> = livingConnection().firstElement()

/**
 * Start a disconnection and completes when it's done.
 *
 * @return
 * onComplete If the disconnection was successful
 *
 * onError with [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected] if the device was disconnect
 * with an error.
 *
 * @see RxBluetoothGatt.livingConnection
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated(
    "disconnect method extension is not allowed anymore, consider using the method from the RxBluetoothGatt interface directly",
    ReplaceWith("RxBluetoothGatt.disconnect", "com.vincentmasselis.rxbluetoothkotlin.RxBluetoothGatt")
)
fun RxBluetoothGatt.disconnect(): Completable = disconnect()

/**
 * Listen [BluetoothGatt] disconnections.
 *
 * @return
 * onComplete If the disconnection was excepted
 *
 * onError with [BluetoothIsTurnedOff] or [SimpleDeviceDisconnected] if the device was disconnected
 * with an error.
 *
 * @see RxBluetoothGatt.livingConnection
 */
@CheckReturnValue
fun RxBluetoothGatt.listenDisconnection(): Completable = livingConnection().ignoreElements()

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
 * @return true if [this] notifications changes can be used with by using indication instead of notification.
 *
 * @see RxBluetoothGatt.enableNotification
 * @see RxBluetoothGatt.listenChanges
 * @see BluetoothGattCharacteristic.PROPERTY_INDICATE
 */
fun BluetoothGattCharacteristic.hasIndication() = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0