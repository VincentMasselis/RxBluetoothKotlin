package com.masselis.rxbluetoothkotlin

public interface Logger {
    public fun v(tag: String, message: String, throwable: Throwable? = null)
    public fun d(tag: String, message: String, throwable: Throwable? = null)
    public fun i(tag: String, message: String, throwable: Throwable? = null)
    public fun w(tag: String, message: String, throwable: Throwable? = null)
    public fun e(tag: String, message: String, throwable: Throwable? = null)
    public fun wtf(tag: String, message: String, throwable: Throwable? = null)
}