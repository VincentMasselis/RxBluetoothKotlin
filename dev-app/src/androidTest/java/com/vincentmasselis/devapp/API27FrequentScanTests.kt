package com.vincentmasselis.devapp

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import com.vincentmasselis.rxbluetoothkotlin.ScanFailedException
import com.vincentmasselis.rxbluetoothkotlin.rxScan
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

/** Theses tests were run and verified on a stock Nexus 5X on Oreo 8.1 */
@RunWith(AndroidJUnit4::class)
class API27FrequentScanTests {

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.BLUETOOTH_ADMIN)

    @Rule
    @JvmField
    val mainActivityRule = ActivityTestRule(TestActivity::class.java, true, false)

    /** Starts and stop 10 scans one by one and check that the returned exception is the one computed by BluetoothManager+rx associated to the silent SCAN_FAILED_SCANNING_TOO_FREQUENTLY */
    @Test
    fun tooMuchStartedAndStoppedScan() {
        check(Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1)
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        activity.setMessage("Testing")
        val bluetoothManager = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
        Observable.interval(1, TimeUnit.SECONDS)
            .takeUntil { it == 9L }
            .subscribe { bluetoothManager.rxScan(logger = AndroidLogger).takeUntil(Flowable.timer(900, TimeUnit.MILLISECONDS)).test() }
        sleep(11000)
        bluetoothManager.rxScan(logger = AndroidLogger)
            .doOnError { Logger.d(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertError { it is ScanFailedException && it.status == 6 }

        activity.setMessage("Test finished please wait")

        // Required to reset the amount of simultaneous scans
        sleep(30000)

        mainActivityRule.finishActivity()
    }

    /** Creates 64 simultaneous scans and check that the returned exception is NOT the one computed by BluetoothManager+rx associated to the silent SCAN_FAILED_SCANNING_TOO_FREQUENTLY */
    @Test
    fun tooMuchSimultaneousScan() {
        check(Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1)
        val activity = mainActivityRule.launchActivity(null)
        bluetoothPreconditions(activity)
        activity.setMessage("Testing")
        val bluetoothManager = (activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
        Observable.interval(100, TimeUnit.MILLISECONDS)
            .takeUntil { it == 39L }
            .subscribe { bluetoothManager.rxScan(logger = AndroidLogger).takeUntil(Flowable.timer(20, TimeUnit.SECONDS)).test() }
        sleep(10000)
        bluetoothManager.rxScan(logger = AndroidLogger)
            .doOnError { Logger.d(TAG, "Failed, reason :$it") }
            .test()
            .await()
            .assertError { it is ScanFailedException && it.status != 6 }

        activity.setMessage("Test finished please wait")

        // Required to reset the amount of the simultaneous scans
        sleep(30000)

        mainActivityRule.finishActivity()
    }

    companion object {
        const val TAG = "API27FrequentScanTests"
    }
}