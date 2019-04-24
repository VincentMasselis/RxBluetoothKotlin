package com.vincentmasselis.rxbluetoothkotlin


/**
 * This property is generally used as the last parameter of the most of the methods from [android.bluetooth.BluetoothGattCallback]. When the operation succeeds, [Status] equals
 * [android.bluetooth.BluetoothGatt.GATT_SUCCESS], otherwise it returns an error code. The error codes are not officially not documented by the Android team and can contains
 * different values between manufacturers. Generally, It match theses values :
 * [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h].
 *
 * @see android.bluetooth.BluetoothGattCallback
 */
typealias Status = Int