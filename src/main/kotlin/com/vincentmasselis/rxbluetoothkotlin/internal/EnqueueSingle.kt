package com.vincentmasselis.rxbluetoothkotlin.internal

import com.vincentmasselis.rxbluetoothkotlin.BluetoothTimeout
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal fun <R> EnqueueSingle(semaphore: Semaphore, disconnectedCompl: Completable, sourceSingle: () -> Single<R>) = Maybe.create<R> { downstream ->
    val downStreamDisp = CompositeDisposable()

    downstream.setDisposable(downStreamDisp)

    Thread {
        semaphore.acquire()

        if (downStreamDisp.isDisposed) {
            semaphore.release()
            return@Thread
        }

        Maybe
                .ambArray<R>(
                        disconnectedCompl.toMaybe(),
                        sourceSingle()
                                // Value is set to 1 minute because some devices take a long time to detect
                                // when the connection is lost. For example, we saw up to 16 seconds on a
                                // Nexus 4 between the last call to write and the moment when the system
                                // fallback the disconnection.
                                .timeout(1, TimeUnit.MINUTES, Single.error(BluetoothTimeout()))
                                .toMaybe()
                )
                .doAfterTerminate { semaphore.release() }
                .subscribe({ downstream.onSuccess(it) },
                        { downstream.tryOnError(it) },
                        { downstream.onComplete() })
    }.start()
}