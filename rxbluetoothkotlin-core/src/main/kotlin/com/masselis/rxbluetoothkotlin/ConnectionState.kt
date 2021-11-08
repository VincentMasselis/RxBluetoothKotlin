package com.masselis.rxbluetoothkotlin

import android.bluetooth.BluetoothProfile

/**
 * The [state] matches [BluetoothProfile.STATE_DISCONNECTED],
 * [BluetoothProfile.STATE_CONNECTING], [BluetoothProfile.STATE_CONNECTED]
 * or [BluetoothProfile.STATE_DISCONNECTING]
 *
 * @see android.bluetooth.BluetoothGattCallback.onConnectionStateChange
 */
public data class ConnectionState(val state: Int, val status: Status)