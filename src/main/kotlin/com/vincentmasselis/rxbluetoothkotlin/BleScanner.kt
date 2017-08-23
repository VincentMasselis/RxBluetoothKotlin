package com.vincentmasselis.rxbluetoothkotlin

import android.Manifest
import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.support.v4.content.ContextCompat
import com.vincentmasselis.rxbluetoothkotlin.internal.toObservable
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import no.nordicsemi.android.support.v18.scanner.*
import java.util.concurrent.TimeUnit


fun BluetoothManager.rxScan(context: Context, scanArgs: Pair<List<ScanFilter>, ScanSettings>? = null, flushEvery: Pair<Long, TimeUnit>? = null): Flowable<ScanResult> =
        Completable
                .defer {
                    when {
                        adapter == null -> return@defer Completable.error(DeviceDoesNotSupportBluetooth())
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED -> return@defer Completable.error(NeedLocationPermission())
                        adapter.isEnabled.not() -> return@defer Completable.error(BluetoothIsTurnedOff())
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                            val locationManager = (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER).not() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER).not())
                                return@defer Completable.error(LocationServiceDisabled())
                        }
                    }

                    Completable.complete()
                }
                .andThen(
                        Flowable.create<ScanResult>({ downStream ->
                            val callback = object : ScanCallback() {
                                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                                override fun onScanResult(callbackType: Int, result: ScanResult) {//TODO Handle callbackType
                                    if (downStream.isCancelled.not()) downStream.onNext(result)
                                }

                                override fun onScanFailed(errorCode: Int) {
                                    if (downStream.isCancelled.not()) downStream.onError(ScanFailedException(errorCode))
                                }
                            }
                            val scanner = BluetoothLeScannerCompat.getScanner()


                            val disposables = CompositeDisposable()

                            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                                    .toObservable(context)
                                    .subscribe { (_, intent) ->
                                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                                        when (state) {
                                            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> downStream.onError(BluetoothIsTurnedOff())
                                            else -> {
                                            }
                                        }
                                    }
                                    .let { disposables.add(it) }

                            flushEvery?.run {
                                Observable
                                        .interval(flushEvery.first, flushEvery.second, AndroidSchedulers.mainThread())
                                        .subscribe { scanner.flushPendingScanResults(callback) }
                                        .let { disposables.add(it) }
                            }

                            if (scanArgs != null) scanner.startScan(scanArgs.first, scanArgs.second, callback)
                            else scanner.startScan(callback)

                            downStream.setCancellable {
                                disposables.dispose()
                                scanner.stopScan(callback)
                            }
                        }, BackpressureStrategy.BUFFER)
                )
                .subscribeOn(AndroidSchedulers.mainThread())