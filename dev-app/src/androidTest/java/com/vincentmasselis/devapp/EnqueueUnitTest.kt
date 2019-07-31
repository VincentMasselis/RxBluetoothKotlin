package com.vincentmasselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.vincentmasselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxuikotlin.postForUI
import io.reactivex.rxkotlin.Maybes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
class EnqueueUnitTest {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN)

    @Rule
    @JvmField
    val mainActivityRule = ActivityTestRule(TestActivity::class.java, true, false)

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

    /** Check the queue is correctly working, if not, [CannotInitialize] exceptions are fired */
    @Test
    fun enqueueingTest() {
        val activity = mainActivityRule.launchActivity(null)
        val gatt = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == "E9:98:86:03:D5:9F" } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .doOnSuccess { activity.setMessage("Running tests") }
            .flatMap { gatt ->
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!)
                            .doOnSuccess { Logger.v(TAG, "battery1 : ${it[0].toInt()}") },
                        gatt.enableNotification(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!)
                            .doOnSuccess { Logger.v(TAG, "Enabled notification") },
                        gatt.readRemoteRssi()
                            .doOnSuccess { Logger.v(TAG, "rssi $it") }
                    )
                    .map { gatt }
            }
            .doOnComplete { throw IllegalStateException("Should not complete here") }
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .blockingGet()
        gatt.disconnect().subscribe()
        mainActivityRule.finishActivity()
    }

    /** Disconnects while there is an I/O in the queue */
    @Test
    fun queueDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)
        val gatt = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == "E9:98:86:03:D5:9F" } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .delay(7, TimeUnit.SECONDS) // Small delay to force the sensor switch into 500ms connection interval
            .doOnSuccess { activity.setMessage("Discovering services") }
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .doOnSuccess { activity.setMessage("Running tests") }
            .flatMap { gatt ->
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!)
                            .doOnSubscribe { activity.postForUI(50L to TimeUnit.MILLISECONDS) { gatt.disconnect().subscribe() } } // Manual disconnection while reading
                            .doOnSubscribe { Logger.v(TAG, "battery1 subscription") }
                            .doOnComplete { Logger.v(TAG, "battery1 completed") },
                        gatt.enableNotification(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!)
                            .doOnSubscribe { Logger.v(TAG, "Enabled notification subscription") }
                            .doOnComplete { Logger.v(TAG, "Enabled notification completed") },
                        gatt.readRemoteRssi()
                            .doOnSubscribe { Logger.v(TAG, "RSSI subscription") }
                            .doOnComplete { Logger.v(TAG, "RSSI completed") }
                    )
            }
            .doOnSuccess { throw IllegalStateException("Should not succeed here, It should complete with because of the gatt.disconnect() call") }
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .blockingGet()
        check(gatt == null)
        mainActivityRule.finishActivity()
    }

    companion object {
        private val BATTERY_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        const val TAG = "EnqueueUnitTest"
    }
}
