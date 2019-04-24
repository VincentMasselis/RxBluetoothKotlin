package com.vincentmasselis.rxbluetoothkotlin.internal

import android.bluetooth.BluetoothDevice

internal class ExceptedDisconnectionException : Throwable()

internal data class DisconnectionException(val device: BluetoothDevice, val status: Int) : Throwable()