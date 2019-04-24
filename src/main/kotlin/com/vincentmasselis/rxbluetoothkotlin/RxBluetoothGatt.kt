package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.*
import com.vincentmasselis.rxbluetoothkotlin.RxBluetoothGatt.Callback
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Maybe
import io.reactivex.Observable

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
     * The default implementation [RxCallbackImpl] exposes a basic example of implementation.
     *
     * @see RxCallbackImpl
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

    fun livingConnection(): Observable<Unit>

    fun readRemoteRssi(): Maybe<Int>

    fun discoverServices(): Maybe<List<BluetoothGattService>>

    fun requestMtu(mtu: Int): Maybe<Int>

    fun readPhy(): Maybe<ConnectionPHY>

    fun setPreferredPhy(connectionPhy: ConnectionPHY, phyOptions: Int): Maybe<ConnectionPHY>

    fun read(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray>

    fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Maybe<BluetoothGattCharacteristic>

    fun enableNotification(characteristic: BluetoothGattCharacteristic, indication: Boolean = false, checkIfAlreadyEnabled: Boolean = true): Maybe<BluetoothGattCharacteristic>

    fun disableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean = true): Maybe<BluetoothGattCharacteristic>

    fun listenChanges(
        characteristic: BluetoothGattCharacteristic,
        composer: FlowableTransformer<BluetoothGattCharacteristic, BluetoothGattCharacteristic> = FlowableTransformer { it.onBackpressureBuffer() }
    ): Flowable<ByteArray>

    fun read(descriptor: BluetoothGattDescriptor): Maybe<ByteArray>

    fun write(descriptor: BluetoothGattDescriptor, value: ByteArray, checkIfAlreadyWritten: Boolean = false): Maybe<BluetoothGattDescriptor>

}