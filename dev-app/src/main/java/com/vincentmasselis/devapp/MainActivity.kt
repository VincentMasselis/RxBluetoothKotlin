package com.vincentmasselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.vincentmasselis.rxbluetoothkotlin.connectRxGatt
import com.vincentmasselis.rxbluetoothkotlin.rxScan
import com.vincentmasselis.rxbluetoothkotlin.whenConnectionIsReady
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .filter { it.device.address == "E9:98:86:03:D5:9F" }
            .firstElement()
            .flatMapSingleElement { it.device.connectRxGatt() }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .subscribe { }
            .disposeOnState(ActivityState.DESTROY, this)
    }

}
