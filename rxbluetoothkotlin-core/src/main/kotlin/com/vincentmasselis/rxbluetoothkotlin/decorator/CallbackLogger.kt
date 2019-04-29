package com.vincentmasselis.rxbluetoothkotlin.decorator

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import androidx.annotation.RequiresApi
import com.vincentmasselis.rxbluetoothkotlin.Logger
import com.vincentmasselis.rxbluetoothkotlin.RxBluetoothGatt
import com.vincentmasselis.rxbluetoothkotlin.internal.toHexString

class CallbackLogger(private val logger: Logger, concrete: RxBluetoothGatt.Callback) : SimpleRxBluetoothGattCallback(concrete) {

    override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
        logger.v(TAG, "onReadRemoteRssi with rssi $rssi and status $status")
        super.onReadRemoteRssi(gatt, rssi, status)
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        logger.v(
            TAG,
            "onCharacteristicRead for characteristic ${characteristic.uuid}, value 0x${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status"
        )
        super.onCharacteristicRead(gatt, characteristic, status)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        logger.v(
            TAG,
            "onCharacteristicWrite for characteristic ${characteristic.uuid}, value 0x${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties} and status $status"
        )
        super.onCharacteristicWrite(gatt, characteristic, status)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        logger.v(TAG, "onServicesDiscovered with status $status")
        super.onServicesDiscovered(gatt, status)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        logger.v(TAG, "onPhyUpdate with txPhy $txPhy, rxPhy $rxPhy and status $status")
        super.onPhyUpdate(gatt, txPhy, rxPhy, status)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        logger.v(TAG, "onMtuChanged with mtu $mtu and status $status")
        super.onMtuChanged(gatt, mtu, status)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        logger.v(TAG, "onReliableWriteCompleted with status $status")
        super.onReliableWriteCompleted(gatt, status)
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        logger.v(TAG, "onDescriptorWrite for descriptor ${descriptor.uuid}, value 0x${descriptor.value.toHexString()}, permissions ${descriptor.permissions}")
        super.onDescriptorWrite(gatt, descriptor, status)
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        logger.v(
            TAG,
            "onCharacteristicChanged for characteristic ${characteristic.uuid}, value 0x${characteristic.value.toHexString()}, permissions ${characteristic.permissions}, properties ${characteristic.properties}"
        )
        super.onCharacteristicChanged(gatt, characteristic)
    }

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        logger.v(TAG, "onDescriptorRead for descriptor ${descriptor.uuid}, value 0x${descriptor.value.toHexString()}, permissions ${descriptor.permissions}")
        super.onDescriptorRead(gatt, descriptor, status)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        logger.v(TAG, "onPhyRead with txPhy $txPhy, rxPhy $rxPhy and status $status")
        super.onPhyRead(gatt, txPhy, rxPhy, status)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        logger.v(TAG, "onConnectionStateChange with status $status and newState $newState")
        super.onConnectionStateChange(gatt, status, newState)
    }

    companion object {
        const val TAG = "CallbackLogger"
    }
}