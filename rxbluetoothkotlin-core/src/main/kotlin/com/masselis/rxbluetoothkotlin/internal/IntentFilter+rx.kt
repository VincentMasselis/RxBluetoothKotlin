package com.masselis.rxbluetoothkotlin.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.reactivex.rxjava3.core.Observable

internal fun IntentFilter.observe(): Observable<Intent> = Observable.create { downStream ->
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(currentContext: Context, intent: Intent) {
            downStream.onNext(intent)
        }
    }
    appContext.registerReceiver(receiver, this)
    downStream.setCancellable {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Calling unregisterReceiver with a receiver already unregistered throws IllegalArgumentException. Everything is fine, fired exception doesn't need to be forwarded to the downstream so I catch it and I do nothing else.
        }
    }
}