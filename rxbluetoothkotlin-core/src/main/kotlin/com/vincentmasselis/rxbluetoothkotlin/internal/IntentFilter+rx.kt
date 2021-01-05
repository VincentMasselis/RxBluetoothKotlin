package com.vincentmasselis.rxbluetoothkotlin.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.reactivex.rxjava3.core.Observable

fun IntentFilter.toObservable(context: Context): Observable<Pair<Context, Intent>> = Observable.create { downStream ->
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(currentContext: Context, intent: Intent) {
            downStream.onNext(currentContext to intent)
        }
    }
    context.registerReceiver(receiver, this)
    downStream.setCancellable {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Calling unregisterReceiver with a receiver already unregistered throws IllegalArgumentException. Everything is fine, fired exception doesn't need to be forwarded to the downstream so I catch it and I do nothing else.
        }
    }
}