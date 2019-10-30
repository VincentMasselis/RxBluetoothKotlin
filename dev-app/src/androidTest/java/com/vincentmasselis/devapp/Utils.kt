package com.vincentmasselis.devapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import com.vincentmasselis.rxbluetoothkotlin.internal.toObservable
import io.reactivex.Observable
import java.util.*

fun bluetoothPreconditions(activity: TestActivity) {
    activity.setMessage("Disabling BLE")
    val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter.disable()
    Thread.sleep(1000)
    bluetoothManager.adapter.enable()
    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        .toObservable(activity)
        .map { (_, intent) -> intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) }
        .startWith(Observable.fromCallable {
            if (bluetoothManager.adapter.isEnabled) BluetoothAdapter.STATE_ON
            else BluetoothAdapter.STATE_OFF
        })
        .filter { it == BluetoothAdapter.STATE_ON }
        .blockingFirst()


    activity.setMessage("Enabling BLE")

    Thread.sleep(1000)
}

val BATTERY_CHARACTERISTIC: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

const val DEVICE_MAC = "EE:72:0C:43:49:B6"