[ ![Download](https://api.bintray.com/packages/vincentmasselis/maven/rx-bluetooth-kotlin/images/download.svg) ](https://bintray.com/vincentmasselis/maven/rx-bluetooth-kotlin/_latestVersion)
[![Build Status](https://app.bitrise.io/app/94c2826fa7361333/status.svg?token=dAysx6Rt7j8iL29CFZlzGQ&branch=master)](https://app.bitrise.io/app/94c2826fa7361333)

# RxBluetoothKotlin
Android + BLE + Kotlin + RxJava2

Made with love at the [Equisense](http://equisense.com) HQ. This library is used in our [Equisense app](https://play.google.com/store/apps/details?id=com.equisense.motions).

## TL/DR

`implementation 'com.vincentmasselis.rxbluetoothkotlin:rxbluetoothkotlin-core:1.0.0'`

### Scan
```kotlin
val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
bluetoothManager.rxScan(context)
  .subscribe { scanResult ->
  }
```
### Connect
```kotlin
bluetoothDevice.connectRxGatt(context)
  .flatMapMaybe { gatt -> gatt.whenConnectionIsReady() }
  .subsribe { rxBluetoothGatt ->
  }
```
### Read
```kotlin
rxBluetoothGatt.read(characteristic)
  .subscribe { byteArray ->
  }
```
### Write
```kotlin
rxBluetoothGatt.write(characteristic, byteArray)
  .subscribe { _ ->
  }
```
### Listen
```kotlin
rxBluetoothGatt.enableNotification(characteristic)
  .flatMapPublisher { listenChanges(characteristic) }
  .subscribe { byteArray ->
  }
```
### Disconnect
```kotlin
rxBluetoothGatt.disconnect().subscribe()
```

## Requirements
* Min target API 18
* A manifest with `<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />` and `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />`
* The user permission access coarse Location `requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_CODE_COARSE_LOCATION)`
* A turned on bluetooth chip `(context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled`

## Logging
When scanning with `rxScan()` or connecting with `connectRxGatt()`, you can set the `logger` parameter. By setting it, RxBluetoothKotlin will produce a log for every bluetooth input, output, starting scan, error thrown, etc.. I recommend to set it for debugging purproses and/or if you're not familiar with the Android BLE API. It could helps you a lot to understand what's going on between your app and the Bluetooth Low Energy device.

## Error management
Interact with Bluetooth Low Energy devices on Android is hard. The BLE specs uses unfamiliars concepts, the BLE API from Android could fails at any moment and some exceptions are silent. Because of this, a basic method like `write(BluetoothGattCharacteristic)` could fails for 5 differents reasons. It becomes harder if you are chaining this call with other calls, this sum up to a thrown exception when it's impossible to known which call fails and why.

For this reason, every public method from this library is documented with every exceptions which could be fired and most of the exception are unique. For example: `write(BluetoothGattCharacteristic)` could fire:
* Unique `CharacteristicWriteDeviceDisconnected` if the device disconnects while writing
* Unique `CannotInitializeCharacteristicWrite` if the Android API returned `false` when calling `writeCharacteristic`
* Unique `CharacteristicWriteFailed` if the characteristic could not be written
* `BluetoothIsTurnedOff` is bluetooth...... is turned off 🤷🏻‍♀️
* `BluetoothTimeout` is writing takes more than 1 minute

All the 3 uniques exceptions are containing the required data to understand why it failed. For example `CharacteristicWriteDeviceDisconnected` has 5 fields, `bluetoothDevice`, `status`, `service`, `characteristic` and `value`.



//TODO 

- Short description

- Link to our twitter, github issues

- Code snippet to integrate with gradle

- More test

- Getting started

- Example app

- Licence
