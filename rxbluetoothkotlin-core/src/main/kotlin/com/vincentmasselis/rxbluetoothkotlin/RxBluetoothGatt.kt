package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.*
import com.vincentmasselis.rxbluetoothkotlin.RxBluetoothGatt.Callback
import io.reactivex.*
import io.reactivex.annotations.CheckReturnValue

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

        /** Unlike the other Observables this one MUST emit a subscription the last known value */
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
         * @return
         * onNext with [Unit] when the connection is ready
         *
         * onComplete is never called
         *
         * onError with [BluetoothIsTurnedOff] or [StateDisconnected] when a unexpected disconnection occurs, if the connection were expected, [StateDisconnected.status] is null.
         */
        @CheckReturnValue
        fun livingConnection(): Observable<Unit>

        /** Puts the [Callback] into a disconnected state just like [onConnectionState] would do if the disconnection was unexpected */
        fun disconnection()
    }

    /**
     * [Observable] of [Unit] which emit a unique [Unit] value when the connection handled by [source] can handle I/O operations.
     *
     * It emit `onNext` only once, `onError` are called only when the device disconnects.
     *
     * @return
     * onNext with [Unit] when the connection is ready
     *
     * onComplete is called when the disconnection was requested by calling [disconnect]
     *
     * onError with [BluetoothIsTurnedOff] or [DeviceDisconnected.SimpleDeviceDisconnected] when a unexpected disconnection occurs, fires `onComplete` if the connection were
     * expected
     */
    @CheckReturnValue
    fun livingConnection(): Observable<Unit>

    @CheckReturnValue
    fun readRemoteRssi(): Maybe<Int>

    @CheckReturnValue
    fun discoverServices(): Maybe<List<BluetoothGattService>>

    @CheckReturnValue
    fun requestMtu(mtu: Int): Maybe<Int>

    @CheckReturnValue
    fun readPhy(): Maybe<ConnectionPHY>

    @CheckReturnValue
    fun setPreferredPhy(connectionPhy: ConnectionPHY, phyOptions: Int): Maybe<ConnectionPHY>

    @CheckReturnValue
    fun read(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray>

    @CheckReturnValue
    fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Maybe<BluetoothGattCharacteristic>

    @CheckReturnValue
    fun enableNotification(characteristic: BluetoothGattCharacteristic, indication: Boolean = false, checkIfAlreadyEnabled: Boolean = true): Maybe<BluetoothGattCharacteristic>

    @CheckReturnValue
    fun disableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean = true): Maybe<BluetoothGattCharacteristic>

    @CheckReturnValue
    fun listenChanges(
        characteristic: BluetoothGattCharacteristic,
        composer: FlowableTransformer<BluetoothGattCharacteristic, BluetoothGattCharacteristic> = FlowableTransformer { it.onBackpressureBuffer() }
    ): Flowable<ByteArray>

    @CheckReturnValue
    fun read(descriptor: BluetoothGattDescriptor): Maybe<ByteArray>

    @CheckReturnValue
    fun write(descriptor: BluetoothGattDescriptor, value: ByteArray, checkIfAlreadyWritten: Boolean = false): Maybe<BluetoothGattDescriptor>

    /**
     * Disconnects the device. If the device is already disconnected, the last known error is fired again. Fires the same errors than [livingConnection] and also completes if the
     * disconnection was expected.
     */
    @CheckReturnValue
    fun disconnect(): Completable
}