package com.masselis.rxbluetoothkotlin

import android.bluetooth.*
import android.os.Build
import androidx.annotation.RequiresApi
import com.masselis.rxbluetoothkotlin.RxBluetoothGatt.Callback
import io.reactivex.*
import io.reactivex.rxjava3.annotations.CheckReturnValue
import io.reactivex.rxjava3.core.*

/**
 * Wrapper of a [BluetoothGatt] instance which adds useful reactive methods. Not all the methods from [BluetoothGatt] are written here, only them which requires asynchronous
 * operations, fortunately you can still access to the system instance of [BluetoothGatt] by calling [source].
 *
 * Most of the methods here are listening to an instance of [Callback], so using a [Callback] decorator could change the behavior of a [RxBluetoothGatt] implementation, it
 * could be useful but also very dangerous. Use the decorators wisely.
 *
 * The default implementation of [RxBluetoothGatt] is [RxBluetoothGattImpl].
 */
interface RxBluetoothGatt {

    val source: BluetoothGatt

    val callback: Callback

    /**
     * Wrapper of a [BluetoothGattCallback] but every of the method is replaced by [Observable]s. Every of the [Observable]s should be called when a method from
     * [BluetoothGattCallback] is called by the system.
     *
     * The default implementation [RxBluetoothGattCallbackImpl] exposes a basic example of implementation.
     *
     * @see RxBluetoothGattCallbackImpl
     */
    interface Callback {

        /** Original callback instance used by the system */
        val source: BluetoothGattCallback

        val onConnectionState: Observable<ConnectionState>

        val onRemoteRssiRead: Observable<RSSI>

        val onServicesDiscovered: Observable<Status>

        val onMtuChanged: Observable<MTU>

        val onPhyRead: Observable<PHY>

        val onPhyUpdate: Observable<PHY>

        val onCharacteristicRead: Observable<Pair<BluetoothGattCharacteristic, Status>>

        val onCharacteristicWrite: Observable<Pair<BluetoothGattCharacteristic, Status>>

        val onCharacteristicChanged: Flowable<BluetoothGattCharacteristic>

        val onDescriptorRead: Observable<Pair<BluetoothGattDescriptor, Status>>

        val onDescriptorWrite: Observable<Pair<BluetoothGattDescriptor, Status>>

        val onReliableWriteCompleted: Observable<Status>

        /** Fired by [livingConnection] when the connection with the sensor is lost */
        class StateDisconnected(val status: Int?) : Throwable()

        /**
         * [Observable] of [Unit] which emit a unique [Unit] value when the connection handled by [source] can handle I/O operations.
         *
         * It emit `onNext` only once, `onError` are called only when the device disconnects.
         *
         * NOTE: Consider using [RxBluetoothGatt.livingConnection] instead. [Callback.livingConnection] is should be only used to transmit connection state to [RxBluetoothGatt]
         *
         * @return
         * onNext with [Unit] when the connection is ready
         *
         * onComplete is never called
         *
         * onError with [BluetoothIsTurnedOff] or [StateDisconnected] when a unexpected disconnection occurs, [StateDisconnected.status] is null if the connection were expected.
         */
        @CheckReturnValue
        fun livingConnection(): Observable<Unit>

        /** Puts the [Callback] into a disconnected state like [onConnectionState] would do if the disconnection was reported by the system */
        fun disconnection()
    }


    /**
     * Returns a [Observable] that throws a [SimpleDeviceDisconnected] which contains the [Status] when a disconnection with the device occurs.
     *
     * @return
     * onNext [Unit] if the device is ready for an I/O operation (it is emitted only once).
     *
     * onComplete If the disconnection is excepted by calling [disconnect].
     *
     * onError with [SimpleDeviceDisconnected] or [BluetoothIsTurnedOff]
     *
     * @see BluetoothGattCallback.onConnectionStateChange
     */
    @CheckReturnValue
    fun livingConnection(): Observable<Unit>

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
    @CheckReturnValue
    fun readRemoteRssi(): Maybe<Int>

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
    @CheckReturnValue
    fun discoverServices(): Maybe<List<BluetoothGattService>>

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
    @CheckReturnValue
    fun requestMtu(mtu: Int): Maybe<Int>

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
    @CheckReturnValue
    fun readPhy(): Maybe<ConnectionPHY>

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
    @CheckReturnValue
    fun setPreferredPhy(connectionPhy: ConnectionPHY, phyOptions: Int): Maybe<ConnectionPHY>

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
    @CheckReturnValue
    fun read(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray>

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
    @CheckReturnValue
    fun write(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Maybe<BluetoothGattCharacteristic>

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
    @CheckReturnValue
    fun enableNotification(
        characteristic: BluetoothGattCharacteristic,
        indication: Boolean = false,
        checkIfAlreadyEnabled: Boolean = true
    ): Maybe<BluetoothGattCharacteristic>

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
    @CheckReturnValue
    fun disableNotification(
        characteristic: BluetoothGattCharacteristic,
        checkIfAlreadyDisabled: Boolean = true
    ): Maybe<BluetoothGattCharacteristic>

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
    @CheckReturnValue
    fun listenChanges(
        characteristic: BluetoothGattCharacteristic,
        composer: FlowableTransformer<BluetoothGattCharacteristic, BluetoothGattCharacteristic> = FlowableTransformer { it.onBackpressureBuffer() }
    ): Flowable<ByteArray>

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
    @CheckReturnValue
    fun read(descriptor: BluetoothGattDescriptor): Maybe<ByteArray>

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
    @CheckReturnValue
    fun write(descriptor: BluetoothGattDescriptor, value: ByteArray): Maybe<BluetoothGattDescriptor>

    /**
     * Disconnects the device. If the device is already disconnected, the last known error is fired again. Fires the same errors than [livingConnection] and also completes if the
     * disconnection was expected.
     */
    @CheckReturnValue
    fun disconnect(): Completable
}