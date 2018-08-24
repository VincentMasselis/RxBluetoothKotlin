@file:Suppress("unused")

package com.vincentmasselis.rxbluetoothkotlin

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.*
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.*
import com.vincentmasselis.rxbluetoothkotlin.IOFailed.*
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Function
import io.reactivex.processors.PublishProcessor
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

internal const val TAG = "RxBluetoothKotlin"

/**
 * Initialize a connection and returns immediately an instance of [BluetoothGatt]. It doesn't wait
 * for the connection to be established to emit a [BluetoothGatt] instance. To do this you have to
 * listen the [io.reactivex.MaybeObserver.onSuccess] event from the [Maybe] returned by
 * [rxWhenConnectionIsReady] method.
 *
 * @param autoConnect similar to "autoConnect" from the [BluetoothDevice.connectGatt] method. Use it wisely.
 * @param logger Set a [logger] to log every event which occurs from the BLE API (connections, writes, notifications, MTU, missing permissions, etc...).
 *
 * @return
 * onSuccess with a [BluetoothGatt] when a [BluetoothGatt] instance is returned by the system API.
 *
 * onError with [NeedLocationPermission], [BluetoothIsTurnedOff] or [NullBluetoothGatt]
 *
 * @see BluetoothGattCallback
 * @see BluetoothDevice.connectGatt
 */
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
                    gatt.connectionStateSubject.onNext(newState to status)
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

                override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
                    gatt.logger?.v(TAG, "onPhyRead with txPhy $txPhy, rxPhy $rxPhy and status $status")
                    gatt.phyReadSubject.onNext(ConnectionPhy(txPhy, rxPhy) to status)
                }

                override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
                    gatt.logger?.v(TAG, "onPhyUpdate with txPhy $txPhy, rxPhy $rxPhy and status $status")
                    gatt.phyUpdateSubject.onNext(ConnectionPhy(txPhy, rxPhy) to status)
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    gatt.logger?.v(
                        TAG,
                        "onCharacteristicRead for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status"
                    )
                    gatt.characteristicReadSubject.onNext(characteristic to status)
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    gatt.logger?.v(
                        TAG,
                        "onCharacteristicWrite for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status"
                    )
                    gatt.characteristicWriteSubject.onNext(characteristic to status)
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    gatt.logger?.v(
                        TAG,
                        "onCharacteristicChanged for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties}"
                    )
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
                downStream.tryOnError(NullBluetoothGatt())
                return@create
            }

            gatt.context = context
            gatt.logger = logger

            downStream.onSuccess(gatt)
        }
        .subscribeOn(AndroidSchedulers.mainThread())


// ------------------------------ Additional properties

private var BluetoothGatt.context: Context? by NullableFieldProperty { null }
private val BluetoothGatt.bluetoothManager: BluetoothManager by SynchronizedFieldProperty { context!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }

// ------------------------------ Logging

internal var BluetoothGatt.logger: Logger? by NullableFieldProperty { null }

// ------------------------------ Callbacks

/**
 * Represents values that matches [BluetoothProfile.STATE_DISCONNECTED],
 * [BluetoothProfile.STATE_CONNECTING], [BluetoothProfile.STATE_CONNECTED]
 * or [BluetoothProfile.STATE_DISCONNECTING]
 *
 * @see BluetoothGattCallback.onConnectionStateChange
 */
typealias NewState = Int

/**
 * The second [Int] is not documented by the Android team and can contains different
 * values between manufacturers. Generally, It match theses values :
 * [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h]
 *
 * @see BluetoothGattCallback.onConnectionStateChange
 */
typealias Status = Int

typealias ConnectionState = Pair<NewState, Status>

private val BluetoothGatt.connectionStateSubject: BehaviorSubject<ConnectionState> by SynchronizedFieldProperty { BehaviorSubject.create() }
val BluetoothGatt.rxConnectionState: Observable<ConnectionState> by SynchronizedFieldProperty { connectionStateSubject.hide() }

typealias Rssi = Int

private val BluetoothGatt.readRemoteRssiSubject: PublishSubject<Pair<Rssi, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxReadRemoteRssi: Observable<Pair<Rssi, Status>> by SynchronizedFieldProperty { readRemoteRssiSubject.hide() }
private val BluetoothGatt.servicesDiscoveredSubject: PublishSubject<Status> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxServicesDiscovered: Observable<Status> by SynchronizedFieldProperty { servicesDiscoveredSubject.hide() }

typealias Mtu = Int

private val BluetoothGatt.mtuChangedSubject: PublishSubject<Pair<Mtu, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxMtuChanged: Observable<Pair<Mtu, Status>> by SynchronizedFieldProperty { mtuChangedSubject.hide() }

typealias Phy = Int

data class ConnectionPhy(val transmitter: Phy, val receiver: Phy)

private val BluetoothGatt.phyReadSubject: PublishSubject<Pair<ConnectionPhy, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxPhyRead: Observable<Pair<ConnectionPhy, Status>> by SynchronizedFieldProperty { phyReadSubject.hide() }
private val BluetoothGatt.phyUpdateSubject: PublishSubject<Pair<ConnectionPhy, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxPhyUpdate: Observable<Pair<ConnectionPhy, Status>> by SynchronizedFieldProperty { phyUpdateSubject.hide() }

internal val BluetoothGatt.characteristicReadSubject: PublishSubject<Pair<BluetoothGattCharacteristic, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxCharacteristicRead: Observable<Pair<BluetoothGattCharacteristic, Status>> by SynchronizedFieldProperty { characteristicReadSubject.hide() }
internal val BluetoothGatt.characteristicWriteSubject: PublishSubject<Pair<BluetoothGattCharacteristic, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxCharacteristicWrite: Observable<Pair<BluetoothGattCharacteristic, Status>> by SynchronizedFieldProperty { characteristicWriteSubject.hide() }
internal val BluetoothGatt.characteristicChangedSubject: PublishProcessor<BluetoothGattCharacteristic> by SynchronizedFieldProperty { PublishProcessor.create() }
val BluetoothGatt.rxCharacteristicChanged: Flowable<BluetoothGattCharacteristic> by SynchronizedFieldProperty { characteristicChangedSubject.hide() }
internal val BluetoothGatt.descriptorReadSubject: PublishSubject<Pair<BluetoothGattDescriptor, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxDescriptorRead: Observable<Pair<BluetoothGattDescriptor, Status>> by SynchronizedFieldProperty { descriptorReadSubject.hide() }
internal val BluetoothGatt.descriptorWriteSubject: PublishSubject<Pair<BluetoothGattDescriptor, Status>> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxDescriptorWrite: Observable<Pair<BluetoothGattDescriptor, Status>> by SynchronizedFieldProperty { descriptorWriteSubject.hide() }
internal val BluetoothGatt.reliableWriteCompletedSubject: PublishSubject<Status> by SynchronizedFieldProperty { PublishSubject.create() }
val BluetoothGatt.rxReliableWriteCompleted: Observable<Status> by SynchronizedFieldProperty { reliableWriteCompletedSubject.hide() }

/**
 * [Observable] of [Unit] which emit a unique [Unit] value when the connection handled by [this] can handle I/O operations.
 *
 * It call [exceptionConverter] when a disconnection an unexpected exception is fired and throws the result in the [Observable].
 * In can throw [ExceptedDisconnectionException] and [BluetoothIsTurnedOff].
 *
 * It never completes.
 */
internal fun BluetoothGatt.livingConnection(exceptionConverter: (device: BluetoothDevice, status: Int) -> DeviceDisconnected): Observable<Unit> = Observable
    .create<Unit> { downStream ->
        downStream.setDisposable(
            connectionStateSubject
                .subscribe { (newState, status) ->
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == GATT_SUCCESS) {
                        //Check if the device is really connected, some specific phones don't call rxConnectionState whereas the device is no longer connected (for example, on the Nexus 5X 8.1, turning off the Bluetooth doesn't fire onConnectionStateChange)
                        val isDeviceReallyConnected = bluetoothManager
                            .getConnectedDevices(BluetoothProfile.GATT)
                            .any { it.address == device.address }
                        if (isDeviceReallyConnected)
                            downStream.onNext(Unit)
                        else
                            downStream.tryOnError(exceptionConverter(device, -1))
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED)
                        if (status != GATT_SUCCESS)
                            downStream.tryOnError(exceptionConverter(device, status))
                        else
                            downStream.tryOnError(ExceptedDisconnectionException())
                    else if (status != GATT_SUCCESS)
                        downStream.tryOnError(exceptionConverter(device, status))
                })
    }
    .takeUntil( // Forward the [BluetoothIsTurnedOff] exception to livingConnection when it occurs
        IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            .toObservable(context!!)
            .map { (_, intent) -> intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) }
            .startWith(
                if ((context!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled)
                    BluetoothAdapter.STATE_ON
                else
                    BluetoothAdapter.STATE_OFF
            )
            .filter { it != BluetoothAdapter.STATE_ON }
            .firstOrError()
            .flatMapCompletable { Completable.error(BluetoothIsTurnedOff()) }
            .toObservable<Unit>()
    )
    .apply {
        if (context == null) // Check that [this] was created by calling [rxGatt]
            throw IllegalStateException("To continue, you have to connect by using rxGatt()")
    }

/**
 * Returns a [Observable] that throws a [SimpleDeviceDisconnected] which contains the [Status]
 * when a disconnection with the device occurs.
 *
 * @return
 * onNext [Unit] if the device is ready for an I/O operation (it is emitted only once).
 *
 * onComplete If the disconnection is excepted (by calling [rxDisconnect] for example), it just completes.
 *
 * onError with [SimpleDeviceDisconnected] or [BluetoothIsTurnedOff]
 *
 * @see BluetoothGattCallback.onConnectionStateChange
 */
fun BluetoothGatt.rxLivingConnection(): Observable<Unit> =
    livingConnection(::SimpleDeviceDisconnected)
        .onErrorResumeNext(Function {
            if (it is ExceptedDisconnectionException)
                Observable.empty()
            else
                Observable.error(it)
        })


// ------------------------------ RSSI

/**
 * Reactive way to read the remote [Rssi] from the [this].
 *
 * @return
 * onSuccess with [Rssi] when the value is correctly read
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while reading. It can emit [RssiDeviceDisconnected], [CannotInitializeRssiReading], [RssiReadingFailed] and
 * [BluetoothIsTurnedOff]
 *
 * @see BluetoothGatt.readRemoteRssi
 * @see BluetoothGattCallback.onReadRemoteRssi
 */
fun BluetoothGatt.rxReadRemoteRssi(): Maybe<Rssi> =
    enqueue(::RssiDeviceDisconnected) {
        Single.create<Pair<Int, Int>> { downStream ->
            downStream.setDisposable(readRemoteRssiSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "readRemoteRssi")
            if (readRemoteRssi().not()) downStream.tryOnError(CannotInitializeRssiReading(device))
        }
    }
        .flatMap { (value, status) ->
            if (status != GATT_SUCCESS) Maybe.error(RssiReadingFailed(status, device))
            else Maybe.just(value)
        }

// ------------------------------ Service

/**
 * Reactive way to fetch a the [List] of [BluetoothGattService] from the [BluetoothGatt].
 *
 * @return
 * onSuccess with a the [List] of [BluetoothGattService] when services are correctly read.
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while reading. It can emit [DiscoverServicesDeviceDisconnected], [CannotInitializeServicesDiscovering], [ServiceDiscoveringFailed] and
 * [BluetoothIsTurnedOff]
 *
 * @see BluetoothGattService
 * @see BluetoothGatt.discoverServices
 * @see BluetoothGattCallback.onServicesDiscovered
 */
fun BluetoothGatt.rxDiscoverServices(): Maybe<List<BluetoothGattService>> =
    enqueue(::DiscoverServicesDeviceDisconnected) {
        Single.create<Int> { downStream ->
            downStream.setDisposable(servicesDiscoveredSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "discoverServices")
            if (discoverServices().not()) downStream.tryOnError(CannotInitializeServicesDiscovering(device))
        }
    }
        .flatMap { status ->
            if (status != GATT_SUCCESS) Maybe.error(ServiceDiscoveringFailed(status, device))
            else Maybe.just(services)
        }

// ------------------------------ MTU

/**
 * Reactive way to read MTU from [this]
 *
 * @return
 * onSuccess with the [Mtu] returned by [this] when the request is successful
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while writing. It can emit [MtuDeviceDisconnected], [CannotInitializeMtuRequesting], [MtuRequestingFailed] and [BluetoothIsTurnedOff].
 *
 * @see Mtu
 * @see BluetoothGatt.requestMtu
 * @see BluetoothGattCallback.onMtuChanged
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
fun BluetoothGatt.rxRequestMtu(mtu: Int): Maybe<Mtu> =
    enqueue(::MtuDeviceDisconnected) {
        Single
            .create<Pair<Int, Int>> { downStream ->
                downStream.setDisposable(mtuChangedSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                logger?.v(TAG, "requestMtu")
                if (requestMtu(mtu).not()) downStream.tryOnError(CannotInitializeMtuRequesting(device))
            }
    }
        .flatMap { (mtu, status) ->
            if (status != GATT_SUCCESS) Maybe.error(MtuRequestingFailed(status, device))
            else Maybe.just(mtu)
        }

// ------------------------------ PHY

/**
 * Reactive way to read PHY from [this]
 *
 * If you don't know what PHY is, consider read this before using it :
 * [https://devzone.nordicsemi.com/blogs/1093/taking-a-deeper-dive-into-bluetooth-5]
 *
 * @return
 * onSuccess with the [ConnectionPhy] returned by [this] when the read is successful
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while writing. It can emit [ReadPhyDeviceDisconnected], [PhyReadFailed] and [BluetoothIsTurnedOff].
 *
 * @see Phy
 * @see BluetoothGatt.readPhy
 * @see BluetoothGattCallback.onPhyRead
 */
@RequiresApi(api = Build.VERSION_CODES.O)
fun BluetoothGatt.rxReadPhy(): Maybe<ConnectionPhy> =
    enqueue(::ReadPhyDeviceDisconnected) {
        Single
            .create<Pair<ConnectionPhy, Int>> { downStream ->
                downStream.setDisposable(phyReadSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                logger?.v(TAG, "readPhy")
                readPhy()
            }
    }
        .flatMap { (connectionPhy, status) ->
            if (status != GATT_SUCCESS) Maybe.error(PhyReadFailed(connectionPhy, status, device))
            else Maybe.just(connectionPhy)
        }

/**
 * Reactive way to set preferred PHY to [this]
 *
 * If you don't know what PHY is, consider read this before using it :
 * [https://devzone.nordicsemi.com/blogs/1093/taking-a-deeper-dive-into-bluetooth-5]
 *
 * @return
 * onSuccess with the [ConnectionPhy] returned by [this] when the request has ended
 *
 * onComplete when the connection of [this] is closed by the user
 *
 * onError if an error has occurred while writing. It can emit [SetPreferredPhyDeviceDisconnected], [SetPreferredPhyFailed] and [BluetoothIsTurnedOff].
 *
 * @see Phy
 * @see BluetoothGatt.setPreferredPhy
 * @see BluetoothGattCallback.onPhyUpdate
 */
@RequiresApi(api = Build.VERSION_CODES.O)
fun BluetoothGatt.rxSetPreferredPhy(connectionPhy: ConnectionPhy, phyOptions: Int): Maybe<ConnectionPhy> =
    enqueue({ device, status -> SetPreferredPhyDeviceDisconnected(connectionPhy, phyOptions, device, status) }
        , {
            Single
                .create<Pair<ConnectionPhy, Int>> { downStream ->
                    downStream.setDisposable(phyUpdateSubject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
                    logger?.v(TAG, "setPreferredPhy with txPhy ${connectionPhy.transmitter}, rxPhy ${connectionPhy.receiver} and phyOptions $phyOptions")
                    setPreferredPhy(connectionPhy.transmitter, connectionPhy.receiver, phyOptions)
                }
        })
        .flatMap { (connectionPhy, status) ->
            if (status != GATT_SUCCESS) Maybe.error(SetPreferredPhyFailed(connectionPhy, phyOptions, status, device))
            else Maybe.just(connectionPhy)
        }
