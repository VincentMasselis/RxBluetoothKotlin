package com.masselis.rxbluetoothkotlin

import android.Manifest.permission.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Build.VERSION.SDK_INT
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
    sleep(100)
    bluetoothManager.adapter.enable()
    bluetoothStateObs.filter { it == BluetoothAdapter.STATE_ON }.firstOrError().test()
        .await(5, TimeUnit.SECONDS)
    while (bluetoothManager.adapter.isEnabled.not()) {
        sleep(100)
    }
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

/** this one is the heart rate characteristic emulated by nRF Connect when advertising with the app */
internal val NOTIFY_CHAR: UUID = UUID
    .fromString("00002a37-0000-1000-8000-00805f9b34fb")

internal val READ_CHAR: UUID = UUID
    .fromString("00002A2B-0000-1000-8000-00805F9B34FB")

internal val PERMISSIONS =
    when (SDK_INT) {
        in Build.VERSION_CODES.M until Build.VERSION_CODES.Q ->
            arrayOf(ACCESS_COARSE_LOCATION)
        in Build.VERSION_CODES.Q until Build.VERSION_CODES.S ->
            arrayOf(ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION)
        in Build.VERSION_CODES.S..Int.MAX_VALUE ->
            arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
        else ->
            emptyArray()
    }