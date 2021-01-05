package com.vincentmasselis.rxbluetoothkotlin.decorator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.vincentmasselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxbluetoothkotlin.internal.toHexString
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable

@SuppressLint("CheckResult")
class CallbackLogger(logger: Logger, concrete: RxBluetoothGatt.Callback) : SimpleRxBluetoothGattCallback(concrete) {

    private val disps = CompositeDisposable()

    init {
        livingConnection().subscribe({}, { disps.dispose() })
    }

    override val onConnectionState: Observable<ConnectionState> = super.onConnectionState.also { source ->
        source
            .subscribe { logger.v(TAG, "onConnectionStateChange with status ${it.status} and newState ${it.state}") }
            .also { disps.add(it) }
    }
    override val onRemoteRssiRead: Observable<RSSI> = super.onRemoteRssiRead.also { source ->
        source
            .subscribe { logger.v(TAG, "onReadRemoteRssi with rssi ${it.rssi} and status ${it.status}") }
            .also { disps.add(it) }
    }
    override val onServicesDiscovered: Observable<Status> = super.onServicesDiscovered.also { source ->
        source
            .subscribe { logger.v(TAG, "onServicesDiscovered with status $it") }
            .also { disps.add(it) }
    }
    override val onMtuChanged: Observable<MTU> = super.onMtuChanged.also { source ->
        source
            .subscribe { logger.v(TAG, "onMtuChanged with mtu ${it.mtu} and status ${it.status}") }
            .also { disps.add(it) }
    }
    override val onPhyRead: Observable<PHY> = super.onPhyRead.also { source ->
        source
            .subscribe { logger.v(TAG, "onPhyRead with txPhy $${it.connectionPHY.transmitter}, rxPhy ${it.connectionPHY.receiver} and status ${it.status}") }
            .also { disps.add(it) }
    }
    override val onPhyUpdate: Observable<PHY> = super.onPhyUpdate.also { source ->
        source
            .subscribe { logger.v(TAG, "onPhyUpdate with txPhy ${it.connectionPHY.transmitter}, rxPhy ${it.connectionPHY.receiver} and status ${it.status}") }
            .also { disps.add(it) }
    }
    override val onCharacteristicRead: Observable<Pair<BluetoothGattCharacteristic, Status>> = super.onCharacteristicRead.also { source ->
        source
            .subscribe {
                logger.v(
                    TAG,
                    "onCharacteristicRead for characteristic ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions}, " +
                            "properties ${it.first.properties} and " +
                            "status ${it.second}"
                )
            }
            .also { disps.add(it) }
    }
    override val onCharacteristicWrite: Observable<Pair<BluetoothGattCharacteristic, Status>> = super.onCharacteristicWrite.also { source ->
        source
            .subscribe {
                logger.v(
                    TAG,
                    "onCharacteristicWrite for characteristic ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions}, " +
                            "properties ${it.first.properties} and " +
                            "status ${it.second}"
                )
            }
            .also { disps.add(it) }
    }
    override val onCharacteristicChanged: Flowable<BluetoothGattCharacteristic> = super.onCharacteristicChanged.also { source ->
        source
            .subscribe {
                logger.v(
                    TAG,
                    "onCharacteristicChanged for characteristic ${it.uuid}, " +
                            "value 0x${it.value.toHexString()}, " +
                            "permissions ${it.permissions}, " +
                            "properties ${it.properties}"
                )
            }
            .also { disps.add(it) }
    }
    override val onDescriptorRead: Observable<Pair<BluetoothGattDescriptor, Status>> = super.onDescriptorRead.also { source ->
        source
            .subscribe {
                logger.v(
                    TAG, "onDescriptorRead for descriptor ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions} and " +
                            "status ${it.second}"
                )
            }
            .also { disps.add(it) }
    }
    override val onDescriptorWrite: Observable<Pair<BluetoothGattDescriptor, Status>> = super.onDescriptorWrite.also { source ->
        source
            .subscribe {
                logger.v(
                    TAG, "onDescriptorWrite for descriptor ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions} and " +
                            "status ${it.second}"
                )
            }
            .also { disps.add(it) }
    }
    override val onReliableWriteCompleted: Observable<Status> = super.onReliableWriteCompleted.also { source ->
        source
            .subscribe { logger.v(TAG, "onReliableWriteCompleted with status $it") }
            .also { disps.add(it) }
    }

    companion object {
        const val TAG = "CallbackLogger"
    }
}