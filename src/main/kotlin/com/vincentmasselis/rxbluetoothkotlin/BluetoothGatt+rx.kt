package com.vincentmasselis.rxbluetoothkotlin

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.*
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.*
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.*
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.Semaphore

internal val TAG = "RxKotlinBleAndroid"

fun BluetoothDevice.rxGatt(context: Context, autoConnect: Boolean = false, logger: Logger? = null): Single<BluetoothGatt> =
        Single
                .create<BluetoothGatt> { downStream ->
                    logger?.v(TAG, "connectGatt with context $context and autoConnect $autoConnect")

                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        logger?.v(TAG, "BLE require ACCESS_COARSE_LOCATION permission")
                        downStream.tryOnError(NeedLocationPermission())
                        return@create
                    }

                    val btState = if ((context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF

                    if (btState == BluetoothAdapter.STATE_OFF) {
                        logger?.v(TAG, "Bluetooth is off")
                        downStream.tryOnError(BluetoothIsTurnedOff())
                        return@create
                    }

                    val callbacks = object : BluetoothGattCallback() {

                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            gatt.logger?.v(TAG, "onConnectionStateChange with status $status and newState $newState")
                            if (newState == BluetoothProfile.STATE_DISCONNECTED) gatt.close()
                            gatt._connectionState.onNext(newState to status)
                        }

                        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                            gatt.logger?.v(TAG, "onReadRemoteRssi with rssi $rssi and status $status")
                            gatt.readRemoteRssiSubject.onNext(rssi to status)
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            gatt.logger?.v(TAG, "onServicesDiscovered with status $status")
                            gatt.servicesDiscoveredSubject.onNext(status)
                        }

                        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                            gatt.logger?.v(TAG, "onMtuChanged with mtu $mtu and status $status")
                            gatt.mtuChangedSubject.onNext(mtu to status)
                        }

                        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                            gatt.logger?.v(TAG, "onCharacteristicRead for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status")
                            gatt.characteristicReadSubject.onNext(characteristic to status)
                        }

                        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                            gatt.logger?.v(TAG, "onCharacteristicWrite for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status")
                            gatt.characteristicWriteSubject.onNext(characteristic to status)
                        }

                        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                            gatt.logger?.v(TAG, "onCharacteristicChanged for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties}")
                            gatt.characteristicChangedSubject.onNext(characteristic)
                        }

                        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                            gatt.logger?.v(TAG, "onDescriptorRead for descriptor ${descriptor.uuid}, value ${descriptor.value.toHexString()}, permissions ${descriptor.permissions}")
                            gatt.descriptorReadSubject.onNext(descriptor to status)
                        }

                        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                            gatt.logger?.v(TAG, "onDescriptorWrite for descriptor ${descriptor.uuid}, value ${descriptor.value.toHexString()}, permissions ${descriptor.permissions}")
                            gatt.descriptorWriteSubject.onNext(descriptor to status)
                        }

                        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
                            gatt.logger?.v(TAG, "onReliableWriteCompleted with status $status")
                            gatt.reliableWriteCompletedSubject.onNext(status)
                        }
                    }

                    val gatt = connectGatt(context, autoConnect, callbacks)

                    if (gatt == null) {
                        logger?.v(TAG, "connectGatt method returned null")
                        downStream.tryOnError(LocalDeviceDoesNotSupportBluetooth())
                        return@create
                    }

                    gatt.callbacks = callbacks
                    gatt.logger = logger
                    gatt.bluetoothState.onNext(btState)

                    downStream.onSuccess(gatt)
                }
                .subscribeOn(AndroidSchedulers.mainThread())

fun BluetoothGatt.rxListenConnection(): Observable<Pair<Int, Int>> =
        Observable
                .create { downStream ->
                    downStream.setDisposable(
                            rxConnectionState
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe { (newState, status) ->
                                        if (newState == BluetoothProfile.STATE_CONNECTING)
                                            downStream.onNext(newState to status)
                                        else if (newState == BluetoothProfile.STATE_CONNECTED) {
                                            downStream.onNext(newState to status)
                                            downStream.onComplete()
                                        } else if (newState == BluetoothProfile.STATE_DISCONNECTING || newState == BluetoothProfile.STATE_DISCONNECTED)
                                            downStream.onNext(newState to status)

                                        //TODO Evaluate if it's a good idea to returns an error only for the condition of the new state
                                        if (status != GATT_SUCCESS) downStream.tryOnError(GattDeviceDisconnected(device, status))
                                    }
                    )
                }

fun BluetoothGatt.rxDisconnect(): Completable =
        Completable
                .create { downStream ->
                    rxConnectionState
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe { (newState) ->
                                if (newState == BluetoothProfile.STATE_CONNECTED || newState == BluetoothProfile.STATE_CONNECTING) disconnect()
                                else if (newState == BluetoothProfile.STATE_DISCONNECTED) downStream.onComplete()
                            }
                            .run { downStream.setDisposable(this) }
                }
                .subscribeOn(AndroidSchedulers.mainThread())

// ------------------------------ Bluetooth state

internal var BluetoothGatt.logger: Logger? by NullableFieldProperty { null }

// ------------------------------ Bluetooth state

private val BluetoothGatt.bluetoothState: BehaviorSubject<Int> by FieldProperty { BehaviorSubject.create() }

// ------------------------------ Callbacks

private var BluetoothGatt.callbacks: BluetoothGattCallback by FieldProperty { object : BluetoothGattCallback() {} }

private var BluetoothGatt.readRemoteRssiSubject: PublishSubject<Pair<Int, Int>> by FieldProperty { PublishSubject.create() }
private var BluetoothGatt.servicesDiscoveredSubject: PublishSubject<Int> by FieldProperty { PublishSubject.create() }
private var BluetoothGatt.mtuChangedSubject: PublishSubject<Pair<Int, Int>> by FieldProperty { PublishSubject.create() }

internal var BluetoothGatt.characteristicReadSubject: PublishSubject<Pair<BluetoothGattCharacteristic, Int>> by FieldProperty { PublishSubject.create() }
internal var BluetoothGatt.characteristicWriteSubject: PublishSubject<Pair<BluetoothGattCharacteristic, Int>> by FieldProperty { PublishSubject.create() }
internal var BluetoothGatt.characteristicChangedSubject: PublishSubject<BluetoothGattCharacteristic> by FieldProperty { PublishSubject.create() }
internal var BluetoothGatt.descriptorReadSubject: PublishSubject<Pair<BluetoothGattDescriptor, Int>> by FieldProperty { PublishSubject.create() }
internal var BluetoothGatt.descriptorWriteSubject: PublishSubject<Pair<BluetoothGattDescriptor, Int>> by FieldProperty { PublishSubject.create() }
internal var BluetoothGatt.reliableWriteCompletedSubject: PublishSubject<Int> by FieldProperty { PublishSubject.create() }


private val BluetoothGatt._connectionState: BehaviorSubject<Pair<Int, Int>> by FieldProperty { BehaviorSubject.create() }

/**
 * The first [Int] of the [Pair] represents the new connection state that match
 * [BluetoothProfile.STATE_DISCONNECTED], [BluetoothProfile.STATE_CONNECTING]
 * [BluetoothProfile.STATE_CONNECTED] or [BluetoothProfile.STATE_DISCONNECTING] values.
 * The second [Int] is not documented by Google and can contains different values between
 * manufacturers. Generally, It match theses values https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h
 */
val BluetoothGatt.rxConnectionState: Observable<Pair<Int, Int>> get() = _connectionState.hide()

/**
 * Returns a completable that emit errors when a disconnection with the device occurs with an error
 * code otherwise it completes.
 */
internal fun BluetoothGatt.assertConnected(exception: (device: BluetoothDevice, reason: Int) -> DeviceDisconnected): Completable =
        Completable.ambArray(
                bluetoothState
                        .filter { it != BluetoothAdapter.STATE_ON }
                        .firstOrError()
                        .flatMapCompletable { Completable.error(BluetoothIsTurnedOff()) },
                rxConnectionState
                        .flatMapCompletable { (newState, status) ->
                            if (newState != BluetoothProfile.STATE_CONNECTED)
                                if (status == GATT_SUCCESS) Completable.complete()
                                else Completable.error(exception(device, status))
                            else Completable.never()
                        }
        )

// ------------------------------ I/O Queue

internal val BluetoothGatt.semaphore: Semaphore by SynchronizedFieldProperty { Semaphore(1) }

// ------------------------------ RSSI

fun BluetoothGatt.rxReadRssi(): Maybe<Int> =
        EnqueueSingle(semaphore, assertConnected(::RssiDeviceDisconnected)) {
            Single
                    .create<Pair<Int, Int>> { downStream ->
                        downStream.setDisposable(readRemoteRssiSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                        logger?.v(TAG, "readRemoteRssi")
                        if (readRemoteRssi().not()) downStream.tryOnError(CannotInitializeRssiReading(device))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMap { (value, status) ->
                    if (status != GATT_SUCCESS) Maybe.error(RssiReadingFailed(status, device))
                    else Maybe.just(value)
                }

// ------------------------------ Service

fun BluetoothGatt.rxDiscoverServices(): Maybe<List<BluetoothGattService>> =
        EnqueueSingle(semaphore, assertConnected(::DiscoverServicesDeviceDisconnected)) {
            Single
                    .create<Int> { downStream ->
                        downStream.setDisposable(servicesDiscoveredSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                        logger?.v(TAG, "discoverServices")
                        if (discoverServices().not()) downStream.tryOnError(CannotInitializeServicesDiscovering(device))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMap { status ->
                    if (status != GATT_SUCCESS) Maybe.error(ServiceDiscoveringFailed(status, device))
                    else Maybe.just(services)
                }

// ------------------------------ MTU

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
fun BluetoothGatt.rxRequestMtu(mtu: Int): Maybe<Int> =
        EnqueueSingle(semaphore, assertConnected(::MtuDeviceDisconnected)) {
            Single
                    .create<Pair<Int, Int>> { downStream ->
                        downStream.setDisposable(mtuChangedSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                        logger?.v(TAG, "requestMtu")
                        if (requestMtu(mtu).not()) downStream.tryOnError(CannotInitializeMtuRequesting(device))
                    }
                    .subscribeOn(AndroidSchedulers.mainThread())
        }
                .flatMap { (mtu, status) ->
                    if (status != GATT_SUCCESS) Maybe.error(MtuRequestingFailed(status, device))
                    else Maybe.just(mtu)
                }