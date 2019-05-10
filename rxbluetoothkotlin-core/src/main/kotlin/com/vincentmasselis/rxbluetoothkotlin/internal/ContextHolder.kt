package com.vincentmasselis.rxbluetoothkotlin.internal

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
internal object ContextHolder {

    lateinit var context: Context

    fun install(context: Context) = context
        .takeIf { this::context.isInitialized.not() }
        ?.let { ContextHolder.context = it }
}