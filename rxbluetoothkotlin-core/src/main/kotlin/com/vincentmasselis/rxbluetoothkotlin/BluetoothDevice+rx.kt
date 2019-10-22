package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.vincentmasselis.rxbluetoothkotlin.decorator.CallbackLogger
import com.vincentmasselis.rxbluetoothkotlin.internal.ContextHolder
import com.vincentmasselis.rxbluetoothkotlin.internal.hasPermissions
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers

private const val TAG = "BluetoothDevice+rx"

/**
 * Initialize a connection and returns immediately an instance of [BluetoothGatt]. It doesn't wait
 * for the connection to be established to emit a [BluetoothGatt] instance. To do this you have to
 * listen the [io.reactivex.MaybeObserver.onSuccess] event from the [Maybe] returned by
 * [whenConnectionIsReady] method.
 *
 * @param autoConnect similar to "autoConnect" from the [BluetoothDevice.connectGatt] method. Use it wisely.
 * @param logger Set a [logger] to log every event which occurs from the BLE API (connections, writes, notifications, MTU, missing permissions, etc...).
 * @param rxGattConstructor Defaults uses a [RxBluetoothGattImpl] instance but you can fill you own. It can be useful if you want to add some business logic between the default
 * [RxBluetoothGatt] and the system.
 * @param callbackConstructor Defaults uses a [RxBluetoothGattCallbackImpl] instance but you can fill you own. It can be useful if you want to add some business logic between the default
 * [RxBluetoothGatt.Callback] and the system.
 *
 * @return
 * onSuccess with a [BluetoothGatt] when a [BluetoothGatt] instance is returned by the system API.
 *
 * onError with [NeedLocationPermission], [BluetoothIsTurnedOff] or [NullBluetoothGatt]
 *
 * @see BluetoothGattCallback
 * @see BluetoothDevice.connectGatt
 */
@Suppress("UNCHECKED_CAST")
fun <T : RxBluetoothGatt.Callback, E : RxBluetoothGatt> BluetoothDevice.connectTypedRxGatt(
    autoConnect: Boolean = false,
    logger: Logger? = null,
    callbackConstructor: (() -> T) = { RxBluetoothGattCallbackImpl().let { concrete -> logger?.let { CallbackLogger(it, concrete) } ?: concrete } as T },
    rxGattConstructor: ((BluetoothGatt, T) -> E) = { gatt, callbacks -> RxBluetoothGattImpl(logger, gatt, callbacks) as E }
): Single<E> = Single
    .fromCallable {

        if (hasPermissions().not()) {
            logger?.v(TAG, "BLE require ACCESS_FINE_LOCATION permission")
            throw NeedLocationPermission()
        }

        val btState =
            if ((ContextHolder.context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF

        if (btState == BluetoothAdapter.STATE_OFF) {
            logger?.v(TAG, "Bluetooth is off")
            throw BluetoothIsTurnedOff()
        }

        val callbacks = callbackConstructor()

        logger?.v(TAG, "connectGatt with autoConnect $autoConnect")
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) connectGatt(ContextHolder.context, autoConnect, callbacks, BluetoothDevice.TRANSPORT_LE)
        else connectGatt(ContextHolder.context, autoConnect, callbacks)

        if (gatt == null) {
            logger?.v(TAG, "connectGatt method returned null")
            throw NullBluetoothGatt()
        }

        return@fromCallable rxGattConstructor(gatt, callbacks)
    }
    .subscribeOn(AndroidSchedulers.mainThread())

/** @see connectTypedRxGatt */
fun BluetoothDevice.connectRxGatt(
    autoConnect: Boolean = false,
    logger: Logger? = null,
    callbackConstructor: (() -> RxBluetoothGatt.Callback) = { RxBluetoothGattCallbackImpl().let { concrete -> logger?.let { CallbackLogger(it, concrete) } ?: concrete } },
    rxGattConstructor: ((BluetoothGatt, RxBluetoothGatt.Callback) -> RxBluetoothGatt) = { gatt, callbacks -> RxBluetoothGattImpl(logger, gatt, callbacks) }
) = connectTypedRxGatt(autoConnect, logger, callbackConstructor, rxGattConstructor)