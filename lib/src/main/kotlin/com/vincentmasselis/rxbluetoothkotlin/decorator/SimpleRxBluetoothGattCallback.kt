package com.vincentmasselis.rxbluetoothkotlin.decorator

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import androidx.annotation.RequiresApi
import com.vincentmasselis.rxbluetoothkotlin.*
import io.reactivex.Flowable
import io.reactivex.Observable

abstract class SimpleRxBluetoothGattCallback(private val concrete: RxBluetoothGatt.Callback) : RxBluetoothGatt.Callback() {
    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) =
        concrete.onReadRemoteRssi(gatt, rssi, status)

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
        concrete.onCharacteristicRead(gatt, characteristic, status)

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
        concrete.onCharacteristicWrite(gatt, characteristic, status)

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) =
        concrete.onServicesDiscovered(gatt, status)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) =
        concrete.onPhyUpdate(gatt, txPhy, rxPhy, status)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) =
        concrete.onMtuChanged(gatt, mtu, status)

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) =
        concrete.onReliableWriteCompleted(gatt, status)

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
        concrete.onDescriptorWrite(gatt, descriptor, status)

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) =
        concrete.onCharacteristicChanged(gatt, characteristic)

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) =
        concrete.onDescriptorRead(gatt, descriptor, status)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) =
        concrete.onPhyRead(gatt, txPhy, rxPhy, status)

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = concrete.onConnectionStateChange(gatt, status, newState)

    override val onConnectionState: Observable<ConnectionState> = concrete.onConnectionState
    override val onRemoteRssiRead: Observable<RSSI> = concrete.onRemoteRssiRead
    override val onServicesDiscovered: Observable<Status> = concrete.onServicesDiscovered
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override val onMtuChanged: Observable<MTU> = concrete.onMtuChanged
    @RequiresApi(Build.VERSION_CODES.O)
    override val onPhyRead: Observable<PHY> = concrete.onPhyRead
    @RequiresApi(Build.VERSION_CODES.O)
    override val onPhyUpdate: Observable<PHY> = concrete.onPhyUpdate
    override val onCharacteristicRead: Observable<Pair<BluetoothGattCharacteristic, Status>> = concrete.onCharacteristicRead
    override val onCharacteristicWrite: Observable<Pair<BluetoothGattCharacteristic, Status>> = concrete.onCharacteristicWrite
    override val onCharacteristicChanged: Flowable<BluetoothGattCharacteristic> = concrete.onCharacteristicChanged
    override val onDescriptorRead: Observable<Pair<BluetoothGattDescriptor, Status>> = concrete.onDescriptorRead
    override val onDescriptorWrite: Observable<Pair<BluetoothGattDescriptor, Status>> = concrete.onDescriptorWrite
    override val onReliableWriteCompleted: Observable<Status> = concrete.onReliableWriteCompleted
}