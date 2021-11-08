package com.masselis.rxbluetoothkotlin

import android.Manifest
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

    private lateinit var gatt: RxBluetoothGatt

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*PERMISSIONS)

    @Before
    fun setup() {
        rebootBluetooth()
        gatt = bluetoothManager
            .rxScan(logger = LogcatLogger)
            .filter { it.device.name == DEVICE_NAME }
            .firstOrError()
            .timeout(10, TimeUnit.SECONDS)
            .flatMap { it.device.connectRxGatt(logger = LogcatLogger) }
            .blockingGet()
    }

    /** Disconnects right after a connection is done */
    @Test
    fun disconnectionImmediatelyTest() {
        gatt.disconnect().subscribe()
        gatt.whenConnectionIsReady()
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap {
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects 10 millis after a connection */
    @Test
    fun disconnection10msTest() {
        Completable.timer(10, TimeUnit.MILLISECONDS)
            .subscribe { gatt.disconnect().subscribe() }
        gatt.whenConnectionIsReady()
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap {
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects 100 millis after a connection */
    @Test
    fun disconnection100msTest() {
        Completable.timer(100, TimeUnit.MILLISECONDS)
            .subscribe { gatt.disconnect().subscribe() }
        gatt.whenConnectionIsReady()
            .flatMap {
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
            }
            .flatMap { gatt.listenDisconnection().toMaybe<RxBluetoothGatt>() }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects 5 second after a connection */
    @Test
    fun disconnection5sTest() {
        Completable.timer(5, TimeUnit.SECONDS)
            .subscribe { gatt.disconnect().subscribe() }
        gatt.whenConnectionIsReady()
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap {
                gatt.discoverServices()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Subscribing to fetch services") }
            }
            .flatMapCompletable {
                gatt.listenDisconnection()
                    .doOnSubscribe { LogcatLogger.v(TAG, "Listening for disconnection") }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    /** Disconnects when reading the services */
    @Test
    fun disconnectionDiscoverServicesTest() {
        gatt.whenConnectionIsReady()
            .delay(600, TimeUnit.MILLISECONDS)
            .flatMap {
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