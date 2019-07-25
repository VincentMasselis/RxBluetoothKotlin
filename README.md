[ ![Download](https://api.bintray.com/packages/vincentmasselis/maven/rx-bluetooth-kotlin/images/download.svg) ](https://bintray.com/vincentmasselis/maven/rx-bluetooth-kotlin/_latestVersion)
[![Build Status](https://app.bitrise.io/app/94c2826fa7361333/status.svg?token=dAysx6Rt7j8iL29CFZlzGQ&branch=master)](https://app.bitrise.io/app/94c2826fa7361333)

# RxBluetoothKotlin
Android + BLE + Kotlin + RxJava2

Bluetooth Low Energy on Android made easy with RxJava.

Made with love at the [Equisense](http://equisense.com) HQ. This library is used in our [Equisense app](https://play.google.com/store/apps/details?id=com.equisense.motions) since years.

Looking for BLE with Coroutines instead of RxJava ? Take a look at the [LouisCAD's implementation](https://github.com/Beepiz/BleGattCoroutines).

## TL/DR

```groovy
implementation 'com.vincentmasselis.rxbluetoothkotlin:rxbluetoothkotlin-core:1.1.6'
// Add this to use the scanner
implementation 'no.nordicsemi.android.support.v18:scanner:1.4.0'
// RxBluetoothKotlin doesn't provides the RxJava dependecy, you have to add it yourself:
implementation 'io.reactivex.rxjava2:rxjava:2.2.10'
```

### Scan
```kotlin
val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
bluetoothManager.rxScan()
  .subscribe { scanResult ->
  }
```
### Connect
```kotlin
bluetoothDevice.connectRxGatt()
  .flatMapMaybe { gatt -> gatt.whenConnectionIsReady().map { gatt } }
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
Interact with Bluetooth Low Energy devices on Android is [hard](https://hellsoft.se/bluetooth-low-energy-on-android-part-1-1aa8bf60717d). The BLE specs uses unfamiliars concepts, the BLE API from Android could fails at any moment and some exceptions are silent. Because of this, a basic method like `write(BluetoothGattCharacteristic)` could fails for 5 differents reasons. It becomes harder if you are chaining this call with other calls, this sum up to a thrown exception when it's impossible to known which call fails and why.

For this reason, every public method from this library is documented with every exceptions which could be fired and most of the exception are unique. For example: `write(BluetoothGattCharacteristic)` could fire:
* Unique `CharacteristicWriteDeviceDisconnected` if the device disconnects while writing
* Unique `CannotInitializeCharacteristicWrite` if the Android API returned `false` when calling `writeCharacteristic`
* Unique `CharacteristicWriteFailed` if the characteristic could not be written
* `BluetoothIsTurnedOff` if bluetooth...... is turned off 🤷🏻‍♀️
* `BluetoothTimeout` if writing takes more than 1 minute

All the 3 uniques exceptions are containing the required data to understand why it failed. For example `CharacteristicWriteDeviceDisconnected` has 5 fields, `bluetoothDevice`, `status`, `service`, `characteristic` and `value`, all of theses fields should helps you to understand what's going on.

## Demo app
If you're not familiar with the Bluetooth Low Energy API or if you want to try this lib, you can build and run the [demo app](https://github.com/VincentMasselis/RxBluetoothKotlin/tree/master/dev-app) from this repo.

<img src="https://github.com/VincentMasselis/RxBluetoothKotlin/raw/master/assets/pictures/demo-scan-activity.jpg" width="250"> <img src="https://github.com/VincentMasselis/RxBluetoothKotlin/raw/master/assets/pictures/demo-device-activity.jpg" width="250">

## Decorator patter
⚠ Before reading this part, you must know how a [Decorator design pattern](https://en.wikipedia.org/wiki/Decorator_pattern) works and how to make a new one.

On Android, communicating with a Bluetooth device requires an instance of `BluetoothGatt` and an instance of `BluetoothGattCallback`. RxBluetoothKotlin wraps both of theses types into `RxBluetoothGatt` and `RxBluetoothGatt.Callback` types to add some reactive touch to the system objects. Because `RxBluetoothGatt` is an interface and `BluetoothGattCallback` an abstract class, calling `connectRxGatt` will return a default implentation for both of them. You are free to wrap the returned `RxBluetoothGatt` implementation and update the original object by adding you own behavior, you only have to follow the Decorator rules.

### Decorate RxBluetoothGatt
The following diagram will show you which classes are used to create the decorator pattern:

![UML Class diagram](http://yuml.me/63d484c6.svg)

[comment]: <> (Used syntax 
"""
[BluetoothGatt{bg:cornsilk}]<1-<>[<<RxBluetoothGatt>>{bg:lavender}]
[<<RxBluetoothGatt>>]^-.-[RxBluetoothGattImpl{bg:lavender}]
[<<RxBluetoothGatt>>]^-.-[SimpleRxBluetoothGatt{bg:lavender}]
[<<RxBluetoothGatt>>]<1-<>[SimpleRxBluetoothGatt]
[SimpleRxBluetoothGatt]^-[An other Decorator{bg:lightblue}]
[SimpleRxBluetoothGatt]^-[Your Decorator{bg:lightblue}]lue}]
"""
On this website `https://yuml.me/diagram/scruffy/class/draw`
)

As you can see, to create a decorator, you only have to subclass `SimpleRxBluetoothGatt`. If you want to decorate `RxBluetoothGatt.Callback` just subclass `SimpleRxBluetoothGattCallback` like you do with `SimpleRxBluetoothGatt` from the previous example.

When your decorators are written you can send them to RxBluetoothKotlin by adding `callbackConstructor` and `rxGattConstructor` parameters when calling `connectRxGatt`. Defaults implentation of RxBluetoothKotlin uses `RxBluetoothGattImpl` and `RxBluetoothGattCallbackImpl`, by using you own decorator you can change the way RxBluetoothKotlin is communicating with the Android SDK in order to match your own requirements.

## Links
Report an issue by using [github](https://github.com/VincentMasselis/RxBluetoothKotlin/issues)

Follow me on Twitter [@VincentMsls](https://twitter.com/VincentMsls)

Discover our [Equisense sensors](https://equisense.com)

//TODO 

- More test

- Getting started

- Example app

- Licence
