package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.CannotInitializeDescriptorReading
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.CannotInitializeDescriptorWrite
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.DescriptorReadDeviceDisconnected
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.DescriptorWriteDeviceDisconnected
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.DescriptorReadingFailed
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.DescriptorWriteFailed
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.*

/**
 * Reactive way to read a value from a [descriptor].
 *
 * @return onSuccess with the value [ByteArray] when the [descriptor] is correctly read.
 * @return onComplete when the [BluetoothGatt] connection is closed by the user
 * @return onError if an error has occurred while reading
 */
fun BluetoothGatt.rxRead(descriptor: BluetoothGattDescriptor): Maybe<ByteArray> =
    enqueue({ device, reason -> DescriptorReadDeviceDisconnected(device, reason, descriptor.characteristic.service, descriptor.characteristic, descriptor) }
        , {
            Single.create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
                downStream.setDisposable(descriptorReadSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                logger?.v(TAG, "readDescriptor ${descriptor.uuid}")
                if (readDescriptor(descriptor).not())
                    downStream.tryOnError(
                        CannotInitializeDescriptorReading(
                            device,
                            descriptor.characteristic?.service,
                            descriptor.characteristic,
                            descriptor,
                            internalService(),
                            clientIf(),
                            descriptor.characteristic?.service?.device(),
                            isDeviceBusy()
                        )
                    )
            }
        })
        .flatMap { (readDescriptor, status) ->
            if (status != BluetoothGatt.GATT_SUCCESS) Maybe.error(
                DescriptorReadingFailed(
                    status,
                    device,
                    readDescriptor.characteristic.service,
                    readDescriptor.characteristic,
                    readDescriptor
                )
            )
            else Maybe.just(readDescriptor.value)
        }

/**
 * Reactive way to write a [value] into a [descriptor].
 * Set [checkIfAlreadyWritten] to true to avoid this write operation if [value] is equals to the
 * current [BluetoothGattDescriptor.getValue].
 *
 * @return onSuccess with the written [descriptor] and when [value] is correctly wrote
 * @return onComplete when the [BluetoothGatt] connection is closed by the user
 * @return onError if an error has occurred while writing
 */
fun BluetoothGatt.rxWrite(descriptor: BluetoothGattDescriptor, value: ByteArray, checkIfAlreadyWritten: Boolean = false): Maybe<BluetoothGattDescriptor> =
    enqueue({ device, reason -> DescriptorWriteDeviceDisconnected(device, reason, descriptor.characteristic.service, descriptor.characteristic, descriptor, value) }
        , {
            Single.create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
                if (checkIfAlreadyWritten && Arrays.equals(descriptor.value, value)) {
                    downStream.onSuccess(descriptor to BluetoothGatt.GATT_SUCCESS)
                    return@create
                }

                downStream.setDisposable(descriptorWriteSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                logger?.v(TAG, "writeDescriptor ${descriptor.uuid} with value ${value.toHexString()}")
                descriptor.value = value
                if (writeDescriptor(descriptor).not())
                    downStream.tryOnError(
                        CannotInitializeDescriptorWrite(
                            device,
                            descriptor.characteristic?.service,
                            descriptor.characteristic,
                            descriptor,
                            value,
                            internalService(),
                            clientIf(),
                            descriptor.characteristic?.service?.device(),
                            isDeviceBusy()
                        )
                    )
            }
        })
        .flatMap { (wroteDescriptor, status) ->
            if (status != BluetoothGatt.GATT_SUCCESS) Maybe.error(
                DescriptorWriteFailed(
                    status,
                    device,
                    wroteDescriptor.characteristic.service,
                    wroteDescriptor.characteristic,
                    wroteDescriptor,
                    value
                )
            )
            else Maybe.just(wroteDescriptor)
        }