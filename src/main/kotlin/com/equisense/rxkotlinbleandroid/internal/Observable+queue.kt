package com.equisense.rxkotlinbleandroid.internal

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal fun <R> EnqueueSingle(semaphore: Semaphore, disconnectedCompl: Completable, sourceSingle: () -> Single<R>): Maybe<R> =
        Maybe.create { downstream ->
            var downstreamTerminated = false

            downstream.setCancellable { downstreamTerminated = true }

            Thread {
                semaphore.acquire()

                if (downstreamTerminated) {
                    semaphore.release()
                    return@Thread
                }

                val disconnectDisp: Disposable?
                var sourceDisp: Disposable? = null

                disconnectDisp = disconnectedCompl
                        .doOnEvent { sourceDisp?.dispose() }
                        .subscribe({
                            if (downstreamTerminated.not())
                                downstream.onComplete()
                            semaphore.release()
                        }, {
                            if (downstreamTerminated.not())
                                downstream.onError(it)
                            semaphore.release()
                        })

                sourceDisp = sourceSingle()
                        // Value is set to 1 minute because some devices take a long time to detect
                        // when the connection is lost. For example, we saw up to 16 seconds on a
                        // Nexus 4 between the last call to write and the moment when the system
                        // forward the disconnection.
                        .timeout(1, TimeUnit.MINUTES)
                        .doOnEvent { _, _ -> disconnectDisp?.dispose() }
                        .subscribe { value, throwable ->
                            if (downstreamTerminated.not())
                                if (throwable == null) downstream.onSuccess(value)
                                else downstream.onError(throwable)
                            semaphore.release()
                        }
            }.start()
        }