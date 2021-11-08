package com.masselis.rxbluetoothkotlin

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.reactivex.rxjava3.core.Completable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class DisconnectionTests {

    private lateinit var device: BluetoothDevice

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    @Before
    fun setup() {
        rebootBluetooth()
        device = bluetoothManager
            .rxScan()
            .filter { it.device.name == DEVICE_NAME }
            .firstOrError()
            .timeout(10, TimeUnit.SECONDS)
            .blockingGet()
            .device
    }

    /** Disconnects right after a connection is done */
    @Test
    fun disconnectionImmediatelyTest() {
        device.connectRxGatt(logger = LogcatLogger)
            .flatMapMaybe { gatt ->
                gatt.disconnect().subscribe()
                gatt.whenConnectionIsReady().map { gatt }
            }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt ->
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
                    .map { gatt }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects 10 millis after a connection */
    @Test
    fun disconnection10msTest() {
        device.connectRxGatt(logger = LogcatLogger)
            .flatMapMaybe { gatt ->
                Completable.timer(10, TimeUnit.MILLISECONDS)
                    .subscribe { gatt.disconnect().subscribe() }
                gatt.whenConnectionIsReady().map { gatt }
            }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt ->
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
                    .map { gatt }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects 100 millis after a connection */
    @Test
    fun disconnection100msTest() {
        device.connectRxGatt(logger = LogcatLogger)
            .flatMapMaybe { gatt ->
                Completable.timer(100, TimeUnit.MILLISECONDS)
                    .subscribe { gatt.disconnect().subscribe() }
                gatt.whenConnectionIsReady().map { gatt }
            }
            .flatMap { gatt ->
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
                    .map { gatt }
            }
            .flatMap { it.listenDisconnection().toMaybe<RxBluetoothGatt>() }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects 5 second after a connection */
    @Test
    fun disconnection5sTest() {
        device.connectRxGatt(logger = LogcatLogger)
            .flatMapMaybe { gatt ->
                Completable.timer(5, TimeUnit.SECONDS)
                    .subscribe { gatt.disconnect().subscribe() }
                gatt.whenConnectionIsReady().map { gatt }
            }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt ->
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
                    .map { gatt }
            }
            .flatMapCompletable {
                it.listenDisconnection()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Listening for disconnection") }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.MILLISECONDS)
            .assertComplete()
    }

    /** Disconnects when reading the services */
    @Test
    fun disconnectionDiscoverServicesTest() {
        device.connectRxGatt(logger = LogcatLogger)
            .flatMapMaybe { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap { gatt ->
                gatt.discoverServices()
                    .doOnSubscribe {
                        LogcatLogger.v(TAG, "Subscribing to fetch services")
                        gatt.disconnect().subscribe()
                    }
                    .map { gatt }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    companion object {
        const val TAG = "DisconnectionTests"
    }
}