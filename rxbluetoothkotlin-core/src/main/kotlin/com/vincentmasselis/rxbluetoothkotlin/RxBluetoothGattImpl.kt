package com.vincentmasselis.rxbluetoothkotlin

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresApi
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.*
import com.vincentmasselis.rxbluetoothkotlin.internal.*
import io.reactivex.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.UnicastSubject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * [source] is always closed after a disconnection, it cannot be reopened, you have a to create another one by calling [BluetoothDevice.connectRxGatt]. You can easily
 * find which [BluetoothDevice] was used in the current object by calling [source.device].
 */
@SuppressLint("CheckResult")
class RxBluetoothGattImpl(
    private val logger: Logger?,
    override val source: BluetoothGatt,
    override val callback: RxBluetoothGatt.Callback
) : RxBluetoothGatt {

    private sealed class ConnectionState {
        /** Default state until onConnectionStateChanged emit for the first time */
        object Initializing : ConnectionState()

        object Active : ConnectionState()

        /** [reason] is null when a connection is fired manually by calling [disconnect], -1 if the bluetooth is turned off */
        data class Lost(val reason: Status?) : ConnectionState()
    }

    private val stateSubject = BehaviorSubject.createDefault<ConnectionState>(ConnectionState.Initializing)

    private val bluetoothManager = ContextHolder.context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    // ---------------- Observables which are listen for system input and updates the current state

    /**
     * On the previous Android version, turning off the Bluetooth calls onConnectionStateChange which automatically closes the BluetoothGatt connection. Since Oreo,
     * onConnectionStateChange is no longer called so I have to manually close the connection the be sure that BluetoothGatt will not be used anymore and a new
     * BluetoothGatt will be created.
     */
    private val bluetoothDisp = IntentFilter(ACTION_STATE_CHANGED)
        .toObservable(ContextHolder.context)
        .map { (_, intent) -> intent.getIntExtra(EXTRA_STATE, ERROR) }
        .startWith(Observable.fromCallable {
            if (bluetoothManager.adapter.isEnabled) STATE_ON
            else STATE_OFF
        })
        .distinctUntilChanged()
        .subscribe { if (it != STATE_ON) stateSubject.onNext(ConnectionState.Lost(-1)) }

    private val connectionStateDisp = callback
        .onConnectionState
        .subscribe { (bluetoothState, status) ->
            logger?.w(TAG, "New connection state, state: $bluetoothState, status $status, connectionState: ${stateSubject.value}")
            @Suppress("UNUSED_VARIABLE") val nothing = when (stateSubject.value!!) {
                ConnectionState.Initializing -> {
                    when {
                        bluetoothState == STATE_CONNECTED && status == GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Active)
                        bluetoothState == STATE_CONNECTED && status != GATT_SUCCESS -> throw IllegalStateException("An impossible was case fired")
                        bluetoothState == STATE_DISCONNECTED && status == GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Lost(null))
                        bluetoothState == STATE_DISCONNECTED && status != GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Lost(status))
                        status != GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Lost(status)) // If STATE_CONNECTING or STATE_DISCONNECTING are emitting values != from GATT_SUCCESS, I fire them
                        else -> { // If STATE_CONNECTING or STATE_DISCONNECTING are emitting GATT_SUCCESS values, I ignore them
                        }
                    }
                }
                ConnectionState.Active -> {
                    when {
                        bluetoothState == STATE_CONNECTED && status == GATT_SUCCESS -> throw IllegalStateException("An impossible was case fired")
                        bluetoothState == STATE_CONNECTED && status != GATT_SUCCESS -> throw IllegalStateException("An impossible was case fired")
                        bluetoothState == STATE_DISCONNECTED && status == GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Lost(null))
                        bluetoothState == STATE_DISCONNECTED && status != GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Lost(status))
                        status != GATT_SUCCESS -> stateSubject.onNext(ConnectionState.Lost(status)) // If STATE_CONNECTING or STATE_DISCONNECTING are emitting values != from GATT_SUCCESS, I fire them
                        else -> { // If STATE_CONNECTING or STATE_DISCONNECTING are emitting GATT_SUCCESS values, I ignore them
                        }
                    }
                }
                is ConnectionState.Lost -> {
                    when {
                        bluetoothState == STATE_CONNECTED && status == GATT_SUCCESS -> throw IllegalStateException("An impossible was case fired")
                        bluetoothState == STATE_CONNECTED && status != GATT_SUCCESS -> throw IllegalStateException("An impossible was case fired")
                        bluetoothState == STATE_DISCONNECTED && status == GATT_SUCCESS -> { // Nothing to do, the state is already set to Lost
                        }
                        bluetoothState == STATE_DISCONNECTED && status != GATT_SUCCESS -> { // Nothing to do, the state is already set to Lost
                        }
                        status != GATT_SUCCESS -> { // Nothing to do, the state is already set to Lost
                        }
                        else -> { // If STATE_CONNECTING or STATE_DISCONNECTING are emitting GATT_SUCCESS values, I ignore them
                        }
                    }
                }
            }
        }

    // ---------------- Observables which are listen for the current state and send requests to the system

    init {
        stateSubject
            .filter { it is ConnectionState.Lost }
            .firstOrError()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(Consumer {
                connectionStateDisp.dispose()
                bluetoothDisp.dispose()
                operationQueueDisp.dispose()
                // Automatically disposes when the subject emits
                source.disconnect()
                // If BluetoothGatt is not closed, I can lead to multiple BluetoothGatt instance connected to the same device with the SAME clientIf id, I've seen it on Mi Mix 2s
                // MIUI 10.0 Android 8.0.0.
                source.close()
            })
    }

    // -------------------- Connection

    private object ExpectedDisconnection : Throwable()

    /**
     * [Observable] of [Unit] which emit a unique [Unit] value when the connection handled by [source] can handle I/O operations.
     *
     * It emit `onNext` only once, `onError` are called only when the device disconnects.
     *
     * @return
     * onNext with [Unit] when the connection is ready
     *
     * onComplete when the connection is closed by the user
     *
     * onError with [BluetoothIsTurnedOff] or the result of [exceptionConverter] when a unexpected disconnection occurs, if the connection where expected, [ExpectedDisconnection] is fired.
     */
    private fun livingConnection(exceptionConverter: (device: BluetoothDevice, status: Int) -> DeviceDisconnected): Observable<Unit> = stateSubject
        .switchMap { state ->
            when (state) {
                ConnectionState.Initializing -> Observable.empty() // Nothing we can do, let's wait
                ConnectionState.Active -> Observable.just(Unit) // Excepted case, let's emit
                is ConnectionState.Lost -> Observable.error( // Listening on a lost connection :(
                    state.reason?.let { exceptionConverter(source.device, it) }
                        ?: ExpectedDisconnection
                )
            }
        }

    /**
     * Returns a [Observable] that throws a [SimpleDeviceDisconnected] which contains the [Status]
     * when a disconnection with the device occurs.
     *
     * @return
     * onNext [Unit] if the device is ready for an I/O operation (it is emitted only once).
     *
     * onComplete If the disconnection is excepted (by calling [disconnect] for example), it just completes.
     *
     * onError with [SimpleDeviceDisconnected] or [BluetoothIsTurnedOff]
     *
     * @see BluetoothGattCallback.onConnectionStateChange
     */
    override fun livingConnection(): Observable<Unit> = livingConnection(DeviceDisconnected::SimpleDeviceDisconnected)
        .onErrorResumeNext(Function {
            if (it is ExpectedDisconnection) Observable.empty()
            else Observable.error(it)
        })

    // -------------------- I/O Tools

    private val operationQueue = UnicastSubject.create<Single<Any>>()
    private val operationQueueDisp = operationQueue
        .concatMapSingle({ it.onErrorReturnItem(Unit) /* To avoid disposing which make the queue unavailable */ }, 1)
        .subscribe()

    /**
     * [enqueue] is a useful method which avoid multiple I/O operation on the [BluetoothGatt] at the same time and it does every one on the main thread.
     *
     * @param [exceptionConverter] lambda called when a disconnection occurs. When using this param, you have to create your own [DeviceDisconnected] subclass which contains every data from your
     * calling method. It helps the downstream to handle the exception and find where and why the exception was fired.
     *
     * @param [this] a single which contains a [BluetoothGatt] I/O operation to do.
     */
    @Suppress("UNCHECKED_CAST", "UNUSED_VARIABLE")
    private fun <T> Single<T>.enqueue(exceptionConverter: (device: BluetoothDevice, status: Int) -> DeviceDisconnected): Maybe<T> = this@RxBluetoothGattImpl
        .livingConnection(exceptionConverter) // Surrounding the single to enqueue with livingConnection() to handle disconnection even if the single is not yet enqueued at this time
        .flatMapSingle {
            Single.create<T> { downstream ->
                val singleToEnqueue = this // this is the single to enqueue
                    .subscribeOn(AndroidSchedulers.mainThread())
                    // Value is set to 1 minute because some devices take a long time to detect when the connection is lost. For example, we saw up to 16 seconds on a Nexus 4
                    // between the last call to write and the moment when the system fallback the disconnection.
                    .timeout(1, TimeUnit.MINUTES, Single.error(BluetoothTimeout()))
                    // You don't have to subscribe to this chain, operationQueue will do it for you
                    // It's impossible to cancel a bluetooth I/O operation, so, even if the downstream is not listening for values, you must continue to listen until the end of the
                    // operation, if not, a new operation could be started before the current has finished, this case causes exceptions.
                    .doOnSuccess { downstream.onSuccess(it) }
                    .doOnError { downstream.tryOnError(it) }

                // This case should be impossible because it require the livingConnection to `onNext()` while operationQueueDisp is disposed.
                if (operationQueue.hasObservers().not())
                    throw IllegalStateException("Cannot enqueue the single, there is no subscriber for the queue")

                operationQueue.onNext(singleToEnqueue as Single<Any>)
            }
        }
        .onErrorResumeNext(Function {
            if (it is ExpectedDisconnection) Observable.empty()
            else Observable.error(it)
        })
        .firstElement()


    // -------------------- I/O Operations

    /**
     * Reactive way to read the remote [RSSI] from the [source].
     *
     * @return
     * onSuccess with an Int containing the RSSI if the value is correctly read
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while reading. It can emit [RssiDeviceDisconnected], [CannotInitialize.CannotInitializeRssiReading], [IOFailed.RssiReadingFailed] and
     * [BluetoothIsTurnedOff]
     *
     * @see RSSI
     * @see BluetoothGatt.readRemoteRssi
     * @see BluetoothGattCallback.onReadRemoteRssi
     */
    override fun readRemoteRssi(): Maybe<Int> = Single
        .create<RSSI> { downStream ->
            downStream.setDisposable(callback.onRemoteRssiRead.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "readRemoteRssi")
            if (source.readRemoteRssi().not()) downStream.tryOnError(CannotInitialize.CannotInitializeRssiReading(source.device))
        }
        .enqueue(::RssiDeviceDisconnected)
        .flatMap {
            if (it.status != GATT_SUCCESS) Maybe.error(IOFailed.RssiReadingFailed(it.status, source.device))
            else Maybe.just(it.rssi)
        }

    /**
     * Reactive way to fetch a [List] of [BluetoothGattService] from the [source].
     *
     * @return
     * onSuccess with a the [List] of [BluetoothGattService] when services are correctly read.
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while reading. It can emit [DiscoverServicesDeviceDisconnected], [CannotInitialize.CannotInitializeServicesDiscovering],
     * [IOFailed.ServiceDiscoveringFailed] and [BluetoothIsTurnedOff]
     *
     * @see BluetoothGattService
     * @see BluetoothGatt.discoverServices
     * @see BluetoothGattCallback.onServicesDiscovered
     */
    override fun discoverServices(): Maybe<List<BluetoothGattService>> = Single
        .create<Int> { downStream ->
            downStream.setDisposable(callback.onServicesDiscovered.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "discoverServices")
            if (source.discoverServices().not()) downStream.tryOnError(CannotInitialize.CannotInitializeServicesDiscovering(source.device))
        }
        .enqueue(::DiscoverServicesDeviceDisconnected)
        .flatMap { status ->
            if (status != GATT_SUCCESS) Maybe.error(IOFailed.ServiceDiscoveringFailed(status, source.device))
            else Maybe.just(source.services)
        }

    /**
     * Reactive way to read MTU from [source]
     *
     * @return
     * onSuccess with an Int containing the MTU returned by [source] if the request is successful
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while writing. It can emit [MtuDeviceDisconnected], [CannotInitialize.CannotInitializeMtuRequesting], [IOFailed.MtuRequestingFailed] and
     * [BluetoothIsTurnedOff].
     *
     * @see MTU
     * @see BluetoothGatt.requestMtu
     * @see BluetoothGattCallback.onMtuChanged
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun requestMtu(mtu: Int): Maybe<Int> = Single
        .create<MTU> { downStream ->
            downStream.setDisposable(callback.onMtuChanged.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "requestMtu")
            if (source.requestMtu(mtu).not()) downStream.tryOnError(CannotInitialize.CannotInitializeMtuRequesting(source.device))
        }
        .enqueue(::MtuDeviceDisconnected)
        .flatMap {
            if (it.status != GATT_SUCCESS) Maybe.error(IOFailed.MtuRequestingFailed(it.status, source.device))
            else Maybe.just(it.mtu)
        }

    /**
     * Reactive way to read PHY from [source]
     *
     * If you don't know what PHY is, consider read this before using it :
     * [https://devzone.nordicsemi.com/blogs/1093/taking-a-deeper-dive-into-bluetooth-5]
     *
     * @return
     * onSuccess with the [ConnectionPHY] returned by [source] when the read is successful
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while writing. It can emit [ReadPhyDeviceDisconnected], [IOFailed.PhyReadFailed] and [BluetoothIsTurnedOff].
     *
     * @see PHY
     * @see BluetoothGatt.readPhy
     * @see BluetoothGattCallback.onPhyRead
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun readPhy(): Maybe<ConnectionPHY> = Single
        .create<PHY> { downStream ->
            downStream.setDisposable(callback.onPhyRead.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "readPhy")
            source.readPhy()
        }
        .enqueue(::ReadPhyDeviceDisconnected)
        .flatMap {
            if (it.status != GATT_SUCCESS) Maybe.error(IOFailed.PhyReadFailed(it.connectionPHY, it.status, source.device))
            else Maybe.just(it.connectionPHY)
        }

    /**
     * Reactive way to set preferred PHY to [source]
     *
     * If you don't know what PHY is, consider read this before using it :
     * [https://devzone.nordicsemi.com/blogs/1093/taking-a-deeper-dive-into-bluetooth-5]
     *
     * @return
     * onSuccess with the [ConnectionPHY] returned by [source] if the update is successful
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while writing. It can emit [SetPreferredPhyDeviceDisconnected], [IOFailed.SetPreferredPhyFailed] and [BluetoothIsTurnedOff].
     *
     * @see PHY
     * @see BluetoothGatt.setPreferredPhy
     * @see BluetoothGattCallback.onPhyUpdate
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun setPreferredPhy(connectionPhy: ConnectionPHY, phyOptions: Int): Maybe<ConnectionPHY> = Single
        .create<PHY> { downStream ->
            downStream.setDisposable(callback.onPhyUpdate.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "setPreferredPhy with txPhy ${connectionPhy.transmitter}, rxPhy ${connectionPhy.receiver} and phyOptions $phyOptions")
            source.setPreferredPhy(connectionPhy.transmitter, connectionPhy.receiver, phyOptions)
        }
        .enqueue { device, status -> SetPreferredPhyDeviceDisconnected(connectionPhy, phyOptions, device, status) }
        .flatMap { (connectionPhy, status) ->
            if (status != GATT_SUCCESS) Maybe.error(IOFailed.SetPreferredPhyFailed(connectionPhy, phyOptions, status, source.device))
            else Maybe.just(connectionPhy)
        }

    /**
     * Reactive way to read a value from [characteristic].
     *
     * @return
     * onSuccess with the value [ByteArray] when the [characteristic] is correctly read.
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while reading. It can emit [CharacteristicReadDeviceDisconnected], [CannotInitialize.CannotInitializeCharacteristicReading],
     * [IOFailed.CharacteristicReadingFailed] and [BluetoothIsTurnedOff]
     *
     * @see BluetoothGattCharacteristic
     * @see BluetoothGatt.readCharacteristic
     * @see BluetoothGattCallback.onCharacteristicRead
     */
    override fun read(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray> = Single
        .create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
            downStream.setDisposable(callback.onCharacteristicRead.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "readCharacteristic ${characteristic.uuid}")
            if (source.readCharacteristic(characteristic).not())
                downStream.tryOnError(
                    CannotInitialize.CannotInitializeCharacteristicReading(
                        source.device,
                        characteristic.service,
                        characteristic,
                        characteristic.properties,
                        source.internalService(),
                        source.clientIf(),
                        characteristic.service?.device(),
                        source.isDeviceBusy()
                    )
                )
        }
        .enqueue { device, status -> CharacteristicReadDeviceDisconnected(device, status, characteristic.service, characteristic) }
        .flatMap { (readCharacteristic, status) ->
            if (status != GATT_SUCCESS) Maybe.error(IOFailed.CharacteristicReadingFailed(status, source.device, readCharacteristic.service, readCharacteristic))
            else Maybe.just(readCharacteristic.value)
        }

    /**
     * Reactive way to write a [value] into a [characteristic].
     *
     * @return
     * onSuccess with the written [BluetoothGattCharacteristic] when [value] is correctly wrote
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while writing. It can emit [CharacteristicWriteDeviceDisconnected], [CannotInitialize.CannotInitializeCharacteristicWrite],
     * [IOFailed.CharacteristicWriteFailed] and [BluetoothIsTurnedOff]
     *
     * @see BluetoothGattCharacteristic
     * @see BluetoothGatt.writeCharacteristic
     * @see BluetoothGattCallback.onCharacteristicWrite
     */
    override fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Maybe<BluetoothGattCharacteristic> = Single
        .create<Pair<BluetoothGattCharacteristic, Int>> { downStream ->
            downStream.setDisposable(callback.onCharacteristicWrite.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "writeCharacteristic ${characteristic.uuid} with value ${value.toHexString()}")
            characteristic.value = value
            if (source.writeCharacteristic(characteristic).not())
                downStream.tryOnError(
                    CannotInitialize.CannotInitializeCharacteristicWrite(
                        source.device,
                        characteristic.service,
                        characteristic,
                        value,
                        characteristic.properties,
                        source.internalService(),
                        source.clientIf(),
                        characteristic.service?.device(),
                        source.isDeviceBusy()
                    )
                )
        }
        .enqueue { device, status -> CharacteristicWriteDeviceDisconnected(device, status, characteristic.service, characteristic, value) }
        .flatMap { (wroteCharacteristic, status) ->
            if (status != GATT_SUCCESS) Maybe.error(
                IOFailed.CharacteristicWriteFailed(
                    status,
                    source.device,
                    wroteCharacteristic.service,
                    wroteCharacteristic,
                    value
                )
            )
            else Maybe.just(wroteCharacteristic)
        }

    /**
     * Enables notification for the [characteristic]. Because enabling notification require an descriptor write, the [Maybe] returned can fire every error from the [write] method.
     *
     * @param checkIfAlreadyEnabled Set [checkIfAlreadyEnabled] to true to avoid enabling twice the same notification.
     *
     * @param indication By default, notification is used, you can change this and use indication instead. Indication is a little bit slower than notification because it has an ACK mechanism for every
     * [ByteArray] received for [characteristic]. Learn more [here](https://devzone.nordicsemi.com/f/nordic-q-a/99/notification-indication-difference/533#533).
     *
     * @return
     * onSuccess with the written [BluetoothGattCharacteristic] when notification is enabled
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while turning on notification for [characteristic]. It can emit [ChangeNotificationDeviceDisconnected],
     * [CannotInitialize.CannotInitializeCharacteristicNotification], [DescriptorNotFound] and every error from [write] method (the one used to write on a descriptor)
     */
    override fun enableNotification(characteristic: BluetoothGattCharacteristic, indication: Boolean, checkIfAlreadyEnabled: Boolean): Maybe<BluetoothGattCharacteristic> =
        rxChangeNotification(
            characteristic,
            if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            checkIfAlreadyEnabled
        )

    /**
     * Disables notification for the [characteristic]. Because disabling notification require an descriptor write, the [Maybe] returned can fire every error from the [write] method.
     *
     * @param checkIfAlreadyDisabled Set [checkIfAlreadyDisabled] to true to avoid disabling twice the same notification.
     *
     * @return
     * onSuccess with the written [BluetoothGattCharacteristic] when notification is disabled
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while turning off notification for [characteristic]. It can emit [ChangeNotificationDeviceDisconnected],
     * [CannotInitialize.CannotInitializeCharacteristicNotification], [DescriptorNotFound] and every error from [write] method (the one used to write on a descriptor)
     */
    override fun disableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean): Maybe<BluetoothGattCharacteristic> =
        rxChangeNotification(
            characteristic,
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
            checkIfAlreadyDisabled
        )

    private fun rxChangeNotification(characteristic: BluetoothGattCharacteristic, byteArray: ByteArray, checkIfAlreadyChanged: Boolean): Maybe<BluetoothGattCharacteristic> = Single
        .create<Unit> { downStream ->
            val isEnable = Arrays.equals(byteArray, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE).not()
            logger?.v(TAG, "setCharacteristicNotification ${characteristic.uuid}} to $isEnable")
            if (source.setCharacteristicNotification(characteristic, isEnable).not())
                downStream.tryOnError(
                    CannotInitialize.CannotInitializeCharacteristicNotification(
                        source.device,
                        characteristic.service,
                        characteristic,
                        source.internalService(),
                        source.clientIf(),
                        characteristic.service?.device()
                    )
                )
            else
                downStream.onSuccess(Unit)
        }
        .enqueue { device, status -> ChangeNotificationDeviceDisconnected(device, status, characteristic, byteArray, checkIfAlreadyChanged) }
        .flatMap {
            val notificationDescriptor = characteristic.getDescriptor(GattConsts.NOTIFICATION_DESCRIPTOR_UUID)
            if (notificationDescriptor == null)
                Maybe.error(DescriptorNotFound(source.device, characteristic.uuid, GattConsts.NOTIFICATION_DESCRIPTOR_UUID))
            else
                write(notificationDescriptor, byteArray, checkIfAlreadyChanged)
                    .map { characteristic }
        }

    /**
     * Reactive way to observe [characteristic] changes. This method doesn't subscribe to notification, you have to call [enableNotification] before listening this method.
     *
     * @param composer By default, the source Flowable will handle back pressure by using the [Flowable.onBackpressureBuffer] operator, you can change this behavior by replacing
     * [composer] by your own implementation.
     *
     * @return
     * onNext with the [ByteArray] value from the [characteristic]
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while listening. It can emit [BluetoothIsTurnedOff] and [ListenChangesDeviceDisconnected].
     *
     * @see enableNotification
     * @see BluetoothGattCallback.onCharacteristicChanged
     */
    override fun listenChanges(
        characteristic: BluetoothGattCharacteristic,
        composer: FlowableTransformer<BluetoothGattCharacteristic, BluetoothGattCharacteristic>
    ): Flowable<ByteArray> = livingConnection { device, status -> ListenChangesDeviceDisconnected(device, status, characteristic.service, characteristic) }
        .toFlowable(BackpressureStrategy.ERROR)
        .flatMap { callback.onCharacteristicChanged }
        .compose(composer)
        .filter { changedCharacteristic -> changedCharacteristic.uuid == characteristic.uuid }
        .map { it.value }
        .onErrorResumeNext(Function {
            if (it is ExpectedDisconnection) Flowable.empty()
            else Flowable.error(it)
        })

    /**
     * Reactive way to read a value from a [descriptor].
     *
     * @return
     * onSuccess with the value [ByteArray] when the [descriptor] is correctly read.
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while reading. It can emit [DescriptorReadDeviceDisconnected], [CannotInitialize.CannotInitializeDescriptorReading],
     * [IOFailed.DescriptorReadingFailed] and [BluetoothIsTurnedOff].
     *
     * @see BluetoothGattDescriptor
     * @see BluetoothGatt.readDescriptor
     * @see BluetoothGattCallback.onDescriptorRead
     */
    override fun read(descriptor: BluetoothGattDescriptor): Maybe<ByteArray> = Single
        .create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
            downStream.setDisposable(callback.onDescriptorRead.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "readDescriptor ${descriptor.uuid}")
            if (source.readDescriptor(descriptor).not())
                downStream.tryOnError(
                    CannotInitialize.CannotInitializeDescriptorReading(
                        source.device,
                        descriptor.characteristic?.service,
                        descriptor.characteristic,
                        descriptor,
                        source.internalService(),
                        source.clientIf(),
                        descriptor.characteristic?.service?.device(),
                        source.isDeviceBusy()
                    )
                )
        }
        .enqueue { device, status -> DescriptorReadDeviceDisconnected(device, status, descriptor.characteristic.service, descriptor.characteristic, descriptor) }
        .flatMap { (readDescriptor, status) ->
            if (status != GATT_SUCCESS) Maybe.error(
                IOFailed.DescriptorReadingFailed(
                    status,
                    source.device,
                    readDescriptor.characteristic.service,
                    readDescriptor.characteristic,
                    readDescriptor
                )
            )
            else Maybe.just(readDescriptor.value)
        }

    /**
     * Reactive way to write a [value] into a [descriptor].
     *
     * @return
     * onSuccess with the written [BluetoothGattDescriptor] when [value] is correctly wrote
     *
     * onComplete when the connection of [source] is closed by the user
     *
     * onError if an error has occurred while writing. It can emit [DescriptorWriteDeviceDisconnected], [CannotInitialize.CannotInitializeDescriptorWrite],
     * [IOFailed.DescriptorWriteFailed] and [BluetoothIsTurnedOff].
     *
     * @see BluetoothGattDescriptor
     * @see BluetoothGatt.writeDescriptor
     * @see BluetoothGattCallback.onDescriptorWrite
     */
    override fun write(descriptor: BluetoothGattDescriptor, value: ByteArray, checkIfAlreadyWritten: Boolean): Maybe<BluetoothGattDescriptor> = Single
        .create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
            if (checkIfAlreadyWritten && Arrays.equals(descriptor.value, value)) {
                downStream.onSuccess(descriptor to GATT_SUCCESS)
                return@create
            }

            downStream.setDisposable(callback.onDescriptorWrite.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.tryOnError(it) }))
            logger?.v(TAG, "writeDescriptor ${descriptor.uuid} with value ${value.toHexString()}")
            descriptor.value = value
            if (source.writeDescriptor(descriptor).not())
                downStream.tryOnError(
                    CannotInitialize.CannotInitializeDescriptorWrite(
                        source.device,
                        descriptor.characteristic?.service,
                        descriptor.characteristic,
                        descriptor,
                        value,
                        source.internalService(),
                        source.clientIf(),
                        descriptor.characteristic?.service?.device(),
                        source.isDeviceBusy()
                    )
                )
        }
        .enqueue { device, status -> DescriptorWriteDeviceDisconnected(device, status, descriptor.characteristic.service, descriptor.characteristic, descriptor, value) }
        .flatMap { (wroteDescriptor, status) ->
            if (status != GATT_SUCCESS) Maybe.error(
                IOFailed.DescriptorWriteFailed(
                    status,
                    source.device,
                    wroteDescriptor.characteristic.service,
                    wroteDescriptor.characteristic,
                    wroteDescriptor,
                    value
                )
            )
            else Maybe.just(wroteDescriptor)
        }

    @Synchronized
    override fun disconnect(): Completable = Completable.defer {
        if (stateSubject.value !is ConnectionState.Lost)
            stateSubject.onNext(ConnectionState.Lost(null))
        livingConnection().ignoreElements()
    }

    companion object {
        private const val TAG = "RxBluetoothGattImpl"
    }
}