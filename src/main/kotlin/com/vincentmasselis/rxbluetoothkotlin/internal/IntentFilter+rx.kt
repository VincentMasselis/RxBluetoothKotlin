package com.vincentmasselis.rxbluetoothkotlin.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.reactivex.Observable

fun IntentFilter.toObservable(context: Context): Observable<Pair<Context, Intent>> =
    Observable.create({ downStream ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(currentContext: Context, intent: Intent) {
                downStream.onNext(currentContext to intent)
            }
        }
        context.registerReceiver(receiver, this)
        downStream.setCancellable { context.unregisterReceiver(receiver) }
    })