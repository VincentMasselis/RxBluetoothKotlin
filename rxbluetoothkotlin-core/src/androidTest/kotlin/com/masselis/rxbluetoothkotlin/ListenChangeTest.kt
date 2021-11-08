package com.masselis.rxbluetoothkotlin

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.reactivex.rxjava3.core.Completable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
internal class ListenChangeTest {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(*PERMISSIONS)

    private lateinit var gatt: RxBluetoothGatt

    @Before
    fun setup() {
        rebootBluetooth()
        gatt = connect()
        gatt.discoverServices().blockingGet()
    }

    /** While listening characteristic, plug in the device to update the battery percent */
    @Test
    fun listenChangeTest() {
        gatt.enableNotification(gatt.source.findCharacteristic(NOTIFY_CHAR)!!)
            .flatMapPublisher {
                gatt.listenChanges(gatt.source.findCharacteristic(NOTIFY_CHAR)!!)
            }
            .map { ByteBuffer.wrap(it).short }
            .doOnNext { Log.v(TAG, "Current HR : $it") }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .firstOrError()
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertValueCount(1)
    }

    @Test
    fun listenChangeDisconnectionTest() {
        Completable.timer(1, TimeUnit.SECONDS)
            .subscribe { gatt.disconnect().subscribe() }
        gatt.enableNotification(gatt.source.findCharacteristic(NOTIFY_CHAR)!!)
            .flatMapPublisher {
                gatt.listenChanges(gatt.source.findCharacteristic(NOTIFY_CHAR)!!)
            }
            .map { ByteBuffer.wrap(it).short }
            .doOnNext { Log.v(TAG, "Current HR : $it") }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(20, TimeUnit.SECONDS)
            .assertNoValues()
    }

    /** While listening characteristic, turn off the device (reset or removing the battery should be enough) */
    @Test
    fun listenChangeUnexpectedDisconnectionTest() {
        gatt.enableNotification(gatt.source.findCharacteristic(NOTIFY_CHAR)!!)
            .flatMapPublisher {
                gatt.listenChanges(gatt.source.findCharacteristic(NOTIFY_CHAR)!!)
            }
            .map { ByteBuffer.wrap(it).short }
            .doOnNext { Log.v(TAG, "Current HR : $it") }
            .doOnError { Log.v(TAG, "Failed, reason :$it") }
            .test()
            .awaitDone(1, TimeUnit.MINUTES)
    }

    companion object {
        const val TAG = "ListenChangeUnitTest"
    }
}
