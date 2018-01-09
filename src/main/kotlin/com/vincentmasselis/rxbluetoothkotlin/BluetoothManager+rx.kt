package com.vincentmasselis.rxbluetoothkotlin

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O_MR1
import android.support.v4.content.ContextCompat
import com.vincentmasselis.rxbluetoothkotlin.internal.toObservable
import io.reactivex.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import no.nordicsemi.android.support.v18.scanner.*
import java.util.concurrent.TimeUnit


fun BluetoothManager.rxScan(
    context: Context,
    scanArgs: Pair<List<ScanFilter>, ScanSettings>? = null,
    flushEvery: Pair<Long, TimeUnit>? = null,
    logger: Logger? = null
): Flowable<ScanResult> =
    Completable
        .defer {
            when {
                adapter == null -> {
                    logger?.v(TAG, "rxScan(), error : DeviceDoesNotSupportBluetooth()")
                    return@defer Completable.error(DeviceDoesNotSupportBluetooth())
                }
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                    logger?.v(TAG, "rxScan(), error : NeedLocationPermission()")
                    return@defer Completable.error(NeedLocationPermission())
                }
                adapter.isEnabled.not() -> {
                    logger?.v(TAG, "rxScan(), error : BluetoothIsTurnedOff()")
                    return@defer Completable.error(BluetoothIsTurnedOff())
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    val locationManager = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER).not() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER).not()) {
                        logger?.v(TAG, "rxScan(), error : LocationServiceDisabled()")
                        return@defer Completable.error(LocationServiceDisabled())
                    }
                }
            }

            Completable.complete()
        }
        .andThen(
            Flowable.create<ScanResult>({ downStream ->
                val callback = object : ScanCallback() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    override fun onScanResult(callbackType: Int, result: ScanResult) {//TODO Handle callbackType
                        downStream.onNext(result)
                    }

                    override fun onScanFailed(errorCode: Int) {
                        val error = ScanFailedException(errorCode)
                        logger?.v(TAG, "rxScan(), error ScanFailedException : $error")
                        downStream.tryOnError(error)
                    }
                }

                val disposables = CompositeDisposable()

                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    .toObservable(context)
                    .subscribe { (_, intent) ->
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

                @SuppressLint("NewApi")
                if (SDK_INT >= O_MR1) {
                    Single
                        .fromCallable {
                            //Since I'm using a scanner compat from nordic, the callback that the system hold is not mine but an instance crated by the nordic lib.
                            val realCallback = scanner.javaClass.superclass.getDeclaredField("mCallbacks")
                                .apply { isAccessible = true }
                                .get(scanner)
                                .let { it as? Map<*, *> }
                                ?.get(callback)
                            val systemScanner = adapter.bluetoothLeScanner
                            systemScanner.javaClass.getDeclaredField("mLeScanClients")
                                .apply { isAccessible = true }
                                .get(systemScanner)
                                .let { it as? Map<*, *> }
                                ?.get(realCallback)
                                ?.let { bluetoothLeScanner ->
                                    bluetoothLeScanner.javaClass.getDeclaredField("mScannerId")
                                        .apply { isAccessible = true }
                                        .get(bluetoothLeScanner)
                                }
                                ?.run { this as? Int }
                                ?.let { mScannerId ->
                                    return@fromCallable mScannerId
                                }
                        }
                        .subscribeOn(Schedulers.computation())
                        .subscribe({ mScannerId ->
                            logger?.v(TAG, "rxScan(), system mScannerId for this scan : $mScannerId")
                            if (mScannerId == -2) //Value fetched from BluetoothLeScanner$BleScanCallbackWrapper.mScannerId. If you check the API27 sources, you will see a -2 in this field when the exception SCAN_FAILED_SCANNING_TOO_FREQUENTLY is fired.
                                downStream.tryOnError(ScanFailedException(6))
                        }, {
                            logger?.w(
                                TAG,
                                "rxScan() is unable to compute system's mScannerId for this scan, it has no effect on the execution of rxScan() but it can leads to bugs from the Android SDK API 27. More information here : https://issuetracker.google.com/issues/71736547",
                                it
                            )
                        })
                }

                downStream.setCancellable {
                    disposables.dispose()
                    logger?.v(TAG, "rxScan(), stopScan()")
                    scanner.stopScan(callback)
                }
            }, BackpressureStrategy.BUFFER)
        )
        .subscribeOn(AndroidSchedulers.mainThread())