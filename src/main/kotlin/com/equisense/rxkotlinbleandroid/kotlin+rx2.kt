package com.equisense.rxkotlinbleandroid

import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

class Optional<out T>(val value: T?)

fun <T> T.toSingle(): Single<T> = Single.just(this)

fun <T> T.toMaybe(): Maybe<T> = Maybe.just(this)

fun <T> T.toObservable(): Observable<T> = Observable.just(this)

fun <T> T.optToObservable(): Observable<Optional<T>> = Observable.just(Optional(this))

fun <T> T?.toOptional() = Optional(this)

fun <T> Observable<T>.optional(): Observable<Optional<T>> = map { it.toOptional() }

fun <T> Observable<Optional<T>>.filterNotNull(): Observable<T> = filter { it.value != null }.map { it.value }

fun <T> Flowable<Optional<T>>.filterNotNull(): Flowable<T> = filter { it.value != null }.map { it.value }