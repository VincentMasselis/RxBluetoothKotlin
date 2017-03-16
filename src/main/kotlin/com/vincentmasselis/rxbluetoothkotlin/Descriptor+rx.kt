package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattDescriptor
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers

fun BluetoothGatt.rxRead(descriptor: BluetoothGattDescriptor): Maybe<ByteArray> =
        EnqueueSingle(semaphore, assertConnected { device, reason -> DescriptorReadDeviceDisconnected(device, reason, descriptor.characteristic.service, descriptor.characteristic, descriptor) }) {
            Single
                    .create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
                        downStream.setDisposable(descriptorReadSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) }))
                        logger?.v(TAG, "readDescriptor ${descriptor.uuid}")
                        if (readDescriptor(descriptor).not())
                            downStream.onError(CannotInitializeDescriptorReading(
                                    device,
                                    descriptor.characteristic?.service,
                                    descriptor.characteristic,
                                    descriptor,
                                    internalService(),
                                    clientIf(),
                                    descriptor.characteristic?.service?.device(),
                                    isDeviceBusy()))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMap { (readDescriptor, status) ->
                    if (status != BluetoothGatt.GATT_SUCCESS) Maybe.error(DescriptorReadingFailed(status, device, readDescriptor.characteristic.service, readDescriptor.characteristic, readDescriptor))
                    else Maybe.just(readDescriptor.value)
                }

fun BluetoothGatt.rxWrite(descriptor: BluetoothGattDescriptor, value: ByteArray): Completable =
        EnqueueSingle(semaphore, assertConnected { device, reason -> DescriptorWriteDeviceDisconnected(device, reason, descriptor.characteristic.service, descriptor.characteristic, descriptor, value) }) {
            Single
                    .create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
                        downStream.setDisposable(descriptorWriteSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) }))
                        logger?.v(TAG, "writeDescriptor ${descriptor.uuid} with value ${value.toHexString()}")
                        descriptor.value = value
                        if (writeDescriptor(descriptor).not())
                            downStream.onError(CannotInitializeDescriptorWrite(
                                    device,
                                    descriptor.characteristic?.service,
                                    descriptor.characteristic,
                                    descriptor,
                                    value,
                                    internalService(),
                                    clientIf(),
                                    descriptor.characteristic?.service?.device(),
                                    isDeviceBusy()))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMapCompletable { (wroteDescriptor, status) ->
                    if (status != BluetoothGatt.GATT_SUCCESS) Completable.error(DescriptorWriteFailed(status, device, wroteDescriptor.characteristic.service, wroteDescriptor.characteristic, wroteDescriptor, value))
                    else Completable.complete()
                }