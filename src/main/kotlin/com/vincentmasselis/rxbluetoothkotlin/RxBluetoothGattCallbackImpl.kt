package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import com.vincentmasselis.rxbluetoothkotlin.internal.toHexString
import io.reactivex.processors.PublishProcessor
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class RxBluetoothGattCallbackImpl(private val logger: Logger?) : RxBluetoothGatt.Callback() {
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
        logger?.v(TAG, "onConnectionStateChange with status $status and newState $newState")
        if (newState == BluetoothProfile.STATE_DISCONNECTED) gatt.close()
        onConnectionState.onNext(ConnectionState(newState, status))
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        logger?.v(TAG, "onReadRemoteRssi with rssi $rssi and status $status")
        onRemoteRssiRead.onNext(RSSI(rssi, status))
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        logger?.v(TAG, "onServicesDiscovered with status $status")
        onServicesDiscovered.onNext(status)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        logger?.v(TAG, "onMtuChanged with mtu $mtu and status $status")
        onMtuChanged.onNext(MTU(mtu, status))
    }

    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        logger?.v(TAG, "onPhyRead with txPhy $txPhy, rxPhy $rxPhy and status $status")
        onPhyRead.onNext(PHY(ConnectionPHY(txPhy, rxPhy), status))
    }

    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        logger?.v(TAG, "onPhyUpdate with txPhy $txPhy, rxPhy $rxPhy and status $status")
        onPhyUpdate.onNext(PHY(ConnectionPHY(txPhy, rxPhy), status))
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        logger?.v(
            TAG,
            "onCharacteristicRead for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status"
        )
        onCharacteristicRead.onNext(characteristic to status)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        logger?.v(
            TAG,
            "onCharacteristicWrite for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status"
        )
        onCharacteristicWrite.onNext(characteristic to status)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        logger?.v(
            TAG,
            "onCharacteristicChanged for characteristic ${characteristic.uuid}, value ${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties}"
        )
        onCharacteristicChanged.onNext(characteristic)
    }

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        logger?.v(TAG, "onDescriptorRead for descriptor ${descriptor.uuid}, value ${descriptor.value.toHexString()}, permissions ${descriptor.permissions}")
        onDescriptorRead.onNext(descriptor to status)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        logger?.v(TAG, "onDescriptorWrite for descriptor ${descriptor.uuid}, value ${descriptor.value.toHexString()}, permissions ${descriptor.permissions}")
        onDescriptorWrite.onNext(descriptor to status)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        logger?.v(TAG, "onReliableWriteCompleted with status $status")
        onReliableWriteCompleted.onNext(status)
    }

    companion object {
        private const val TAG = "RxCallbackImpl"
    }
}