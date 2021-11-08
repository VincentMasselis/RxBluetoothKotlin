package com.masselis.rxbluetoothkotlin

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.kotlin.Maybes
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
internal class EnqueueTest {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    private lateinit var gatt: RxBluetoothGatt

    @Before
    fun setup() {
        rebootBluetooth()
        gatt = connect()
    }

    /** Check the queue is correctly working, if not, [CannotInitialize] exceptions are fired */
    @Test
    fun enqueueingTest() {
        sleep(600)
        gatt.discoverServices()
            .flatMap { _ ->
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(CURRENT_TIME_CHARACTERISTIC)!!)
                            .doOnSuccess { LogcatLogger.v(TAG, "currentTime1 : ${it[0].toInt()}") },
                        gatt.enableNotification(
                            gatt.source.findCharacteristic(
                                HEART_RATE_CHARACTERISTIC
                            )!!
                        ).doOnSuccess { LogcatLogger.v(TAG, "Enabled notification") },
                        gatt.readRemoteRssi()
                            .doOnSuccess { LogcatLogger.v(TAG, "rssi $it") }
                    )
                    .map { gatt }
            }
            .doOnComplete { throw IllegalStateException("Should not complete here") }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertValueCount(1)
            .values()
            .first()
            .disconnect()
            .subscribe()
    }

    /** Disconnects while there is an I/O in the queue */
    @Test
    fun queueDisconnectionTest() {
        sleep(600)
        gatt.discoverServices()
            // Small delay to force the sensor switch into 500ms connection interval
            .delay(7, TimeUnit.SECONDS)
            .flatMap {
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(CURRENT_TIME_CHARACTERISTIC)!!)
                            .doOnSubscribe {
                                Completable.timer(50, TimeUnit.MILLISECONDS)
                                    .subscribe { gatt.disconnect().subscribe() }
                            } // Manual disconnection while reading
                            .doOnSubscribe { LogcatLogger.v(TAG, "currentTime1 subscription") }
                            .doOnComplete { LogcatLogger.v(TAG, "currentTime1 completed") },
                        gatt.enableNotification(
                            gatt.source.findCharacteristic(HEART_RATE_CHARACTERISTIC)!!
                        )
                            .doOnSubscribe {
                                LogcatLogger.v(
                                    TAG,
                                    "Enabled notification subscription"
                                )
                            }
                            .doOnComplete { LogcatLogger.v(TAG, "Enabled notification completed") },
                        gatt.readRemoteRssi()
                            .doOnSubscribe { LogcatLogger.v(TAG, "RSSI subscription") }
                            .doOnComplete { LogcatLogger.v(TAG, "RSSI completed") }
                    )
            }
            .doOnSuccess { throw IllegalStateException("Should not succeed here, It should complete with because of the gatt.disconnect() call") }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    @Test
    fun queueCallRightAfterDisconnectionTest() {
        gatt.disconnect().test()
        sleep(600)
        gatt.discoverServices()
            .doOnSuccess { throw IllegalStateException("Should not succeed here, It should complete with because of the gatt.disconnect() call") }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertComplete()
    }

    @Test
    fun checkQueueWaitingElementsDisconnectionTest() {
        sleep(600)
        gatt.discoverServices()
            .flatMap {
                Maybes
                    .zip(
                        gatt.read(gatt.source.findCharacteristic(CURRENT_TIME_CHARACTERISTIC)!!)
                            .map { 1 }.switchIfEmpty(Maybe.just(0)),
                        gatt.readRemoteRssi().map { 1 }.switchIfEmpty(Maybe.just(0)),
                        gatt.read(gatt.source.findCharacteristic(CURRENT_TIME_CHARACTERISTIC)!!)
                            .map { 1 }.switchIfEmpty(Maybe.just(0))
                    )
                    .doOnSubscribe { LogcatLogger.e(TAG, "I/O Subscription") }
                    .doOnDispose { LogcatLogger.e(TAG, "I/O Dispose") }
                    .doOnEvent { t1, t2 -> LogcatLogger.e(TAG, "I/O t1 $t1, t2 $t2") }
                    .doOnSubscribe {
                        Completable.timer(10, TimeUnit.MILLISECONDS)
                            .subscribe { gatt.disconnect().subscribe() }
                    }
            }
            .doOnError { LogcatLogger.e(TAG, "Failed, reason :$it") }
            .timeout(20L, TimeUnit.SECONDS)
            .test()
            .await()
            .assertValue(Triple(0, 0, 0))
    }

    companion object {
        const val TAG = "EnqueueUnitTest"
    }
}
