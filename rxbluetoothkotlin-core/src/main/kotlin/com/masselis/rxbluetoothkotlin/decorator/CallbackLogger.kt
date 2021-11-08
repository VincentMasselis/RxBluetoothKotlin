package com.masselis.rxbluetoothkotlin.decorator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.masselis.rxbluetoothkotlin.*
import com.masselis.rxbluetoothkotlin.internal.toHexString
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable

@SuppressLint("CheckResult")
public class CallbackLogger(logger: Logger, concrete: RxBluetoothGatt.Callback) :
    SimpleRxBluetoothGattCallback(concrete) {

    private val disps = CompositeDisposable()

    init {
        livingConnection().subscribe({}, { disps.dispose() })
    }

    override val onConnectionState: Observable<ConnectionState> = super.onConnectionState.apply {
        subscribe {
            logger.v(
                TAG,
                "onConnectionStateChange with status ${it.status} and newState ${it.state}"
            )
        }.also { disps.add(it) }
    }

    override val onRemoteRssiRead: Observable<RSSI> = super.onRemoteRssiRead.apply {
        subscribe {
            logger.v(
                TAG,
                "onReadRemoteRssi with rssi ${it.rssi} and status ${it.status}"
            )
        }.also { disps.add(it) }
    }

    override val onServicesDiscovered: Observable<Status> = super.onServicesDiscovered.apply {
        subscribe { logger.v(TAG, "onServicesDiscovered with status $it") }
            .also { disps.add(it) }
    }

    override val onMtuChanged: Observable<MTU> = super.onMtuChanged.apply {
        subscribe { logger.v(TAG, "onMtuChanged with mtu ${it.mtu} and status ${it.status}") }
            .also { disps.add(it) }
    }

    override val onPhyRead: Observable<PHY> = super.onPhyRead.apply {
        subscribe {
            logger.v(
                TAG,
                "onPhyRead with txPhy $${it.connectionPHY.transmitter}, rxPhy ${it.connectionPHY.receiver} and status ${it.status}"
            )
        }.also { disps.add(it) }
    }

    override val onPhyUpdate: Observable<PHY> = super.onPhyUpdate.apply {
        subscribe {
            logger.v(
                TAG,
                "onPhyUpdate with txPhy ${it.connectionPHY.transmitter}, rxPhy ${it.connectionPHY.receiver} and status ${it.status}"
            )
        }.also { disps.add(it) }
    }

    override val onCharacteristicRead: Observable<Pair<BluetoothGattCharacteristic, Status>> = super
        .onCharacteristicRead.apply {
            subscribe {
                logger.v(
                    TAG,
                    "onCharacteristicRead for characteristic ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions}, " +
                            "properties ${it.first.properties} and " +
                            "status ${it.second}"
                )
            }.also { disps.add(it) }
        }

    override val onCharacteristicWrite: Observable<Pair<BluetoothGattCharacteristic, Status>> =
        super.onCharacteristicWrite.apply {
            subscribe {
                logger.v(
                    TAG,
                    "onCharacteristicWrite for characteristic ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions}, " +
                            "properties ${it.first.properties} and " +
                            "status ${it.second}"
                )
            }.also { disps.add(it) }
        }

    override val onCharacteristicChanged: Flowable<BluetoothGattCharacteristic> = super
        .onCharacteristicChanged.apply {
            subscribe {
                logger.v(
                    TAG,
                    "onCharacteristicChanged for characteristic ${it.uuid}, " +
                            "value 0x${it.value.toHexString()}, " +
                            "permissions ${it.permissions}, " +
                            "properties ${it.properties}"
                )
            }.also { disps.add(it) }
        }

    override val onDescriptorRead: Observable<Pair<BluetoothGattDescriptor, Status>> = super
        .onDescriptorRead.apply {
            subscribe {
                logger.v(
                    TAG, "onDescriptorRead for descriptor ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions} and " +
                            "status ${it.second}"
                )
            }.also { disps.add(it) }
        }

    override val onDescriptorWrite: Observable<Pair<BluetoothGattDescriptor, Status>> = super
        .onDescriptorWrite.apply {
            subscribe {
                logger.v(
                    TAG, "onDescriptorWrite for descriptor ${it.first.uuid}, " +
                            "value 0x${it.first.value.toHexString()}, " +
                            "permissions ${it.first.permissions} and " +
                            "status ${it.second}"
                )
            }.also { disps.add(it) }
        }

    override val onReliableWriteCompleted: Observable<Status> = super
        .onReliableWriteCompleted.apply {
            subscribe { logger.v(TAG, "onReliableWriteCompleted with status $it") }
                .also { disps.add(it) }
        }

    public companion object {
        public const val TAG: String = "CallbackLogger"
    }
}