package com.masselis.rxbluetoothkotlin

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.getSystemService
import com.masselis.rxbluetoothkotlin.internal.appContext
import com.masselis.rxbluetoothkotlin.internal.hasPermissions
import com.masselis.rxbluetoothkotlin.internal.observe
import io.reactivex.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import no.nordicsemi.android.support.v18.scanner.*
import java.util.concurrent.TimeUnit

private const val TAG = "BluetoothManager+rx"

/**
 * Reactive way to get [ScanResult] while scanning.
 *
 * @param scanArgs If [scanArgs] param is not null, the method [android.bluetooth.le.BluetoothLeScanner.startScan] with 3 params will be called instead of the one with 1 param.
 *
 * @param flushEvery If [flushEvery] is not null, [android.bluetooth.le.BluetoothLeScanner.flushPendingScanResults] will be called repeatedly with the specified delay until the
 * downstream is disposed.
 *
 * Warning ! It never completes ! It stops his scan only when the downstream is disposed.
 * For example you can use a [Flowable.takeUntil] + [Flowable.timer] operator to stop scanning after a delay.
 * Alternatively you can use an [Flowable.firstElement] if you have set a [scanArgs] or you can simply call [io.reactivex.disposables.Disposable.dispose] when your job is done.
 *
 * @return
 * onNext with [ScanResult]
 *
 * onComplete is never called. The downstream has to dispose to stop the scan.
 *
 * onError if an error has occurred. It can emit [DeviceDoesNotSupportBluetooth], [NeedLocationPermission], [BluetoothIsTurnedOff], [LocationServiceDisabled] and
 * [ScanFailedException]
 *
 * @see android.bluetooth.le.ScanResult
 * @see android.bluetooth.le.ScanFilter
 * @see android.bluetooth.le.ScanSettings
 * @see [android.bluetooth.le.BluetoothLeScanner.startScan]
 * @see [android.bluetooth.le.BluetoothLeScanner.flushPendingScanResults]
 */
public fun BluetoothManager.rxScan(
    scanArgs: Pair<List<ScanFilter>, ScanSettings>? = null,
    flushEvery: Pair<Long, TimeUnit>? = null,
    logger: Logger? = null
): Flowable<ScanResult> =
    Completable
        .defer {
            when {
                adapter == null || appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE).not() -> {
                    logger?.v(TAG, "rxScan(), error : DeviceDoesNotSupportBluetooth()")
                    return@defer Completable.error(DeviceDoesNotSupportBluetooth())
                }
                hasPermissions().not() -> {
                    logger?.v(TAG, "rxScan(), error : NeedLocationPermission()")
                    return@defer Completable.error(NeedLocationPermission())
                }
                adapter.isEnabled.not() -> {
                    logger?.v(TAG, "rxScan(), error : BluetoothIsTurnedOff()")
                    return@defer Completable.error(BluetoothIsTurnedOff())
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        appContext.getSystemService<LocationManager>()!!.let { locationManager ->
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                .not() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER).not()
                        } -> {
                    logger?.v(TAG, "rxScan(), error : LocationServiceDisabled()")
                    return@defer Completable.error(LocationServiceDisabled())
                }
            }

            Completable.complete()
        }
        .andThen(
            Flowable.create<ScanResult>({ downStream ->

                // Used to prevent memory leaks
                var safeDownStream = downStream as FlowableEmitter<ScanResult>?

                val callback = object : ScanCallback() {

                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    override fun onScanResult(callbackType: Int, result: ScanResult) {//TODO Handle callbackType
                        safeDownStream?.onNext(result)
                    }

                    override fun onScanFailed(errorCode: Int) {
                        val error = ScanFailedException(errorCode)
                        logger?.v(TAG, "rxScan(), error ScanFailedException : $error")
                        safeDownStream?.tryOnError(error)
                    }
                }

                val disposables = CompositeDisposable()

                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    .observe()
                    .subscribe { intent ->
                        when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> downStream.tryOnError(BluetoothIsTurnedOff())
                            else -> {
                            }
                        }
                    }
                    .let { disposables.add(it) }

                val scanner = BluetoothLeScannerCompat.getScanner()

                flushEvery?.run {
                    Observable
                        .interval(flushEvery.first, flushEvery.second, AndroidSchedulers.mainThread())
                        .subscribe { scanner.flushPendingScanResults(callback) }
                        .let { disposables.add(it) }
                }

                if (scanArgs != null) {
                    logger?.v(TAG, "rxScan(), startScan() with scanArgs.first : ${scanArgs.first} and scanArgs.second : ${scanArgs.second}")
                    scanner.startScan(scanArgs.first, scanArgs.second, callback)
                } else {
                    logger?.v(TAG, "rxScan(), startScan() without scanArgs")
                    scanner.startScan(callback)
                }

                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                    // If you look at BluetoothLeScanner.java#389 (SDK 27 sources only) you'll see an early return which causes a silent exception because the method
                    // `postCallbackError` is not called. Because of this I need to check if the scan callback is really added to the system callback registry right after the scan
                    // started. If not, an exception occurred and I consider this is because the `SCAN_FAILED_SCANNING_TOO_FREQUENTLY` error was returned since only this error is
                    // silent.
                    Single
                        .fromCallable {
                            //Since I'm using a scanner compat from nordic, the callback that the system hold is not mine but an instance crated by the nordic lib.
                            val nativeCallback = scanner.javaClass.superclass!!.superclass!!
                                .getDeclaredField("wrappers")
                                .apply { isAccessible = true }
                                .get(scanner)!!
                                .let { (it as Map<*, *>)[callback]!! }
                                .let { wrapper -> wrapper.javaClass.getDeclaredField("nativeCallback").apply { isAccessible = true }.get(wrapper) }
                            // Let's check if the system holds my callback
                            val systemScanner = adapter.bluetoothLeScanner
                            systemScanner.javaClass
                                .getDeclaredField("mLeScanClients")
                                .apply { isAccessible = true }
                                .get(systemScanner)!!
                                .let { (it as Map<*, *>).contains(nativeCallback) }
                        }
                        .subscribeOn(Schedulers.computation())
                        // Legitimate errors could be fired by the system but theses one are send to the main thread by posting on a Handler. To avoid reading `mLeScanClients`
                        // too soon before the error is returned, I also post on the main thread the force the execution of my code AFTER the error callback is called.
                        // Take a look at BluetoothLeScanner.java#544
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .subscribe({ isNativeCallbackAdded ->
                            if (isNativeCallbackAdded.not())
                                downStream.tryOnError(ScanFailedException(6))
                        }, {
                            logger?.w(
                                TAG,
                                "rxScan() is unable to compute system's mScannerId for this scan, it has no effect on the execution of rxScan() but it can leads to bugs in SDK 27. More information here : https://issuetracker.google.com/issues/71736547",
                                it
                            )
                        })
                        .let { disposables.add(it) }
                }

                downStream.setCancellable {
                    safeDownStream = null
                    disposables.dispose()
                    Handler(Looper.getMainLooper()).post {
                        logger?.v(TAG, "rxScan(), stopScan()")
                        try {
                            scanner.stopScan(callback)
                        } catch (e: IllegalStateException) {
                            // IllegalStateException is fired is stop scan is called while the bluetooth is already turned off
                        }
                    }
                }
            }, BackpressureStrategy.BUFFER)
        )
        .subscribeOn(AndroidSchedulers.mainThread())