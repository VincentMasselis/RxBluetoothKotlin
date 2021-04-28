package com.masselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.masselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxuikotlin.postForUI
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DisconnectionTests {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN)

    @Rule
    @JvmField
    val mainActivityRule = ActivityTestRule(TestActivity::class.java, true, false)

    /** Disconnects right after a connection is done */
    @Test
    fun disconnectionImmediatelyTest() {
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.name == DEVICE_NAME }
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingle { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt ->
                gatt.disconnect().subscribe()
                gatt.whenConnectionIsReady().map { gatt }
            }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().doOnSubscribe { Logger.v(TAG, "Subscribing to fetch services") }.map { gatt } }
            .timeout(20, TimeUnit.SECONDS)
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    /** Disconnects 10 millis after a connection */
    @Test
    fun disconnection10msTest() {
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.name == DEVICE_NAME }
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingle { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt ->
                activity.postForUI(10L to TimeUnit.MILLISECONDS) { gatt.disconnect().subscribe() }
                gatt.whenConnectionIsReady().map { gatt }
            }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().doOnSubscribe { Logger.v(TAG, "Subscribing to fetch services") }.map { gatt } }
            .timeout(20, TimeUnit.SECONDS)
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    /** Disconnects 100 millis after a connection */
    @Test
    fun disconnection100msTest() {
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.name == DEVICE_NAME }
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingle { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt ->
                activity.postForUI(100L to TimeUnit.MILLISECONDS) { gatt.disconnect().subscribe() }
                gatt.whenConnectionIsReady().map { gatt }
            }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .flatMap { gatt -> gatt.discoverServices().doOnSubscribe { Logger.v(TAG, "Subscribing to fetch services") }.map { gatt } }
            .flatMap { it.listenDisconnection().toMaybe<RxBluetoothGatt>() }
            .timeout(20, TimeUnit.SECONDS)
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    /** Disconnects 5 second after a connection */
    @Test
    fun disconnection5sTest() {
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.name == DEVICE_NAME }
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingle { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt ->
                activity.postForUI(5L to TimeUnit.SECONDS) { gatt.disconnect().subscribe() }
                gatt.whenConnectionIsReady().map { gatt }
            }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt -> gatt.discoverServices().doOnSubscribe { Logger.v(TAG, "Subscribing to fetch services") }.map { gatt } }
            .flatMapCompletable { it.listenDisconnection().doOnSubscribe { Logger.v(TAG, "Listening for disconnection") } }
            .timeout(20, TimeUnit.SECONDS)
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    /** Disconnects when reading the services */
    @Test
    fun disconnectionDiscoverServicesTest() {
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan()
            .doOnSubscribe { activity.setMessage("Please wakeup your device") }
            .filter { it.device.name == DEVICE_NAME }
            .firstElement()
            .doOnSuccess { activity.setMessage("Connecting") }
            .flatMapSingle { it.device.connectRxGatt(logger = Logger) }
            .flatMap { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSuccess { activity.setMessage("Discovering services") }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt ->
                gatt.discoverServices().doOnSubscribe { Logger.v(TAG, "Subscribing to fetch services"); gatt.disconnect().subscribe() }.map { gatt }
            }
            .timeout(20, TimeUnit.SECONDS)
            .doOnError { Logger.e(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertComplete()

        mainActivityRule.finishActivity()
    }

    companion object {
        const val TAG = "DisconnectionTests"
    }
}