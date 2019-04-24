package com.vincentmasselis.rxbluetoothkotlin.decorator

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.vincentmasselis.rxbluetoothkotlin.ConnectionPHY
import com.vincentmasselis.rxbluetoothkotlin.RxBluetoothGatt
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Maybe
import io.reactivex.Observable

abstract class SimpleRxBluetoothGatt(private val concrete: RxBluetoothGatt) : RxBluetoothGatt {
    override val source: BluetoothGatt = concrete.source
    override val callback: RxBluetoothGatt.Callback = concrete.callback
    override fun livingConnection(): Observable<Unit> = concrete.livingConnection()
    override fun readRemoteRssi(): Maybe<Int> = concrete.readRemoteRssi()
    override fun discoverServices(): Maybe<List<BluetoothGattService>> = concrete.discoverServices()
    override fun requestMtu(mtu: Int): Maybe<Int> = concrete.requestMtu(mtu)
    override fun readPhy(): Maybe<ConnectionPHY> = concrete.readPhy()
    override fun setPreferredPhy(connectionPhy: ConnectionPHY, phyOptions: Int): Maybe<ConnectionPHY> = concrete.setPreferredPhy(connectionPhy, phyOptions)
    override fun read(characteristic: BluetoothGattCharacteristic): Maybe<ByteArray> = concrete.read(characteristic)
    override fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Maybe<BluetoothGattCharacteristic> = concrete.write(characteristic, value)
    override fun enableNotification(characteristic: BluetoothGattCharacteristic, indication: Boolean, checkIfAlreadyEnabled: Boolean): Maybe<BluetoothGattCharacteristic> =
        concrete.enableNotification(characteristic, indication, checkIfAlreadyEnabled)

    override fun disableNotification(characteristic: BluetoothGattCharacteristic, checkIfAlreadyDisabled: Boolean): Maybe<BluetoothGattCharacteristic> =
        concrete.disableNotification(characteristic, checkIfAlreadyDisabled)

    override fun listenChanges(
        characteristic: BluetoothGattCharacteristic,
        composer: FlowableTransformer<BluetoothGattCharacteristic, BluetoothGattCharacteristic>
    ): Flowable<ByteArray> = concrete.listenChanges(characteristic, composer)

    override fun read(descriptor: BluetoothGattDescriptor): Maybe<ByteArray> = concrete.read(descriptor)
    override fun write(descriptor: BluetoothGattDescriptor, value: ByteArray, checkIfAlreadyWritten: Boolean): Maybe<BluetoothGattDescriptor> =
        concrete.write(descriptor, value, checkIfAlreadyWritten)
}