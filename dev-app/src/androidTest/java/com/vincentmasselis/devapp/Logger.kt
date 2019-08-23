package com.vincentmasselis.devapp

import android.util.Log

object Logger : com.vincentmasselis.rxbluetoothkotlin.Logger {
    override fun v(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
    }

    override fun d(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
    }

    override fun wtf(tag: String, message: String, throwable: Throwable?) {
        Log.v(tag, message, throwable)
    }

}