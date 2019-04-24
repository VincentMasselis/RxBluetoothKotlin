package com.vincentmasselis.rxbluetoothkotlin

import android.Manifest
import android.app.Application
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
fun <T : RxBluetoothGatt.Callback, E : RxBluetoothGatt> BluetoothDevice.connectRxGattOfType(
    app: Application,
    autoConnect: Boolean = false,
    logger: Logger? = null,
    callbackConstructor: (() -> T)? = null,
    rxGattConstructor: ((BluetoothGatt, T) -> E)? = null
): Single<E> = Single
    .fromCallable<E> {

        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            logger?.v(TAG, "BLE require ACCESS_COARSE_LOCATION permission")
            throw NeedLocationPermission()
        }

        val btState = if ((app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF

        if (btState == BluetoothAdapter.STATE_OFF) {
            logger?.v(TAG, "Bluetooth is off")
            throw BluetoothIsTurnedOff()
        }

        val callbacks = callbackConstructor?.invoke() ?: RxBluetoothGattCallbackImpl(logger) as T

        logger?.v(TAG, "connectGatt with app $app and autoConnect $autoConnect")
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) connectGatt(app, autoConnect, callbacks, BluetoothDevice.TRANSPORT_LE)
        else connectGatt(app, autoConnect, callbacks)

        if (gatt == null) {
            logger?.v(TAG, "connectGatt method returned null")
            throw NullBluetoothGatt()
        }

        return@fromCallable rxGattConstructor?.invoke(gatt, callbacks) ?: RxBluetoothGattImpl(app, logger, gatt, callbacks) as E
    }
    .subscribeOn(AndroidSchedulers.mainThread())

/** @see connectRxGattOfType */
fun BluetoothDevice.connectRxGatt(
    app: Application,
    autoConnect: Boolean = false,
    logger: Logger? = null,
    callbackConstructor: (() -> RxBluetoothGatt.Callback)? = null,
    rxGattConstructor: ((BluetoothGatt, RxBluetoothGatt.Callback) -> RxBluetoothGatt)? = null
) = connectRxGattOfType(app, autoConnect, logger, callbackConstructor, rxGattConstructor)