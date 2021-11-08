package com.masselis.rxbluetoothkotlin

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

/** Theses tests were run and verified on a stock Nexus 5X on Oreo 8.1 */
@RunWith(AndroidJUnit4::class)
internal class API27FrequentScanTests {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(ACCESS_FINE_LOCATION)

    @Before
    fun setup() {
        check(Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1)
        rebootBluetooth()
    }

    /** Starts and stop 10 scans one by one and check that the returned exception is the one computed by BluetoothManager+rx associated to the silent SCAN_FAILED_SCANNING_TOO_FREQUENTLY */
    @Test
    fun tooMuchStartedAndStoppedScan() {
        Observable.interval(1, TimeUnit.SECONDS)
            .takeUntil { it == 9L }
            .subscribe {
                bluetoothManager.rxScan(logger = LogcatLogger)
                    .takeUntil(Flowable.timer(900, TimeUnit.MILLISECONDS)).test()
            }
        sleep(10_000)
        bluetoothManager.rxScan(logger = LogcatLogger)
            .doOnError { LogcatLogger.d(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertError { it is ScanFailedException && it.status == 6 }

        // Required to reset the amount of simultaneous scans
        sleep(30_000)
    }

    /** Creates 64 simultaneous scans and check that the returned exception is NOT the one computed by BluetoothManager+rx associated to the silent SCAN_FAILED_SCANNING_TOO_FREQUENTLY */
    @Test
    fun tooMuchSimultaneousScan() {
        Observable.interval(100, TimeUnit.MILLISECONDS)
            .takeUntil { it == 39L }
            .subscribe {
                bluetoothManager.rxScan(logger = LogcatLogger)
                    .takeUntil(Flowable.timer(20, TimeUnit.SECONDS)).test()
            }
        sleep(10_000)
        bluetoothManager.rxScan(logger = LogcatLogger)
            .doOnError { LogcatLogger.d(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertError { it is ScanFailedException && it.status != 6 }

        // Required to reset the amount of the simultaneous scans
        sleep(30_000)
    }

    companion object {
        const val TAG = "API27FrequentScanTests"
    }
}