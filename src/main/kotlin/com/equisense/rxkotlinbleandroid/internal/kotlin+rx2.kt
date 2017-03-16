package com.equisense.rxkotlinbleandroid.internal

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

fun Disposable.addTo(compositeSubscription: CompositeDisposable): Disposable {
    compositeSubscription.add(this)
    return this
}