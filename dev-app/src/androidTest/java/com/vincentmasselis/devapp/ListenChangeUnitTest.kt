package com.vincentmasselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.vincentmasselis.rxbluetoothkotlin.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*


@RunWith(AndroidJUnit4::class)
class ListenChangeUnitTest {

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

    /** While listening characteristic, plug in the device to update the battery percent */
    @Test
    fun listenChangeTest() {
        val activity = mainActivityRule.launchActivity(null)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .filter { it.device.address == DEVICE_MAC }
            .firstElement()
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .flatMap { gatt -> gatt.enableNotification(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!).map { gatt } }
            .flatMapPublisher { gatt -> gatt.listenChanges(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!) }
            .doOnNext { Log.v(TAG, "battery1 : ${it[0].toInt()}") }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .blockingFirst()

        mainActivityRule.finishActivity()
    }

    @Test
    fun listenChangeDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .filter { it.device.address == DEVICE_MAC }
            .firstElement()
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { gatt ->
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    gatt.disconnect().subscribe()
                }, 1000)
            }
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .flatMap { gatt -> gatt.enableNotification(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!).map { gatt } }
            .flatMapPublisher { gatt -> gatt.listenChanges(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!) }
            .doOnNext { Log.v(TAG, "battery1 : ${it[0].toInt()}") }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .blockingSubscribe(
                { throw IllegalStateException() },
                { throw  it },
                { /* Excepted case */ })

        mainActivityRule.finishActivity()
    }

    /** While listening characteristic, turn off the device (reset or removing the battery should be enough) */
    @Test
    fun listenChangeUnexpectedDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .filter { it.device.address == DEVICE_MAC }
            .firstElement()
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .flatMap { gatt -> gatt.enableNotification(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!).map { gatt } }
            .flatMapPublisher { gatt -> gatt.listenChanges(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!) }
            .doOnNext { Log.v(TAG, "battery1 : ${it[0].toInt()}") }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .blockingSubscribe(
                {},
                { check(it is DeviceDisconnected.ListenChangesDeviceDisconnected) },
                { throw IllegalStateException() }
            )


        mainActivityRule.finishActivity()
    }

    companion object {
        private val BATTERY_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        const val TAG = "ListenChangeUnitTest"
    }
}
