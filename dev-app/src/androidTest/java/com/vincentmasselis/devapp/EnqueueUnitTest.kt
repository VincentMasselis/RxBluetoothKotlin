package com.vincentmasselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.vincentmasselis.rxbluetoothkotlin.connectRxGatt
import com.vincentmasselis.rxbluetoothkotlin.findCharacteristic
import com.vincentmasselis.rxbluetoothkotlin.rxScan
import com.vincentmasselis.rxbluetoothkotlin.whenConnectionIsReady
import io.reactivex.rxkotlin.Maybes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*


@RunWith(AndroidJUnit4::class)
class EnqueueUnitTest {

    @get:Rule val mainActivityRule = ActivityTestRule(TestActivity::class.java, true, false)

    private object Logger : com.vincentmasselis.rxbluetoothkotlin.Logger {
        override fun v(tag: String, message: String, throwable: Throwable?) {
            Log.v(TAG, message, throwable)
        }

        override fun d(tag: String, message: String, throwable: Throwable?) {
            Log.v(TAG, message, throwable)
        }

        override fun i(tag: String, message: String, throwable: Throwable?) {
            Log.v(TAG, message, throwable)
        }

        override fun w(tag: String, message: String, throwable: Throwable?) {
            Log.v(TAG, message, throwable)
        }

        override fun e(tag: String, message: String, throwable: Throwable?) {
            Log.v(TAG, message, throwable)
        }

        override fun wtf(tag: String, message: String, throwable: Throwable?) {
            Log.v(TAG, message, throwable)
        }

    }

    @Test
    fun queueTest() {
        val activity = mainActivityRule.launchActivity(null)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .filter { it.device.address == "E9:98:86:03:D5:9F" } // Write the mac address for your own device here
            .firstElement()
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .flatMap { gatt ->
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!)
                            .doOnSuccess { Log.v(TAG, "battery1 : ${it[0].toInt()}") },
                        gatt.read(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!)
                            .doOnSuccess { Log.v(TAG, "battery2 : ${it[0].toInt()}") }
                    )
            }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .blockingGet()
        mainActivityRule.finishActivity()
    }

    companion object {
        private val BATTERY_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        const val TAG = "EnqueueUnitTest"
    }
}
