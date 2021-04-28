package com.masselis.rxbluetoothkotlin

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.os.Build
import androidx.annotation.RequiresApi
import com.masselis.rxbluetoothkotlin.DeviceDisconnected.*
import com.masselis.rxbluetoothkotlin.internal.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.subjects.UnicastSubject
import java.util.concurrent.TimeUnit

/**
 * When a disconnection occurs, connection is always closed after a disconnection and it cannot be reopened. To connect again, you have a to create another instance of [RxBluetoothGatt] by calling
 * [BluetoothDevice.connectRxGatt].
 *
 * You can easily find which [BluetoothDevice] was used in the current object by calling [RxBluetoothGatt.source].
 */
@SuppressLint("CheckResult")
class RxBluetoothGattImpl(
    private val logger: Logger?,
    override val source: BluetoothGatt,
    override val callback: RxBluetoothGatt.Callback
) : RxBluetoothGatt {

    /** Disposes the queue and definitely closes the `BluetoothGatt` instance when a disconnection occurs */
    init {
        callback.livingConnection()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({}, {
                operationQueueDisp.dispose()
                // Automatically disposes when the subject emits
                source.disconnect()
                // If BluetoothGatt is not closed, I can lead to multiple BluetoothGatt instance connected to the same device with the SAME clientIf id, I've seen it on Mi Mix 2s
                // MIUI 10.0 Android 8.0.0.
                source.close()
            })
    }

    // -------------------- Connection tools

    private inline fun <T> Observable<T>.handleCallbackDisconnection(crossinline exceptionConverter: (device: BluetoothDevice, status: Int) -> DeviceDisconnected) =
        this
            .onErrorResumeNext {
                when (it) {
                    is RxBluetoothGatt.Callback.StateDisconnected ->
                        if (it.status == null) Observable.empty()
                        else Observable.error(exceptionConverter(source.device, it.status))

                    else -> Observable.error(it)
                }
            }

    private inline fun <T> Flowable<T>.handleCallbackDisconnection(crossinline exceptionConverter: (device: BluetoothDevice, status: Int) -> DeviceDisconnected) =
        this
            .onErrorResumeNext {
                when (it) {
                    is RxBluetoothGatt.Callback.StateDisconnected ->
                        if (it.status == null) Flowable.empty()
                        else Flowable.error(exceptionConverter(source.device, it.status))

                    else -> Flowable.error(it)
                }
            }

    override fun livingConnection(): Observable<Unit> = callback.livingConnection().handleCallbackDisconnection(::SimpleDeviceDisconnected)

    // -------------------- I/O Tools

    private val operationQueue = UnicastSubject.create<Single<Any>>()
    private val operationQueueDisp = operationQueue
        .concatMapSingle({ it.onErrorReturnItem(Unit) /* To avoid disposing which make the queue unavailable */ }, 1)
        .subscribe()

    /**
     * [enqueue] is a useful method which avoid multiple I/O operation on the [BluetoothGatt] at the same time. Additionally, enqueue does every request on the main thread.
     *
     * @param [exceptionConverter] lambda called when a disconnection occurs. When using this param, you have to create your own [DeviceDisconnected] subclass which contains every
     * data from your calling method. It helps the downstream to handle the exception and find where and why the exception was fired.
     */
    @Suppress("UNCHECKED_CAST", "UNUSED_VARIABLE")
    private fun <T> Single<T>.enqueue(exceptionConverter: (device: BluetoothDevice, status: Int) -> DeviceDisconnected): Maybe<T> = callback
        .livingConnection() // Surrounding the single to enqueue with livingConnection() to handle disconnection even if the single is not yet enqueued at this time
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
                check(operationQueue.hasObservers()) { "Cannot enqueue the single, there is no subscriber for the queue" }

                operationQueue.onNext(singleToEnqueue as Single<Any>)
            }
        }
        .handleCallbackDisconnection(exceptionConverter)
        .firstElement()


    // -------------------- I/O Operations

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

    override fun enableNotification(
        characteristic: BluetoothGattCharacteristic,
        indication: Boolean,
        checkIfAlreadyEnabled: Boolean
    ): Maybe<BluetoothGattCharacteristic> =
        rxChangeNotification(
            characteristic,
            if (indication) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            checkIfAlreadyEnabled
        )

    override fun disableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean): Maybe<BluetoothGattCharacteristic> =
        rxChangeNotification(
            characteristic,
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
            checkIfAlreadyDisabled
        )

    private fun rxChangeNotification(
        characteristic: BluetoothGattCharacteristic,
        byteArray: ByteArray,
        checkIfAlreadyChanged: Boolean
    ): Maybe<BluetoothGattCharacteristic> = Single
        .create<Unit> { downStream ->
            val isEnable = byteArray.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE).not()
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
            val descriptor = characteristic.getDescriptor(GattConsts.NOTIFICATION_DESCRIPTOR_UUID)
            when {
                descriptor == null ->
                    Maybe.error(DescriptorNotFound(source.device, characteristic.uuid, GattConsts.NOTIFICATION_DESCRIPTOR_UUID))

                checkIfAlreadyChanged && descriptor.value?.contentEquals(byteArray) ?: false ->
                    Maybe.just(characteristic)

                else -> write(descriptor, byteArray)
                    .map { characteristic }
            }
        }

    override fun listenChanges(
        characteristic: BluetoothGattCharacteristic,
        composer: FlowableTransformer<BluetoothGattCharacteristic, BluetoothGattCharacteristic>
    ): Flowable<ByteArray> = callback
        .livingConnection()
        .toFlowable(BackpressureStrategy.ERROR)
        .flatMap { callback.onCharacteristicChanged }
        .compose(composer)
        .filter { changedCharacteristic -> changedCharacteristic.uuid == characteristic.uuid }
        .map { it.value }
        .handleCallbackDisconnection { device, status -> ListenChangesDeviceDisconnected(device, status, characteristic.service, characteristic) }

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
        .enqueue { device, status ->
            DescriptorReadDeviceDisconnected(
                device,
                status,
                descriptor.characteristic.service,
                descriptor.characteristic,
                descriptor
            )
        }
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

    override fun write(descriptor: BluetoothGattDescriptor, value: ByteArray): Maybe<BluetoothGattDescriptor> = Single
        .create<Pair<BluetoothGattDescriptor, Int>> { downStream ->
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
        .enqueue { device, status ->
            DescriptorWriteDeviceDisconnected(
                device,
                status,
                descriptor.characteristic.service,
                descriptor.characteristic,
                descriptor,
                value
            )
        }
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

    /** The connection state is not handled by the current class, this job is done by [callback] so I forward the information to [callback] */
    override fun disconnect(): Completable = livingConnection().ignoreElements().doOnSubscribe { callback.disconnection() }

    companion object {
        private const val TAG = "RxBluetoothGattImpl"
    }
}