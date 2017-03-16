package com.vincentmasselis.rxbluetoothkotlin.internal

import java.util.*

class GattConst {
    companion object {
        val CLIENT_CHARACTERISTIC_CONFIG: UUID by lazy { UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") }
    }
}