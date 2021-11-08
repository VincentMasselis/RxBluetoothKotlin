package com.masselis.rxbluetoothkotlin

public data class PHY(val connectionPHY: ConnectionPHY, val status: Status)

/**
 * [transmitter] PHY in use. One of [android.bluetooth.BluetoothDevice.PHY_LE_1M], [android.bluetooth.BluetoothDevice.PHY_LE_2M], and
 * [android.bluetooth.BluetoothDevice.PHY_LE_CODED].
 *
 * [receiver] PHY in use. One of [android.bluetooth.BluetoothDevice.PHY_LE_1M], [android.bluetooth.BluetoothDevice.PHY_LE_2M], and
 * [android.bluetooth.BluetoothDevice.PHY_LE_CODED]
 */
public data class ConnectionPHY(val transmitter: Int, val receiver: Int)