package com.equisense.rxkotlinbleandroid.scan

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.support.v4.content.ContextCompat
import com.equisense.rxkotlinbleandroid.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import no.nordicsemi.android.support.v18.scanner.*
import java.util.concurrent.TimeUnit


fun BluetoothManager.scan(context: Context, scanArgs: Pair<List<ScanFilter>, ScanSettings>? = null, flushEvery: Pair<Long, TimeUnit>? = null): Flowable<ScanResult> =
        Completable
                .defer {
                    if (adapter == null)
                        return@defer Completable.error(DeviceDoesNotSupportBluetooth())
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        return@defer Completable.error(NeedLocationPermission())
                    else if (adapter.isEnabled.not())
                        return@defer Completable.error(BluetoothIsTurnedOff())

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val locationManager = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER).not() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER).not())
                            return@defer Completable.error(LocationServiceDisabled())
                    }
                    Completable.complete()
                }
                .andThen(
                        Flowable.create<ScanResult>({ emitter ->
                            val callback = object : ScanCallback() {
                                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                                override fun onScanResult(callbackType: Int, result: ScanResult) {//TODO Handle callbackType
                                    emitter.onNext(result)
                                }

                                override fun onScanFailed(errorCode: Int) {
                                    emitter.onError(ScanFailedException(errorCode))
                                }
                            }
                            val scanner = BluetoothLeScannerCompat.getScanner()

                            if (scanArgs != null) scanner.startScan(scanArgs.first, scanArgs.second, callback)
                            else scanner.startScan(callback)

                            var flushEveryDisp: Disposable? = null
                            flushEvery?.run {
                                flushEveryDisp = Observable
                                        .interval(flushEvery.first, flushEvery.second)
                                        .subscribe { scanner.flushPendingScanResults(callback) }
                            }

                            emitter.setCancellable {
                                flushEveryDisp?.dispose()
                                scanner.stopScan(callback)
                            }
                        }, BackpressureStrategy.BUFFER)
                )
                .subscribeOn(AndroidSchedulers.mainThread())