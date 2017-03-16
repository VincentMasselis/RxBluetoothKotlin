package com.equisense.rxkotlinbleandroid.internal

import io.reactivex.*
import io.reactivex.disposables.CompositeDisposable

/**
 * Transform the source single to changes it's behavior depending to the filled completable to
 * match the maybe behavior.
 * If the completable emit onComplete, the transformed obs completes. If the completable emit
 * onError, the transformed obs emit error. If the completable does'nt emit any value, the
 * transformed obs act like a single.
 */
internal fun <T> Single<T>.takeUntilMaybe(completable: Completable): Maybe<T> =
        to { upStream ->
            Maybe.create<T> { downStream ->
                val disposables = CompositeDisposable()
                downStream.setDisposable(disposables)

                upStream.subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) })
                        .addTo(disposables)

                completable
                        .subscribe({ downStream.onComplete() }, { downStream.onError(it) })
                        .addTo(disposables)
            }
        }

/**
 * Transform the source flowable to changes it's behavior depending to the filled completable.
 * If the completable emit onComplete, the transformed obs completes. If the completable emit
 * onError, the transformed obs emit error. If the completable does'nt emit any value, the
 * transformed obs act like a flowable.
 */
internal fun <T> Flowable<T>.takeUntil(completable: Completable): Flowable<T> =
        compose { upStream ->
            Flowable.create<T>({ downStream ->
                val disposables = CompositeDisposable()
                downStream.setDisposable(disposables)

                upStream.subscribe({
                    if (disposables.isDisposed.not() && downStream.isCancelled.not()) downStream.onNext(it)
                }, {
                    if (disposables.isDisposed.not() && downStream.isCancelled.not()) downStream.onError(it)
                }, {
                    if (disposables.isDisposed.not() && downStream.isCancelled.not()) downStream.onComplete()
                })
                        .addTo(disposables)

                completable
                        .subscribe({
                            if (disposables.isDisposed.not() && downStream.isCancelled.not()) downStream.onComplete()
                        }, {
                            if (disposables.isDisposed.not() && downStream.isCancelled.not()) downStream.onError(it)
                        })
                        .addTo(disposables)

            }, BackpressureStrategy.BUFFER)
        }