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
 * The default implementation is [RxBluetoothGattImpl].
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
    abstract class Callback : BluetoothGattCallback() {

        /** Unlike the other [Observable]s this one MUST emit a subscription if a value is known */
        abstract val onConnectionState: Observable<ConnectionState>

        abstract val onRemoteRssiRead: Observable<RSSI>

        abstract val onServicesDiscovered: Observable<Status>

        abstract val onMtuChanged: Observable<MTU>

        abstract val onPhyRead: Observable<PHY>

        abstract val onPhyUpdate: Observable<PHY>

        abstract val onCharacteristicRead: Observable<Pair<BluetoothGattCharacteristic, Status>>

        abstract val onCharacteristicWrite: Observable<Pair<BluetoothGattCharacteristic, Status>>

        abstract val onCharacteristicChanged: Flowable<BluetoothGattCharacteristic>

        abstract val onDescriptorRead: Observable<Pair<BluetoothGattDescriptor, Status>>

        abstract val onDescriptorWrite: Observable<Pair<BluetoothGattDescriptor, Status>>

        abstract val onReliableWriteCompleted: Observable<Status>

    }

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

    @CheckReturnValue
    fun disconnect(): Completable

}