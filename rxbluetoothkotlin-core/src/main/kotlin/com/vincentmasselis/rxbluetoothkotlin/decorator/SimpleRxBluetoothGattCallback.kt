package com.vincentmasselis.rxbluetoothkotlin.decorator

import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import androidx.annotation.RequiresApi
import com.vincentmasselis.rxbluetoothkotlin.*
import io.reactivex.Flowable
import io.reactivex.Observable

abstract class SimpleRxBluetoothGattCallback(private val concrete: RxBluetoothGatt.Callback) : RxBluetoothGatt.Callback {
    override val source: BluetoothGattCallback = concrete.source
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
    override fun livingConnection(): Observable<Unit> = concrete.livingConnection()
    override fun disconnection() = concrete.disconnection()
}