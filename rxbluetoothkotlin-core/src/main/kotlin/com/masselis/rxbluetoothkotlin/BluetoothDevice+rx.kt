package com.masselis.rxbluetoothkotlin

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.masselis.rxbluetoothkotlin.decorator.CallbackLogger
import com.masselis.rxbluetoothkotlin.internal.appContext
import com.masselis.rxbluetoothkotlin.internal.missingConnectPermission
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single

private const val TAG = "BluetoothDevice+rx"

/**
 * Initialize a connection and returns immediately an instance of [BluetoothGatt]. It doesn't wait
 * for the connection to be established to emit a [BluetoothGatt] instance. To do this you have to
 * listen the [io.reactivex.MaybeObserver.onSuccess] event from the [Maybe] returned by
 * [whenConnectionIsReady] method.
 *
 * @param logger Set a [logger] to log every event which occurs from the BLE API (connections, writes, notifications, MTU, missing permissions, etc...).
 * @param rxGattBuilder Defaults uses a [RxBluetoothGattImpl] instance but you can fill you own. It can be useful if you want to add some business logic between the default
 * [RxBluetoothGatt] implementation and the system.
 * @param connectGattWrapper Default calls [BluetoothDevice.connectGatt]. If you want to use an other variant of [BluetoothDevice.connectGatt] regarding to your requirements,
 * replace the default implementation by your own.
 * @param rxCallbackBuilder Defaults uses a [RxBluetoothGattCallbackImpl] instance but you can fill you own. It can be useful if you want to add some business logic between the
 * default [RxBluetoothGatt.Callback] implementation and the system.
 *
 * @return
 * onSuccess with a [BluetoothGatt] when a [BluetoothGatt] instance is returned by the system API.
 *
 * onError with [NeedLocationPermission], [NeedBluetoothConnectPermission], [BluetoothIsTurnedOff] or [NullBluetoothGatt]
 *
 * @see BluetoothGattCallback
 * @see BluetoothDevice.connectGatt
 */
@SuppressLint("NewApi")
@Suppress("UNCHECKED_CAST")
public fun <T : RxBluetoothGatt.Callback, E : RxBluetoothGatt> BluetoothDevice.connectTypedRxGatt(
    logger: Logger? = null,
    rxCallbackBuilder: () -> T = {
        RxBluetoothGattCallbackImpl().let { concrete ->
            logger?.let { CallbackLogger(it, concrete) } ?: concrete
        } as T
    },
    connectGattWrapper: (Context, BluetoothGattCallback) -> BluetoothGatt? = { context, callback ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) connectGatt(
            context,
            false,
            callback,
            BluetoothDevice.TRANSPORT_LE
        )
        else connectGatt(context, false, callback)
    },
    rxGattBuilder: (BluetoothGatt, T) -> E = { gatt, callbacks ->
        RxBluetoothGattImpl(logger, gatt, callbacks) as E
    }
): Single<E> = Single
    .fromCallable {

        when (missingConnectPermission()) {
            Manifest.permission.BLUETOOTH_CONNECT -> throw NeedBluetoothConnectPermission()
        }

        val btState =
            if ((appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled)
                BluetoothAdapter.STATE_ON
            else
                BluetoothAdapter.STATE_OFF

        if (btState == BluetoothAdapter.STATE_OFF) {
            logger?.v(TAG, "Bluetooth is off")
            throw BluetoothIsTurnedOff()
        }

        val callbacks = rxCallbackBuilder()

        val gatt = connectGattWrapper(appContext, callbacks.source)

        if (gatt == null) {
            logger?.v(TAG, "connectGatt method returned null")
            throw NullBluetoothGatt()
        }

        return@fromCallable rxGattBuilder(gatt, callbacks)
    }.subscribeOn(AndroidSchedulers.mainThread())

/** @see connectTypedRxGatt */
public fun BluetoothDevice.connectRxGatt(
    logger: Logger? = null,
    rxCallbackBuilder: (() -> RxBluetoothGatt.Callback) = {
        RxBluetoothGattCallbackImpl()
            .let { concrete -> logger?.let { CallbackLogger(it, concrete) } ?: concrete }
    },
    connectGattWrapper: (Context, BluetoothGattCallback) -> BluetoothGatt? = { context, callback ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        else
            connectGatt(context, false, callback)
    },
    rxGattBuilder: ((BluetoothGatt, RxBluetoothGatt.Callback) -> RxBluetoothGatt) = { gatt, callbacks ->
        RxBluetoothGattImpl(logger, gatt, callbacks)
    }
): Single<RxBluetoothGatt> =
    connectTypedRxGatt(
        logger,
        rxCallbackBuilder,
        connectGattWrapper,
        rxGattBuilder
    )