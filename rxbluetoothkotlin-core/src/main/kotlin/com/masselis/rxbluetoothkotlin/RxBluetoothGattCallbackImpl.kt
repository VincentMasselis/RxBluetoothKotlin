package com.masselis.rxbluetoothkotlin

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.IntentFilter
import com.masselis.rxbluetoothkotlin.internal.appContext
import com.masselis.rxbluetoothkotlin.internal.observe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Default wrapper which convert system's callback [BluetoothGattCallback] to [RxBluetoothGatt.Callback] and keeps the current connection status.
 */
@SuppressLint("CheckResult")
public class RxBluetoothGattCallbackImpl : BluetoothGattCallback(), RxBluetoothGatt.Callback {

    // ---------------- Converts methods from `BluetoothGattCallback` to `RxBluetoothGatt.Callback`'s observables.

    override val onConnectionState: PublishSubject<ConnectionState> = PublishSubject.create()
    override val onRemoteRssiRead: PublishSubject<RSSI> = PublishSubject.create()
    override val onServicesDiscovered: PublishSubject<Status> = PublishSubject.create()
    override val onMtuChanged: PublishSubject<MTU> = PublishSubject.create()
    override val onPhyRead: PublishSubject<PHY> = PublishSubject.create()
    override val onPhyUpdate: PublishSubject<PHY> = PublishSubject.create()
    override val onCharacteristicRead: PublishSubject<Pair<BluetoothGattCharacteristic, Status>> =
        PublishSubject.create()
    override val onCharacteristicWrite: PublishSubject<Pair<BluetoothGattCharacteristic, Status>> =
        PublishSubject.create()
    override val onCharacteristicChanged: PublishProcessor<BluetoothGattCharacteristic> =
        PublishProcessor.create()
    override val onDescriptorRead: PublishSubject<Pair<BluetoothGattDescriptor, Status>> =
        PublishSubject.create()
    override val onDescriptorWrite: PublishSubject<Pair<BluetoothGattDescriptor, Status>> =
        PublishSubject.create()
    override val onReliableWriteCompleted: PublishSubject<Status> = PublishSubject.create()

    override val source: BluetoothGattCallback = this

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        onCharacteristicRead.onNext(characteristic to status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        onCharacteristicWrite.onNext(characteristic to status)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        onCharacteristicChanged.onNext(characteristic)
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        onDescriptorRead.onNext(descriptor to status)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        onDescriptorWrite.onNext(descriptor to status)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
        onReliableWriteCompleted.onNext(status)
    }

    // ---------------- Connection state

    private sealed class ConnectionEvent {
        /** Default state until onConnectionStateChanged emit for the first time */
        object Initializing : ConnectionEvent()

        object Active : ConnectionEvent()

        /** [reason] is null when a connection is fired manually by calling [disconnection], -1 if the bluetooth is turned off */
        data class Lost(val reason: Status?) : ConnectionEvent()
    }

    private val stateSubject = BehaviorSubject
        .createDefault<ConnectionEvent>(ConnectionEvent.Initializing)

    // ---------------- System inputs about the current connection, updates the stateSubject if a disconnection is detected

    /**
     * On the previous Android version, turning off the Bluetooth calls onConnectionStateChange which automatically closes the BluetoothGatt connection. Since Oreo,
     * onConnectionStateChange is no longer called so I have to manually close the connection the be sure that BluetoothGatt will not be used anymore and a new
     * BluetoothGatt will be created.
     */
    private val bluetoothDisp = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        .observe()
        .map { intent ->
            intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR
            )
        }
        .startWith(Observable.fromCallable {
            if ((appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled)
                BluetoothAdapter.STATE_ON
            else BluetoothAdapter.STATE_OFF
        })
        .distinctUntilChanged()
        .subscribe { if (it != BluetoothAdapter.STATE_ON) stateSubject.onNext(ConnectionEvent.Lost(-1)) }

    private val connectionStateDisp = onConnectionState
        .subscribe { (bluetoothState, status) ->
            @Suppress("UNUSED_VARIABLE") val nothing = when (stateSubject.value!!) {
                ConnectionEvent.Initializing -> {
                    when {
                        bluetoothState == BluetoothAdapter.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Active
                        )
                        bluetoothState == BluetoothAdapter.STATE_CONNECTED && status != BluetoothGatt.GATT_SUCCESS -> throw IllegalStateException(
                            "An impossible case was fired"
                        )
                        bluetoothState == BluetoothAdapter.STATE_DISCONNECTED && status == BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Lost(null)
                        )
                        bluetoothState == BluetoothAdapter.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Lost(status)
                        )
                        status != BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Lost(
                                status
                            )
                        ) // If STATE_CONNECTING or STATE_DISCONNECTING are emitting values != from GATT_SUCCESS, I fire them
                        else -> { // If STATE_CONNECTING or STATE_DISCONNECTING are emitting GATT_SUCCESS values, I ignore them
                        }
                    }
                }
                ConnectionEvent.Active -> {
                    when {
                        bluetoothState == BluetoothAdapter.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> throw IllegalStateException(
                            "An impossible case was fired"
                        )
                        bluetoothState == BluetoothAdapter.STATE_CONNECTED && status != BluetoothGatt.GATT_SUCCESS -> throw IllegalStateException(
                            "An impossible case was fired"
                        )
                        bluetoothState == BluetoothAdapter.STATE_DISCONNECTED && status == BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Lost(null)
                        )
                        bluetoothState == BluetoothAdapter.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Lost(status)
                        )
                        status != BluetoothGatt.GATT_SUCCESS -> stateSubject.onNext(
                            ConnectionEvent.Lost(
                                status
                            )
                        ) // If STATE_CONNECTING or STATE_DISCONNECTING are emitting values != from GATT_SUCCESS, I fire them
                        else -> { // If STATE_CONNECTING or STATE_DISCONNECTING are emitting GATT_SUCCESS values, I ignore them
                        }
                    }
                }
                is ConnectionEvent.Lost -> {
                    when {
                        bluetoothState == BluetoothAdapter.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> throw IllegalStateException(
                            "An impossible case was fired"
                        )
                        bluetoothState == BluetoothAdapter.STATE_CONNECTED && status != BluetoothGatt.GATT_SUCCESS -> throw IllegalStateException(
                            "An impossible case was fired"
                        )
                        bluetoothState == BluetoothAdapter.STATE_DISCONNECTED && status == BluetoothGatt.GATT_SUCCESS -> { // Nothing to do, the state is already set to Lost
                        }
                        bluetoothState == BluetoothAdapter.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS -> { // Nothing to do, the state is already set to Lost
                        }
                        status != BluetoothGatt.GATT_SUCCESS -> { // Nothing to do, the state is already set to Lost
                        }
                        else -> { // If STATE_CONNECTING or STATE_DISCONNECTING are emitting GATT_SUCCESS values, I ignore them
                        }
                    }
                }
            }
        }

    init {
        livingConnection()
            .subscribe({}, {
                connectionStateDisp.dispose()
                bluetoothDisp.dispose()
            })
    }

    // -------------------- Connection and disconnection tools

    override fun livingConnection(): Observable<Unit> = stateSubject
        .switchMap { state ->
            when (state) {
                ConnectionEvent.Initializing -> Observable.empty() // Nothing we can do, let's wait
                ConnectionEvent.Active -> Observable.just(Unit) // Excepted case, let's emit
                is ConnectionEvent.Lost -> Observable.error( // Listening on a lost connection :(
                    when (state.reason) {
                        -1 -> BluetoothIsTurnedOff()
                        else -> RxBluetoothGatt.Callback.StateDisconnected(state.reason)
                    }
                )
            }
        }

    private val disconnectLock = ReentrantLock()
    override fun disconnection(): Unit = disconnectLock.withLock {
        if (stateSubject.value !is ConnectionEvent.Lost)
            stateSubject.onNext(ConnectionEvent.Lost(null))
    }
}