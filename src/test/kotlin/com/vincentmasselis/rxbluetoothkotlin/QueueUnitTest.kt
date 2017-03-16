package com.vincentmasselis.rxbluetoothkotlin

class QueueUnitTest {

    /*private class ExceptedException : Throwable()

    @Test
    fun success() {
        println()
        println("-------- success")

        val executor = Semaphore(1)

        EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} Processing")
            Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} Processing done") }
                .run { assertEquals(0L, blockingGet()) }

        Thread.sleep(5)
        assertEquals(false, executor.hasQueuedThreads())
    }

    @Test
    fun error() {
        println()
        println("-------- error")

        val executor = Semaphore(1)

        EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} Processing")
            Single.timer(100, TimeUnit.MILLISECONDS)
                    .flatMap { Single.error<Long>(ExceptedException()) }
        }
                .doOnSuccess { println("${System.currentTimeMillis()} Emitting $it after queue") }
                .doOnError { println("${System.currentTimeMillis()} Excepted exception $it") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} Processing done") }
                .run {
                    assertFailsWith<ExceptedException> {
                        try {
                            blockingGet()
                        } catch (ex: RuntimeException) {
                            throw ex.cause!!
                        }
                    }
                }

        Thread.sleep(5)
        assertEquals(false, executor.hasQueuedThreads())
    }

    @Test
    fun two() {
        println()
        println("-------- two")

        val executor = Semaphore(1)

        var resourceAvailableAt: Long? = null

        val obs1 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #1 Processing")
            resourceAvailableAt = System.currentTimeMillis() + 100
            Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #1 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #1 Processing done") }

        val obs2 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #2 Processing")
            if (System.currentTimeMillis() <= resourceAvailableAt!!) throw IllegalStateException("#2 is processing before the resource is released")
            else Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #2 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #2 Processing done") }

        Single.zip(obs1, obs2, BiFunction<Long, Long, Pair<Long, Long>> { result1, result2 -> result1 to result2 })
                .subscribeOn(Schedulers.newThread())
                .run { assertEquals(0L to 0L, blockingGet()) }


        Thread.sleep(5)
        assertEquals(false, executor.hasQueuedThreads())
    }

    @Test
    fun oneFailsInOneSucceeded() {
        println()
        println("-------- oneFailsInOneSucceeded")

        val executor = Semaphore(1)

        var resourceAvailableAt: Long? = null

        val obs1 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #1 Processing")
            resourceAvailableAt = System.currentTimeMillis() + 50
            Single.timer(50, TimeUnit.MILLISECONDS).flatMap { Single.error<Long>(ExceptedException()) }
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #1 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #1 Queue processing done") }

        val obs2 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #2 Processing")
            if (System.currentTimeMillis() <= resourceAvailableAt!!) throw IllegalStateException("#2 is processing before the resource is released")
            else Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #2 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #2 Processing done") }

        obs1.subscribeOn(Schedulers.newThread())
                .subscribe({ throw IllegalStateException("obs1 should fails") },
                        { assertFailsWith<ExceptedException> { throw it } })

        Thread.sleep(20)

        obs2.subscribeOn(Schedulers.newThread())
                .subscribe({ assertEquals(0L, it) },
                        { throw IllegalStateException("obs2 should not fails") })

        Thread.sleep(220)
        assertEquals(false, executor.hasQueuedThreads())
    }

    @Test
    fun oneFailsOutOneSucceeded() {
        println()
        println("-------- oneFailsOutOneSucceeded")

        val executor = Semaphore(1)

        var resourceAvailableAt: Long? = null

        val obs1 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #1 Processing")
            resourceAvailableAt = System.currentTimeMillis() + 100
            Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .ambWith(Single.timer(50, TimeUnit.MILLISECONDS).flatMap { Single.error<Long>(ExceptedException()) })
                .doOnSuccess { println("${System.currentTimeMillis()} #1 Emitting$it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #1 Processing done") }

        val obs2 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #2 Processing")
            if (System.currentTimeMillis() <= resourceAvailableAt!!) throw IllegalStateException("#2 is processing before the resource is released")
            else Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #2 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #2 Processing done") }

        obs1.subscribeOn(Schedulers.newThread())
                .subscribe({ throw IllegalStateException("obs1 should fails") },
                        { assertFailsWith<ExceptedException> { throw it } })

        Thread.sleep(20)

        obs2.subscribeOn(Schedulers.newThread())
                .subscribe({ assertEquals(0L, it) },
                        { throw IllegalStateException("obs2 should not fails") })

        Thread.sleep(220)
        assertEquals(false, executor.hasQueuedThreads())
    }

    @Test
    fun oneDisposeOneSucceeded() {
        println()
        println("-------- oneDisposeOneSucceeded")

        val executor = Semaphore(1)

        var resourceAvailableAt: Long? = null

        val obs1 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #1 Processing")
            resourceAvailableAt = System.currentTimeMillis() + 100
            Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #1 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #1 Processing done") }
                .doOnDispose { println("${System.currentTimeMillis()} #1 Disposing") }

        val obs2 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #2 Processing")
            if (System.currentTimeMillis() <= resourceAvailableAt!!) throw IllegalStateException("#2 is processing before the resource is released")
            else Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #2 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #2 Processing done") }

        val disposable = obs1.subscribeOn(Schedulers.newThread())
                .subscribe({ assertEquals(0L, it) },
                        { throw IllegalStateException("obs1 should not fails") })

        Thread.sleep(20)

        obs2.subscribeOn(Schedulers.newThread())
                .subscribe({ assertEquals(0L, it) },
                        { throw IllegalStateException("obs2 should not fails") })

        Thread.sleep(50)

        disposable.dispose()

        Thread.sleep(250)
        assertEquals(true, disposable.isDisposed)
        assertEquals(false, executor.hasQueuedThreads())
    }

    @Test
    fun oneTimeoutOneSucceeded() {
        println()
        println("-------- oneTimeoutOneSucceeded")

        val executor = Semaphore(1)

        var resourceAvailableAt: Long? = null

        val obs1 = EnqueueSingle(executor, 100, TimeUnit.MILLISECONDS) {
            println("${System.currentTimeMillis()} #1 Processing")
            resourceAvailableAt = System.currentTimeMillis() + 100
            Single.timer(200, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #1 Emitting$it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #1 Processing done") }

        val obs2 = EnqueueSingle(executor) {
            println("${System.currentTimeMillis()} #2 Processing")
            if (System.currentTimeMillis() <= resourceAvailableAt!!) throw IllegalStateException("#2 is processing before the resource is released")
            else Single.timer(100, TimeUnit.MILLISECONDS)
        }
                .doOnSuccess { println("${System.currentTimeMillis()} #2 Emitting $it after queue") }
                .doOnEvent { _, _ -> println("${System.currentTimeMillis()} #2 Processing done") }

        obs1.subscribeOn(Schedulers.newThread())
                .subscribe({ throw IllegalStateException("obs1 should fails") },
                        { assertFailsWith<TimeoutException> { throw it } })

        Thread.sleep(20)

        obs2.subscribeOn(Schedulers.newThread())
                .subscribe({ assertEquals(0L, it) },
                        { throw IllegalStateException("obs2 should not fails") })

        Thread.sleep(220)
        assertEquals(false, executor.hasQueuedThreads())
    }*/
}