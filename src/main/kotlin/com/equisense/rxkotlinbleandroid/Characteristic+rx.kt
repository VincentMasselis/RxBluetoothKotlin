package com.equisense.rxkotlinbleandroid

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.support.v4.util.ArrayMap
import com.equisense.kotlin_field_extension.FieldProperty
import com.equisense.rxkotlinbleandroid.internal.*
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

fun BluetoothGatt.rxRead(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray> =
        EnqueueSingle(semaphore, assertConnected { device, reason -> CharacteristicReadDeviceDisconnected(device, reason, characteristic.service, characteristic) }) {
            Single
                    .create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
                        downStream.setDisposable(characteristicReadSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) }))
                        logger?.v(TAG, "readCharacteristic ${characteristic.uuid}")
                        if (readCharacteristic(characteristic).not())
                            downStream.onError(CannotInitializeCharacteristicReading(
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
                        downStream.setDisposable(characteristicWriteSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) }))
                        logger?.v(TAG, "writeCharacteristic ${characteristic.uuid} with value ${value.toHexString()}")
                        characteristic.value = value
                        if (writeCharacteristic(characteristic).not())
                            downStream.onError(CannotInitializeCharacteristicWrite(
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


private val BluetoothGatt.notificationsObs: ArrayMap<UUID, Flowable<Optional<ByteArray>>?> by FieldProperty { ArrayMap() }

/**
 * Because listening to notification require an descriptor write, the observable returned can fire
 * errors from [BluetoothGattDescriptor.write] method like [DescriptorWriteDeviceDisconnected],
 * [CannotInitializeDescriptorWrite] or [DescriptorWriteFailed]
 */
fun BluetoothGatt.rxListenChanges(characteristic: BluetoothGattCharacteristic, indication: Boolean = false): Flowable<Optional<ByteArray>> =
        notificationsObs[characteristic.uuid]?.startWith(Optional(null)) ?:
                Completable
                        .defer {
                            logger?.v(TAG, "setCharacteristicNotification ${characteristic.uuid}} to true")
                            if (setCharacteristicNotification(characteristic, true).not())
                                Completable.error {
                                    CannotInitializeCharacteristicNotification(
                                            device,
                                            characteristic.service,
                                            characteristic,
                                            internalService(),
                                            clientIf(),
                                            characteristic.service?.device())
                                }
                            else
                                rxWrite(characteristic.getDescriptor(GattConst.CLIENT_CHARACTERISTIC_CONFIG), if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        }
                        .andThen(characteristicChangedSubject.toFlowable(BackpressureStrategy.BUFFER)
                                .filter { changedCharacteristic -> changedCharacteristic.uuid == characteristic.uuid }
                                .map { Optional(it.value) }
                                .startWith(Optional(null)))
                        .takeUntil(assertConnected { device, reason -> CharacteristicChangedDeviceDisconnected(device, reason, characteristic.service, characteristic) }.andThen(Flowable.just(Unit)))
                        .doOnTerminate { notificationsObs[characteristic.uuid] = null }
                        .share()
                        .apply { notificationsObs[characteristic.uuid] = this }

fun BluetoothGatt.rxDisableChanges(characteristic: BluetoothGattCharacteristic): Completable =
        Completable
                .fromCallable {
                    logger?.v(TAG, "setCharacteristicNotification ${characteristic.uuid}} to false")
                    setCharacteristicNotification(characteristic, false)
                    notificationsObs[characteristic.uuid] = null
                    Unit
                }
                .andThen(rxWrite(characteristic.getDescriptor(GattConst.CLIENT_CHARACTERISTIC_CONFIG), BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))

fun BluetoothGatt.rxCharacteristicMaybe(uuid: UUID): Maybe<BluetoothGattCharacteristic> =
        Maybe.defer {
            if (services.isEmpty())
                Maybe.error<BluetoothGattCharacteristic>(FindCharacteristicButServiceNotDiscovered(device, uuid))
            else {
                services.forEach { it.characteristics.forEach { if (it.uuid == uuid) return@defer Maybe.just(it) } }
                Maybe.empty()
            }
        }

fun BluetoothGatt.rxCharacteristic(uuid: UUID): Single<BluetoothGattCharacteristic> =
        Single.defer {
            if (services.isEmpty())
                Single.error<BluetoothGattCharacteristic>(FindCharacteristicButServiceNotDiscovered(device, uuid))
            else {
                services.forEach { it.characteristics.forEach { if (it.uuid == uuid) return@defer Single.just(it) } }
                Single.error<BluetoothGattCharacteristic>(CharacteristicNotFound(device, uuid))
            }
        }

fun BluetoothGattCharacteristic.hasIndication() = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0