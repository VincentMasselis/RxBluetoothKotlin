package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import io.reactivex.processors.PublishProcessor
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class RxBluetoothGattCallbackImpl : RxBluetoothGatt.Callback() {
    override val onConnectionState = BehaviorSubject.create<ConnectionState>()
    override val onRemoteRssiRead = PublishSubject.create<RSSI>()
    override val onServicesDiscovered = PublishSubject.create<Status>()
    override val onMtuChanged = PublishSubject.create<MTU>()
    override val onPhyRead = PublishSubject.create<PHY>()
    override val onPhyUpdate = PublishSubject.create<PHY>()
    override val onCharacteristicRead = PublishSubject.create<Pair<BluetoothGattCharacteristic, Status>>()
    override val onCharacteristicWrite = PublishSubject.create<Pair<BluetoothGattCharacteristic, Status>>()
    override val onCharacteristicChanged = PublishProcessor.create<BluetoothGattCharacteristic>()
    override val onDescriptorRead = PublishSubject.create<Pair<BluetoothGattDescriptor, Status>>()
    override val onDescriptorWrite = PublishSubject.create<Pair<BluetoothGattDescriptor, Status>>()
    override val onReliableWriteCompleted = PublishSubject.create<Status>()

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_DISCONNECTED) gatt.close()
        onConnectionState.onNext(ConnectionState(newState, status))
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        onRemoteRssiRead.onNext(RSSI(rssi, status))
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        onServicesDiscovered.onNext(status)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        onMtuChanged.onNext(MTU(mtu, status))
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        onPhyRead.onNext(PHY(ConnectionPHY(txPhy, rxPhy), status))
    }

    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        onPhyUpdate.onNext(PHY(ConnectionPHY(txPhy, rxPhy), status))
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        onCharacteristicRead.onNext(characteristic to status)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        onCharacteristicWrite.onNext(characteristic to status)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {

        onCharacteristicChanged.onNext(characteristic)
    }

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        onDescriptorRead.onNext(descriptor to status)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        onDescriptorWrite.onNext(descriptor to status)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        onReliableWriteCompleted.onNext(status)
    }
}