package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.*
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Maybe
import io.reactivex.Observable

interface RxBluetoothGatt {

    val source: BluetoothGatt

    val rxCallback: RxCallback

    interface RxCallback {

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