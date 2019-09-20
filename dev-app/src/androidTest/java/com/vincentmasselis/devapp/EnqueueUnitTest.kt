package com.vincentmasselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.vincentmasselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxuikotlin.postForUI
import io.reactivex.Maybe
import io.reactivex.rxkotlin.Maybes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
class EnqueueUnitTest {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN)

    @Rule
    @JvmField
    val mainActivityRule = ActivityTestRule(TestActivity::class.java, true, false)

    /** Check the queue is correctly working, if not, [CannotInitialize] exceptions are fired */
    @Test
    fun enqueueingTest() {
        val activity = mainActivityRule.launchActivity(null)

        bluetoothPreconditions(activity)

        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == DEVICE_MAC } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
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
            .test()
            .await()
            .assertValueCount(1)
            .values()
            .first()
            .disconnect().subscribe()

        mainActivityRule.finishActivity()
    }

    /** Disconnects while there is an I/O in the queue */
    @Test
    fun queueDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)

        bluetoothPreconditions(activity)

        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == DEVICE_MAC } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .delay(7, TimeUnit.SECONDS) // Small delay to force the sensor switch into 500ms connection interval
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
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    @Test
    fun queueCallRightAfterDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)

        bluetoothPreconditions(activity)

        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == DEVICE_MAC } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { it.disconnect().subscribe() }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .doOnSuccess { throw IllegalStateException("Should not succeed here, It should complete with because of the gatt.disconnect() call") }
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .timeout(20L, TimeUnit.SECONDS)
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    @Test
    fun queueCallDelayAfterDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)

        bluetoothPreconditions(activity)

        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == DEVICE_MAC } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { it.disconnect().subscribe() }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .doOnSuccess { throw IllegalStateException("Should not succeed here, It should complete with because of the gatt.disconnect() call") }
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .timeout(20L, TimeUnit.SECONDS)
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    @Test
    fun checkQueueWaitingElementsDisconnectionTest() {
        val activity = mainActivityRule.launchActivity(null)

        bluetoothPreconditions(activity)

        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.address == DEVICE_MAC } // Write the mac address for your own device here
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingleElement { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().map { gatt } }
            .doOnSuccess { activity.setMessage("Services discovered") }
            .flatMap { gatt ->
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!).map { 1 }.switchIfEmpty(Maybe.just(0)),
                        gatt.readRemoteRssi().map { 1 }.switchIfEmpty(Maybe.just(0)),
                        gatt.read(gatt.source.findCharacteristic(BATTERY_CHARACTERISTIC)!!).map { 1 }.switchIfEmpty(Maybe.just(0))
                    )
                    .doOnSubscribe { Logger.e(TAG, "I/O Subscription") }
                    .doOnDispose { Logger.e(TAG, "I/O Dispose") }
                    .doOnEvent { t1, t2 -> Logger.e(TAG, "I/O t1 $t1, t2 $t2") }
                    .doOnSubscribe { activity.postForUI(10L to TimeUnit.MILLISECONDS) { gatt.disconnect().subscribe() } }
            }
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .timeout(20L, TimeUnit.SECONDS)
            .test()
            .await()
            .assertValue(Triple(0, 0, 0))

        mainActivityRule.finishActivity()
    }

    companion object {
        const val TAG = "EnqueueUnitTest"
    }
}
