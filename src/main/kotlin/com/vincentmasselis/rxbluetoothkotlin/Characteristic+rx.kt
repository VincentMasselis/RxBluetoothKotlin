package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.*
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.*
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.*
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

fun BluetoothGatt.rxRead(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray> =
        EnqueueSingle(semaphore, assertConnected { device, reason -> CharacteristicReadDeviceDisconnected(device, reason, characteristic.service, characteristic) }) {
            Single
                    .create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
                        downStream.setDisposable(characteristicReadSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                        logger?.v(TAG, "readCharacteristic ${characteristic.uuid}")
                        if (readCharacteristic(characteristic).not())
                            downStream.tryOnError(CannotInitializeCharacteristicReading(
                                    device,
                                    characteristic.service,
                                    characteristic,
                                    characteristic.properties,
                                    internalService(),
                                    clientIf(),
                                    characteristic.service?.device(),
                                    isDeviceBusy()))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMap { (readCharacteristic, status) ->
                    if (status != BluetoothGatt.GATT_SUCCESS) Maybe.error(CharacteristicReadingFailed(status, device, readCharacteristic.service, readCharacteristic))
                    else Maybe.just(readCharacteristic.value)
                }

/*fun BluetoothGatt.reliableWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray): Observable<Unit> =
        assertConnected { device, reason -> CharacteristicWriteDeviceDisconnected(device, reason, characteristic.service, characteristic, value) }
                .flatMap {
                    beginReliableWrite()
                    characteristic.value = value
                    if (writeCharacteristic(characteristic).not()) Observable.error(CannotInitializeCharacteristicWrite(device, characteristic.service, characteristic, value))
                    else characteristicWriteSubject.asObservable()
                }
                .filter { it.first.uuid == characteristic.uuid }
                .first()
                .flatMap {
                    if (it.second != BluetoothGatt.GATT_SUCCESS) {
                        abortReliableWrite()
                        return@flatMap Observable.error<Pair<BluetoothGattCharacteristic, Int>>(CharacteristicBeginWriteFailed(it.second, device, it.first.service, it.first, value))
                    } else if (it.first.value != value) {
                        abortReliableWrite()
                        return@flatMap Observable.error<Pair<BluetoothGattCharacteristic, Int>>(AbortedCharacteristicWrite(it.second, device, it.first.service, it.first, value))
                    } else
                        executeReliableWrite()
                    reliableWriteCompletedSubject.map { status -> it.first to status }
                }
                .filter { it.first.uuid == characteristic.uuid }
                .first()
                .flatMap {
                    if (it.second != BluetoothGatt.GATT_SUCCESS) Observable.error(CharacteristicReliableWriteFailed(it.second, device, it.first.service, it.first, value))
                    else Observable.just<Unit>(null)
                }

class CharacteristicWriteDeviceDisconnected(bluetoothDevice: BluetoothDevice, reason: Int, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : DeviceDisconnected(bluetoothDevice, reason)
class CannotInitializeCharacteristicWrite(device: BluetoothDevice, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : CannotInitialize(device)
class CharacteristicBeginWriteFailed(val reason: Int, val device: BluetoothDevice, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Throwable()
class AbortedCharacteristicWrite(val reason: Int, val device: BluetoothDevice, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Throwable()
class CharacteristicReliableWriteFailed(val reason: Int, val device: BluetoothDevice, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Throwable()*/

fun BluetoothGatt.rxWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray): Completable =
        EnqueueSingle(semaphore, assertConnected { device, reason -> CharacteristicWriteDeviceDisconnected(device, reason, characteristic.service, characteristic, value) }) {
            Single
                    .create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
                        downStream.setDisposable(characteristicWriteSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                        logger?.v(TAG, "writeCharacteristic ${characteristic.uuid} with value ${value.toHexString()}")
                        characteristic.value = value
                        if (writeCharacteristic(characteristic).not())
                            downStream.tryOnError(CannotInitializeCharacteristicWrite(
                                    device,
                                    characteristic.service,
                                    characteristic,
                                    value,
                                    characteristic.properties,
                                    internalService(),
                                    clientIf(),
                                    characteristic.service?.device(),
                                    isDeviceBusy()))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMapCompletable { (wroteCharacteristic, status) ->
                    if (status != BluetoothGatt.GATT_SUCCESS) Completable.error(CharacteristicWriteFailed(status, device, wroteCharacteristic.service, wroteCharacteristic, value))
                    else Completable.complete()
                }

/**
 * Because enabling notification require an descriptor write, the [Completable] returned can fire
 * errors from [BluetoothGattDescriptor.write] method like [DescriptorWriteDeviceDisconnected],
 * [CannotInitializeDescriptorWrite] or [DescriptorWriteFailed].
 */
fun BluetoothGatt.rxEnableNotification(characteristic: BluetoothGattCharacteristic, indication: Boolean = false, checkIfAlreadyEnabled: Boolean = true): Completable =
        rxChangeNotification(
                characteristic,
                if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                checkIfAlreadyEnabled
        )

/**
 * Because disabling notification require an descriptor write, the [Completable] returned can fire
 * errors from [BluetoothGattDescriptor.write] method like [DescriptorWriteDeviceDisconnected],
 * [CannotInitializeDescriptorWrite] or [DescriptorWriteFailed].
 */
fun BluetoothGatt.rxDisableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean = true): Completable =
        rxChangeNotification(
                characteristic,
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                checkIfAlreadyDisabled
        )

private fun BluetoothGatt.rxChangeNotification(characteristic: BluetoothGattCharacteristic, byteArray: ByteArray, checkIfAlreadyChanged: Boolean): Completable =
        Completable
                .defer {
                    val isEnable = Arrays.equals(byteArray, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE).not()
                    logger?.v(TAG, "setCharacteristicNotification ${characteristic.uuid}} to $isEnable")
                    if (setCharacteristicNotification(characteristic, isEnable).not())
                        Completable.error {
                            CannotInitializeCharacteristicNotification(
                                    device,
                                    characteristic.service,
                                    characteristic,
                                    internalService(),
                                    clientIf(),
                                    characteristic.service?.device())
                        }
                    else {
                        val notificationDescriptor = characteristic.getDescriptor(GattConst.CLIENT_CHARACTERISTIC_CONFIG)
                        if (notificationDescriptor == null)
                            Completable.error(DescriptorNotFound(device, characteristic.uuid, GattConst.CLIENT_CHARACTERISTIC_CONFIG))
                        else
                            rxWrite(notificationDescriptor, byteArray, checkIfAlreadyChanged)
                    }
                }

fun BluetoothGatt.rxListenChanges(characteristic: BluetoothGattCharacteristic): Flowable<ByteArray> =
        Flowable.defer { characteristicChangedSubject.toFlowable(BackpressureStrategy.BUFFER) }
                .filter { changedCharacteristic -> changedCharacteristic.uuid == characteristic.uuid }
                .map { it.value }
                .takeUntil(assertConnected { device, reason -> CharacteristicChangedDeviceDisconnected(device, reason, characteristic.service, characteristic) }.andThen(Flowable.just(Unit)))

fun BluetoothGatt.rxCharacteristicMaybe(uuid: UUID): Maybe<BluetoothGattCharacteristic> =
        Maybe.defer {
            if (services.isEmpty())
                Maybe.error<BluetoothGattCharacteristic>(SearchingCharacteristicButServicesNotDiscovered(device, uuid))
            else {
                services.forEach { it.characteristics.forEach { if (it.uuid == uuid) return@defer Maybe.just(it) } }
                Maybe.empty()
            }
        }

fun BluetoothGatt.rxCharacteristic(uuid: UUID): Single<BluetoothGattCharacteristic> =
        Single.defer {
            if (services.isEmpty())
                Single.error<BluetoothGattCharacteristic>(SearchingCharacteristicButServicesNotDiscovered(device, uuid))
            else {
                services.forEach { it.characteristics.forEach { if (it.uuid == uuid) return@defer Single.just(it) } }
                Single.error<BluetoothGattCharacteristic>(CharacteristicNotFound(device, uuid))
            }
        }

fun BluetoothGattCharacteristic.hasIndication() = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0