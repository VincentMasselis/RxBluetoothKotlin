package com.masselis.rxbluetoothkotlin.internal

/**
 *  Returns the HEX representation of ByteArray data.
 */
internal fun ByteArray.toHexString(): String = joinToString(",", "[", "]") { "%02X".format(it) }