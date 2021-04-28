package com.masselis.devapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import com.masselis.rxbluetoothkotlin.internal.observe
import io.reactivex.rxjava3.core.Observable
import java.util.*

fun bluetoothPreconditions(activity: TestActivity) {
    activity.setMessage("Disabling BLE")
    val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter.disable()
    Thread.sleep(1000)
    bluetoothManager.adapter.enable()
    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
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
        .filter { it == BluetoothAdapter.STATE_ON }
        .blockingFirst()


    activity.setMessage("Enabling BLE")

    Thread.sleep(1000)
}

// Read capability but notify takes a long to emit
val CURRENT_TIME_CHARACTERISTIC: UUID = UUID.fromString("00002A2B-0000-1000-8000-00805F9B34FB")

// Unable to read but notify takes a few seconds to emit
val HEART_RATE_CHARACTERISTIC: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

const val DEVICE_NAME = "MOCK"

object AndroidLogger : com.masselis.rxbluetoothkotlin.Logger {
    override fun v(tag: String, message: String, throwable: Throwable?) {
        Log.v("AndroidLogger", message, throwable)
    }

    override fun d(tag: String, message: String, throwable: Throwable?) {
        Log.v("AndroidLogger", message, throwable)
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        Log.v("AndroidLogger", message, throwable)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.v("AndroidLogger", message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.v("AndroidLogger", message, throwable)
    }

    override fun wtf(tag: String, message: String, throwable: Throwable?) {
        Log.v("AndroidLogger", message, throwable)
    }

}