package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.*
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.*
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.CharacteristicReadingFailed
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.CharacteristicWriteFailed
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.Function
import java.util.*

/**
 * Reactive way to read a value from a [characteristic].
 *
 * @return
 * onSuccess with the value [ByteArray] when the [characteristic] is correctly read.
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while reading. It can emit [CharacteristicReadDeviceDisconnected], [CannotInitializeCharacteristicReading], [CharacteristicReadingFailed] and
 * [BluetoothIsTurnedOff]
 *
 * @see BluetoothGattCharacteristic
 * @see BluetoothGatt.readCharacteristic
 * @see BluetoothGattCallback.onCharacteristicRead
 */
fun BluetoothGatt.rxRead(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray> =
    enqueue({ device, status -> CharacteristicReadDeviceDisconnected(device, status, characteristic.service, characteristic) }
        , {
            Single.create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
                downStream.setDisposable(characteristicReadSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                logger?.v(TAG, "readCharacteristic ${characteristic.uuid}")
                if (readCharacteristic(characteristic).not())
                    downStream.tryOnError(
                        CannotInitializeCharacteristicReading(
                            device,
                            characteristic.service,
                            characteristic,
                            characteristic.properties,
                            internalService(),
                            clientIf(),
                            characteristic.service?.device(),
                            isDeviceBusy()
                        )
                    )
            }
        })
        .flatMap { (readCharacteristic, status) ->
            if (status != BluetoothGatt.GATT_SUCCESS) Maybe.error(CharacteristicReadingFailed(status, device, readCharacteristic.service, readCharacteristic))
            else Maybe.just(readCharacteristic.value)
        }

/**
 * Reactive way to write a [value] into a [characteristic].
 *
 * @return
 * onSuccess with the written [BluetoothGattCharacteristic] when [value] is correctly wrote
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while writing. It can emit [CharacteristicWriteDeviceDisconnected], [CannotInitializeCharacteristicWrite], [CharacteristicWriteFailed] and
 * [BluetoothIsTurnedOff]
 *
 * @see BluetoothGattCharacteristic
 * @see BluetoothGatt.writeCharacteristic
 * @see BluetoothGattCallback.onCharacteristicWrite
 */
fun BluetoothGatt.rxWrite(characteristic: BluetoothGattCharacteristic, value: ByteArray): Maybe<BluetoothGattCharacteristic> =
    enqueue({ device, status -> CharacteristicWriteDeviceDisconnected(device, status, characteristic.service, characteristic, value) }
        , {
            Single.create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
                downStream.setDisposable(characteristicWriteSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                logger?.v(TAG, "writeCharacteristic ${characteristic.uuid} with value ${value.toHexString()}")
                characteristic.value = value
                if (writeCharacteristic(characteristic).not())
                    downStream.tryOnError(
                        CannotInitializeCharacteristicWrite(
                            device,
                            characteristic.service,
                            characteristic,
                            value,
                            characteristic.properties,
                            internalService(),
                            clientIf(),
                            characteristic.service?.device(),
                            isDeviceBusy()
                        )
                    )
            }
        })
        .flatMap { (wroteCharacteristic, status) ->
            if (status != BluetoothGatt.GATT_SUCCESS) Maybe.error(CharacteristicWriteFailed(status, device, wroteCharacteristic.service, wroteCharacteristic, value))
            else Maybe.just(wroteCharacteristic)
        }

/**
 * Enables notification for the [characteristic]. Because enabling notification require an descriptor write, the [Maybe] returned can fire every error from [BluetoothGatt.rxWrite]
 * method.
 *
 * @param checkIfAlreadyEnabled Set [checkIfAlreadyEnabled] to true to avoid enabling twice the same notification.
 *
 * @param indication By default, notification is used, you can change this and use indication instead. Indication is a little bit slower than notification because it has an ACK mechanism for every
 * [ByteArray] received for [characteristic]. Learn more [here](https://devzone.nordicsemi.com/f/nordic-q-a/99/notification-indication-difference/533#533).
 *
 * @return
 * onSuccess with the written [BluetoothGattCharacteristic] when notification is enabled
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while turning on notification for [characteristic]. It can emit [ChangeNotificationDeviceDisconnected],
 * [CannotInitializeCharacteristicNotification], [DescriptorNotFound] and every error from [BluetoothGatt.rxWrite] method (the one used to write on a descriptor)
 */
fun BluetoothGatt.rxEnableNotification(
    characteristic: BluetoothGattCharacteristic,
    indication: Boolean = false,
    checkIfAlreadyEnabled: Boolean = true
): Maybe<BluetoothGattCharacteristic> =
    rxChangeNotification(
        characteristic,
        if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
        checkIfAlreadyEnabled
    )

/**
 * Disables notification for the [characteristic]. Because disabling notification require an descriptor write, the [Maybe] returned can fire every error from [BluetoothGatt.rxWrite] method.
 *
 * @param checkIfAlreadyDisabled Set [checkIfAlreadyDisabled] to true to avoid disabling twice the same notification.
 *
 * @return
 * onSuccess with the written [BluetoothGattCharacteristic] when notification is disabled
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while turning off notification for [characteristic]. It can emit [ChangeNotificationDeviceDisconnected],
 * [CannotInitializeCharacteristicNotification], [DescriptorNotFound] and every error from [BluetoothGatt.rxWrite] method (the one used to write on a descriptor)
 */
fun BluetoothGatt.rxDisableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean = true): Maybe<BluetoothGattCharacteristic> =
    rxChangeNotification(
        characteristic,
        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
        checkIfAlreadyDisabled
    )

private fun BluetoothGatt.rxChangeNotification(
    characteristic: BluetoothGattCharacteristic,
    byteArray: ByteArray,
    checkIfAlreadyChanged: Boolean
): Maybe<BluetoothGattCharacteristic> =
    enqueue({ device, status -> ChangeNotificationDeviceDisconnected(device, status, characteristic, byteArray, checkIfAlreadyChanged) },
        {
            Single.create<Unit> { downStream ->
                val isEnable = Arrays.equals(byteArray, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE).not()
                logger?.v(TAG, "setCharacteristicNotification ${characteristic.uuid}} to $isEnable")
                if (setCharacteristicNotification(characteristic, isEnable).not())
                    downStream.tryOnError(
                        CannotInitializeCharacteristicNotification(
                            device,
                            characteristic.service,
                            characteristic,
                            internalService(),
                            clientIf(),
                            characteristic.service?.device()
                        )
                    )
                else
                    downStream.onSuccess(Unit)
            }
        })
        .flatMap { _ ->
            val notificationDescriptor = characteristic.getDescriptor(GattConsts.NOTIFICATION_DESCRIPTOR_UUID)
            if (notificationDescriptor == null)
                Maybe.error(DescriptorNotFound(device, characteristic.uuid, GattConsts.NOTIFICATION_DESCRIPTOR_UUID))
            else
                rxWrite(notificationDescriptor, byteArray, checkIfAlreadyChanged)
                    .map { characteristic }
        }

/**
 * Reactive way to observe [characteristic] changes. This method doesn't subscribe to notification, you have to call [rxEnableNotification] before listening this method.
 *
 * @param composer By default, the source Flowable will handle back pressure by using the [Flowable.onBackpressureBuffer] operator, you can change this behavior by replacing
 * [composer] by your own implementation.
 *
 * @return
 * onNext with the [ByteArray] value from the [characteristic]
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while listening. It can emit [BluetoothIsTurnedOff]
 * and [ListenChangesDeviceDisconnected].
 *
 * @see rxEnableNotification
 * @see BluetoothGattCallback.onCharacteristicChanged
 */
fun BluetoothGatt.rxListenChanges(
    characteristic: BluetoothGattCharacteristic,
    composer: ((Flowable<BluetoothGattCharacteristic>) -> Flowable<BluetoothGattCharacteristic>) = { it.onBackpressureBuffer() }
): Flowable<ByteArray> =
    characteristicChangedSubject
        .compose(composer)
        .filter { changedCharacteristic -> changedCharacteristic.uuid == characteristic.uuid }
        .map { it.value }
        .takeUntil(
            livingConnection { device, status -> ListenChangesDeviceDisconnected(device, status, characteristic.service, characteristic) }
                .onErrorResumeNext(Function {
                    if (it is ExceptedDisconnectionException)
                        Observable.empty()
                    else
                        Observable.error(it)
                })
                .ignoreElements()
                .andThen(Flowable.just(Unit))
        )