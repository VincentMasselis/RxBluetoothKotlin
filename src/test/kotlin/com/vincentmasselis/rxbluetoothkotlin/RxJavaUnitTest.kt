package com.vincentmasselis.rxbluetoothkotlin

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RxJavaUnitTest {

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
                .run {
                    assertFailsWith<ExceptedException> {
                        try {
                            blockingIterable().last()
                        } catch (ex: RuntimeException) {
                            throw ex.cause!!
                        }
                    }
                }
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
                .run { assertEquals(3, blockingIterable().count()) }
    }

    @Test
    fun doOnSubscribe() {
        println()
        println("-------- doOnSubscribe")

        val subject = PublishSubject.create<Int>()

        Single.create<Int> { downStream ->
            downStream.setDisposable(subject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) }))
            subject.onNext(5)
        }
                .run { assertEquals(5, blockingGet()) }
    }

    @Test
    fun doOnSubscribePlusFail() {
        println()
        println("-------- doOnSubscribePlusFail")

        val subject = PublishSubject.create<Int>()

        Single
                .create<Int> { downStream ->
                    downStream.setDisposable(subject.firstOrError().subscribe({ downStream.onSuccess(it) }, { downStream.onError(it) }))
                    subject.onError(ExceptedException())
                }
                .run {
                    assertFailsWith<ExceptedException> {
                        try {
                            blockingGet()
                        } catch (ex: RuntimeException) {
                            throw ex.cause!!
                        }
                    }
                }
    }
}