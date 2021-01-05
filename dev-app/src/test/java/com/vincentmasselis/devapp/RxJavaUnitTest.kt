package com.vincentmasselis.devapp

import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.UnicastSubject
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.fail

class RxJavaUnitTest {

    private class DisconnectException : Throwable()

    @Test
    fun livingConnectionNormalTest() {
        println()
        println("-------- livingConnectionNormalTest")

        Observable.just(Unit)
            .mergeWith(
                Observable.timer(
                    150,
                    TimeUnit.MILLISECONDS
                ).flatMap { Observable.error<Unit>(DisconnectException()) })
            .doOnEach {
                println("${System.currentTimeMillis()} living connection value : ${it.value}")
                println("${System.currentTimeMillis()} living connection error : ${it.error}")
                println("${System.currentTimeMillis()} living connection isComplete : ${it.isOnComplete}")
            }
            .doOnDispose {
                println("${System.currentTimeMillis()} living connection disposed")
            }
            .switchMapSingle {
                Single.timer(50, TimeUnit.MILLISECONDS)
            }
            .onErrorResumeNext {
                if (it is DisconnectException)
                    Observable.empty()
                else
                    Observable.error(it)
            }
            .firstElement()
            .doOnEvent { t1, t2 ->
                println("${System.currentTimeMillis()} I/O value : $t1")
                println("${System.currentTimeMillis()} I/O error : $t2")
                println("${System.currentTimeMillis()} I/O isComplete : ${t1 == null && t2 == null}")
            }
            .doOnDispose {
                println("I/O disposed")
            }
            .test()
            .awaitCount(1)
            .assertValue(0L)
    }

    @Test
    fun livingConnectionDisconnectedTest() {
        println()
        println("-------- livingConnectionDisconnectedTest")

        Observable.just(Unit)
            .mergeWith(
                Observable.timer(
                    50,
                    TimeUnit.MILLISECONDS
                ).flatMap { Observable.error<Unit>(DisconnectException()) })
            .doOnEach {
                println("${System.currentTimeMillis()} living connection value : ${it.value}")
                println("${System.currentTimeMillis()} living connection error : ${it.error}")
                println("${System.currentTimeMillis()} living connection isComplete : ${it.isOnComplete}")
            }
            .doOnDispose {
                println("${System.currentTimeMillis()} living connection disposed")
            }
            .switchMapSingle {
                Single.timer(150, TimeUnit.MILLISECONDS)
            }
            .onErrorResumeNext {
                if (it is DisconnectException)
                    Observable.empty()
                else
                    Observable.error(it)
            }
            .firstElement()
            .doOnEvent { t1, t2 ->
                println("${System.currentTimeMillis()} I/O value : $t1")
                println("${System.currentTimeMillis()} I/O error : $t2")
                println("${System.currentTimeMillis()} I/O isComplete : ${t1 == null && t2 == null}")
            }
            .doOnDispose {
                println("I/O disposed")
            }
            .test()
            .await()
            .assertComplete()
    }

    @Test
    fun livingConnectionErrorTest() {
        println()
        println("-------- livingConnectionErrorTest")

        Observable.just(Unit)
            .mergeWith(Observable.timer(50, TimeUnit.MILLISECONDS).flatMap { Observable.error(Throwable()) })
            .doOnEach {
                println("${System.currentTimeMillis()} living connection value : ${it.value}")
                println("${System.currentTimeMillis()} living connection error : ${it.error}")
                println("${System.currentTimeMillis()} living connection isComplete : ${it.isOnComplete}")
            }
            .doOnDispose {
                println("${System.currentTimeMillis()} living connection disposed")
            }
            .switchMapSingle {
                Single.timer(150, TimeUnit.MILLISECONDS)
            }
            .onErrorResumeNext {
                if (it is DisconnectException)
                    Observable.empty()
                else
                    Observable.error(it)
            }
            .firstElement()
            .doOnEvent { t1, t2 ->
                println("${System.currentTimeMillis()} I/O value : $t1")
                println("${System.currentTimeMillis()} I/O error : $t2")
                println("${System.currentTimeMillis()} I/O isComplete : ${t1 == null && t2 == null}")
            }
            .doOnDispose {
                println("I/O disposed")
            }
            .test()
            .await()
            .assertFailure(Throwable::class.java)
    }

    @Test
    fun takeUntilCompleteTest() {
        println()
        println("-------- takeUntilCompleteTest")

        Observable.interval(50, TimeUnit.MILLISECONDS)
            .takeUntil(Completable.timer(175, TimeUnit.MILLISECONDS).toObservable<Long>())
            .doOnEach {
                println("value : ${it.value}")
                println("error : ${it.error}")
                println("complete : ${it.isOnComplete}")
            }
            .test()
            .await()
            .assertValueAt(2, 2)
    }

    private class ExceptedException : Throwable()

    @Test
    fun mergeWithErrorTest() {
        println()
        println("-------- mergeWithErrorTest")

        Flowable.interval(50, TimeUnit.MILLISECONDS)
            .takeUntil(Completable.timer(175, TimeUnit.MILLISECONDS).andThen(Flowable.error<Long>(ExceptedException())))
            .doOnEach {
                println("value : ${it.value}")
                println("error : ${it.error}")
                println("complete : ${it.isOnComplete}")
            }
            .test()
            .await()
            .assertFailure(ExceptedException::class.java, 0, 1, 2)
    }

    @Test
    fun mergeWithCompleteTest() {
        println()
        println("-------- mergeWithCompleteTest")

        Flowable.interval(50, TimeUnit.MILLISECONDS)
            .takeUntil(Completable.timer(175, TimeUnit.MILLISECONDS).andThen(Flowable.just(0L)))
            .doOnEach {
                println("value : ${it.value}")
                println("error : ${it.error}")
                println("complete : ${it.isOnComplete}")
            }

            .test()
            .await()
            .assertValueCount(3)
    }

    @Test
    fun doOnSubscribe() {
        println()
        println("-------- doOnSubscribe")

        val subject = PublishSubject.create<Int>()

        Single.create<Int> { downStream ->
            downStream.setDisposable(
                subject.firstOrError().subscribe(
                    { downStream.onSuccess(it) },
                    { downStream.onError(it) })
            )
            subject.onNext(5)
        }
            .test()
            .await()
            .assertValue(5)
    }

    @Test
    fun doOnSubscribePlusFail() {
        println()
        println("-------- doOnSubscribePlusFail")

        val subject = PublishSubject.create<Int>()

        Single
            .create<Int> { downStream ->
                downStream.setDisposable(
                    subject.firstOrError().subscribe(
                        { downStream.onSuccess(it) },
                        { downStream.onError(it) })
                )
                subject.onError(ExceptedException())
            }
            .test()
            .await()
            .assertFailure(ExceptedException::class.java)
    }

    @Test
    fun errorInError() {
        println()
        println("-------- doOnSubscribePlusFail")

        Flowable
            .create<Long>({ downStream ->

                Flowable.interval(50, TimeUnit.MILLISECONDS).takeUntil(Flowable.timer(200, TimeUnit.MILLISECONDS))
                    .subscribe(
                        { downStream.onNext(it) },
                        {},
                        { downStream.onComplete() }
                    )

                Single
                    .fromCallable {
                        Thread.sleep(80)
                        throw ExceptedException()
                    }
                    .subscribeOn(Schedulers.computation())
                    .subscribe(
                        { fail() },
                        { println("Excepted error correctly catch") }
                    )
            }, BackpressureStrategy.BUFFER)
            .test()
            .await()
            .assertValues(0, 1, 2)
    }

    @Test
    fun takeUntilOnSubscription() {
        println()
        println("-------- takeUntilOnSubscription")

        val obs = Observable.interval(0, 50, TimeUnit.MILLISECONDS)

        obs
            .takeUntil(obs.filter { it == 0L })
            .switchMap {
                when (it) {
                    0L -> Observable.empty()
                    1L -> Observable.just(it)
                    2L -> Observable.empty()
                    else -> throw IllegalStateException()
                }
            }
            .test()
            .await()
            .assertValueCount(0)
    }

    @Test
    fun takeUntilDelayed() {
        println()
        println("-------- takeUntilOnSubscription")

        val obs = Observable.interval(0, 50, TimeUnit.MILLISECONDS)

        obs
            .takeUntil(obs.filter { it == 2L })
            .switchMap {
                when (it) {
                    0L -> Observable.empty()
                    1L -> Observable.just(it)
                    2L -> Observable.empty()
                    else -> throw IllegalStateException()
                }
            }
            .test()
            .await()
            .assertValueCount(1)
    }

    @Test
    fun concatMapQueueWaitingTest() {
        println()
        println("-------- concatMapQueueWaitingTest")

        val subject = UnicastSubject.create<Maybe<Long>>()

        val disp = subject
            .concatMapMaybe { it }
            .subscribe { println("Queue: A maybe is consumed") }

        subject.onNext(Maybe.timer(100, TimeUnit.MILLISECONDS).doOnSubscribe { println("Timer 1 sub") }.doOnEvent { t1, t2 -> println("Timer 1 event $t1 $t2") })
        subject.onNext(Maybe.timer(100, TimeUnit.MILLISECONDS).doOnSubscribe { println("Timer 2 sub") }.doOnEvent { t1, t2 -> println("Timer 2 event $t1 $t2") })

        Thread.sleep(50)
        disp.dispose()

        Thread.sleep(300)

    }

    @Test
    fun concatMapQueueWaitingErrorTest() {
        println()
        println("-------- concatMapQueueWaitingTest")

        val subject = UnicastSubject.create<Maybe<Long>>()

        subject
            .concatMapMaybe { it }

        subject.onNext(Maybe.timer(100, TimeUnit.MILLISECONDS).doOnSubscribe { println("Timer 1 sub") }.doOnEvent { t1, t2 -> println("Timer 1 event $t1 $t2") })
        subject.onNext(Maybe.timer(100, TimeUnit.MILLISECONDS).doOnSubscribe { println("Timer 2 sub") }.doOnEvent { t1, t2 -> println("Timer 2 event $t1 $t2") })

        Thread.sleep(50)
        subject.onError(IllegalStateException())

        Thread.sleep(300)

    }

    @Test
    fun queueLikeTest() {
        println()
        println("-------- queueLikeTest")

        val subject = UnicastSubject.create<Single<Long>>()

        val queueDisp = subject
            .concatMapSingle { it }
            .subscribe(
                { println("Queue: A maybe is consumed") },
                { println("Queue: Error received") }
            )

        val state = BehaviorSubject.create<Long>()

        Observable.timer(10, TimeUnit.MILLISECONDS).subscribe { state.onNext(0L) }
        Observable.timer(100, TimeUnit.MILLISECONDS).subscribe { queueDisp.dispose(); state.onError(IllegalStateException()) }

        val operation = Single.timer(20, TimeUnit.MILLISECONDS)

        state
            .flatMapSingle {
                Single.create<Long> { downStream ->
                    val singleToEnqueue = operation
                        .doOnSuccess { downStream.onSuccess(it) }
                        .doOnError { downStream.tryOnError(it) }

                    subject.onNext(singleToEnqueue)
                }
            }
            .firstElement()
            .test()
            .await()
            .assertValue(0)
    }

    @Test
    fun queueLikeTestDisconnectionError() {
        println()
        println("-------- queueLikeTestDisconnectionError")

        val subject = UnicastSubject.create<Single<Long>>()

        val queueDisp = subject
            .concatMapSingle { it }
            .subscribe(
                { println("Queue: A maybe is consumed") },
                { println("Queue: Error received") }
            )

        val state = BehaviorSubject.create<Long>()

        Observable.timer(10, TimeUnit.MILLISECONDS).subscribe { state.onNext(0L) }
        Observable.timer(100, TimeUnit.MILLISECONDS).subscribe { queueDisp.dispose(); state.onError(IllegalStateException()) }

        val operation = Single.timer(150, TimeUnit.MILLISECONDS)

        state
            .flatMapSingle {
                Single.create<Long> { downStream ->
                    val singleToEnqueue = operation
                        .doOnSuccess { downStream.onSuccess(it) }
                        .doOnError { downStream.tryOnError(it) }

                    subject.onNext(singleToEnqueue)
                }
            }
            .firstElement()
            .test()
            .await()
            .assertFailure(IllegalStateException::class.java)
    }

    @Test
    fun queueLikeTestDoubleDisconnectionError() {
        println()
        println("-------- queueLikeTestDisconnectionError")


        val queue = UnicastSubject.create<Single<Long>>()

        val queueDisp = queue
            .concatMapSingle { it }
            .subscribe(
                { println("Queue: A maybe is consumed") },
                { println("Queue: Error received") }
            )

        val state = BehaviorSubject.create<Long>()

        Observable.timer(10, TimeUnit.MILLISECONDS).subscribe { state.onNext(0L) }
        Observable.timer(100, TimeUnit.MILLISECONDS).subscribe { queueDisp.dispose(); state.onError(IllegalStateException()) }

        val operation = Single.timer(55, TimeUnit.MILLISECONDS)

        val result1 = state
            .flatMapSingle {
                Single.create<Long> { downStream ->
                    val singleToEnqueue = operation
                        .doOnSuccess { downStream.onSuccess(it) }
                        .doOnError { downStream.tryOnError(it) }

                    queue.onNext(singleToEnqueue)
                }
            }
            .firstElement()

        val result2 = state
            .flatMapSingle {
                Single.create<Long> { downStream ->
                    val singleToEnqueue = operation
                        .doOnSuccess { downStream.onSuccess(it) }
                        .doOnError { downStream.tryOnError(it) }

                    queue.onNext(singleToEnqueue)
                }
            }
            .firstElement()

        result1.test().await().assertValue(0)
        result2.test().await().assertFailure(IllegalStateException::class.java)
    }
}