package com.masselis.rxbluetoothkotlin

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import com.masselis.rxbluetoothkotlin.internal.appContext
import com.masselis.rxbluetoothkotlin.internal.observe
import io.reactivex.rxjava3.core.Observable
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.TimeUnit

internal const val DEVICE_NAME = "Pixel 6 Pro"

internal val bluetoothManager =
    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
internal val bluetoothStateObs = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    .observe()
    .map { intent ->
        intent.getIntExtra(
            BluetoothAdapter.EXTRA_STATE,
            BluetoothAdapter.ERROR
        )
    }
    .startWith(Observable.fromCallable {
        if (bluetoothManager.adapter.isEnabled) BluetoothAdapter.STATE_ON
        else BluetoothAdapter.STATE_OFF
    })
    .replay(1)
    .refCount()

internal fun rebootBluetooth() {
    val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter.disable()
    bluetoothStateObs.filter { it == BluetoothAdapter.STATE_OFF }.firstOrError().test()
        .await(5, TimeUnit.SECONDS)
    bluetoothManager.adapter.enable()
    bluetoothStateObs.filter { it == BluetoothAdapter.STATE_ON }.firstOrError().test()
        .await(5, TimeUnit.SECONDS)
    while (bluetoothManager.adapter.enable().not()) {
        sleep(100)
    }
    sleep(3_000)
}

internal fun connect(): RxBluetoothGatt {
    lateinit var gatt: RxBluetoothGatt
    bluetoothManager
        .rxScan(logger = LogcatLogger)
        .filter { it.device.name == DEVICE_NAME }
        .firstOrError()
        .timeout(10, TimeUnit.SECONDS)
        .flatMap { it.device.connectRxGatt(logger = LogcatLogger) }
        .doOnSuccess { gatt = it }
        .flatMapMaybe { it.whenConnectionIsReady() }
        .toSingle()
        .ignoreElement()
        .blockingAwait()
    return gatt
}

internal object LogcatLogger : Logger {
    override fun v(tag: String, message: String, throwable: Throwable?) {
        Log.v("LogcatLogger", message, throwable)
    }

    override fun d(tag: String, message: String, throwable: Throwable?) {
        Log.v("LogcatLogger", message, throwable)
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        Log.v("LogcatLogger", message, throwable)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.v("LogcatLogger", message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.v("LogcatLogger", message, throwable)
    }

    override fun wtf(tag: String, message: String, throwable: Throwable?) {
        Log.v("LogcatLogger", message, throwable)
    }

}

internal val CURRENT_TIME_CHARACTERISTIC: UUID = UUID
    .fromString("00002A2B-0000-1000-8000-00805F9B34FB")