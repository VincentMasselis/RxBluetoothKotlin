package com.vincentmasselis.rxbluetoothkotlin.internal

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.vincentmasselis.rxbluetoothkotlin.BluetoothTimeout
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected
import com.vincentmasselis.rxbluetoothkotlin.livingConnection
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// ------------------------------ I/O Queue

private val BluetoothGatt.semaphore: Semaphore by SynchronizedFieldProperty { Semaphore(1) }
private val BluetoothGatt.executor: ExecutorService by SynchronizedFieldProperty { Executors.newSingleThreadExecutor() }

internal fun <R> BluetoothGatt.enqueue(exception: (device: BluetoothDevice, status: Int) -> DeviceDisconnected, sourceSingle: () -> Single<R>) = Maybe.create<R> { downstream ->
    val downStreamDisp = CompositeDisposable()

    downstream.setDisposable(downStreamDisp)

    executor.submit {
        semaphore.acquire()

        if (downStreamDisp.isDisposed) {
            semaphore.release()
            return@submit
        }

        Observable
            .defer { livingConnection(exception) }
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapSingle {
                sourceSingle()
                    // Value is set to 1 minute because some devices take a long time to detect
                    // when the connection is lost. For example, we saw up to 16 seconds on a
                    // Nexus 4 between the last call to write and the moment when the system
                    // fallback the disconnection.
                    .timeout(1, TimeUnit.MINUTES, Single.error(BluetoothTimeout()))
            }
            .onErrorResumeNext(Function {
                if (it is ExceptedDisconnectionException)
                    Observable.empty()
                else
                    Observable.error(it)
            })
            .firstElement()
            .doAfterTerminate { semaphore.release() }
            .subscribe({ downstream.onSuccess(it) },
                { downstream.tryOnError(it) },
                { downstream.onComplete() })

    }
}