package com.masselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.masselis.rxbluetoothkotlin.rxScan
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .subscribe {
                applicationContext
            }
            .disposeOnState(ActivityState.DESTROY, this)
    }
}
