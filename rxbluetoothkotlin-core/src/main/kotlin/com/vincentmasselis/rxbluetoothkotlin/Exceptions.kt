package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import com.vincentmasselis.rxbluetoothkotlin.CannotInitialize.CannotInitializeCharacteristicWrite
import com.vincentmasselis.rxbluetoothkotlin.DeviceDisconnected.CharacteristicWriteDeviceDisconnected
import java.util.*


// ------------------ Bluetooth exceptions for scanning and I/O

/**
 * Fire this error if the bluetooth is turned off on the current device. You can ask the user to
 * enable Bluetooth by starting an activity for result with this intent :
 * [android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE]
 */
class BluetoothIsTurnedOff : Throwable() {
    override fun toString(): String = "BluetoothIsTurnedOff()"
}

// ------------------ Bluetooth exceptions for scanning only

/**
 * Fire this error if the current device doesn't support Bluetooth.
 */
class DeviceDoesNotSupportBluetooth : Throwable() {
    override fun toString(): String = "DeviceDoesNotSupportBluetooth()"
}

/**
 * Error fired if the location permission is require for the current app. You have to request for
 * the missing permission [android.Manifest.permission.ACCESS_FINE_LOCATION].
 */
class NeedLocationPermission : Throwable() {
    override fun toString(): String = "NeedLocationPermission()"
}

/**
 * Fired if location service is disabled for the app. You can ask the user to enable Location
 * service by starting an activity for result with this intent :
 * [android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS]
 */
class LocationServiceDisabled : Throwable() {
    override fun toString(): String = "LocationServiceDisabled()"
}

/**
 * Fired if an error append when scanning.
 * The [status] field is filled with an [Int] provided by the Android framework, it could be one of
 * the [android.bluetooth.le.ScanCallback.SCAN_FAILED_*] const
 */
class ScanFailedException(val status: Int) : Throwable() {
    override fun toString(): String = "ScanFailedException(status=$status)"
}


// ------------------ Bluetooth exceptions when connected

/**
 * Exception fired when trying to connect to a remote device but the [BluetoothDevice.connectGatt] method returned null.
 * According to the Android source code, this exception could fire if the device doesn't support Bluetooth or if an
 * exception occurs while getting the Bluetooth interface from the manufacturer.
 */
class NullBluetoothGatt : Throwable() {
    override fun toString(): String = "NullBluetoothGatt()"
}

/**
 * Fired when a I/O operation takes more than 1 minute to be executed. Normally, a
 * [DeviceDisconnected] exception should be fired before this exception because the bluetooth
 * standard define a Timeout of 3 seconds but, sometimes, the Android BLE framework doesn't call the
 * callback and the [DeviceDisconnected] exception is not fired ¯\_(ツ)_/¯. Is this case, this
 * exception is fired.
 */
class BluetoothTimeout : Throwable() {
    override fun toString(): String = "BluetoothTimeout()"
}

/**
 * Fired when trying to search a characteristic whereas services are not discovered
 */
class LookingForCharacteristicButServicesNotDiscovered(val device: BluetoothDevice, val characteristicUUID: UUID) : Throwable() {
    override fun toString(): String =
        "SearchingCharacteristicButServicesNotDiscovered(device=$device, characteristicUUID=$characteristicUUID)"
}

/**
 * Fired when calling [android.bluetooth.BluetoothGatt.rxEnableNotification] or
 * [android.bluetooth.BluetoothGatt.rxDisableNotification] and the descriptor for notifications is
 * not found.
 */
class DescriptorNotFound(val device: BluetoothDevice, val characteristicUUID: UUID, val descriptorUUID: UUID) : Throwable() {
    override fun toString(): String =
        "DescriptorNotFound(device=$device, characteristicUUID=$characteristicUUID, descriptorUUID=$descriptorUUID)"
}

// ------------------ Bluetooth exceptions for I/O only

/**
 * Top error corresponding to a device disconnection. Each I/O method like
 * [android.bluetooth.BluetoothGatt.write] have an specific implementation of
 * [DeviceDisconnected], in this case it's [CharacteristicWriteDeviceDisconnected].
 *
 * The [status] field is filled with an [Int] provided by the Android framework. Android sources are
 * undocumented but [status] seems to refers to theses consts :
 * [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h]
 *
 * /!\ IMPORTANT : It can returns a -1 [status] which is not documented in the previous URL. This status
 * is used when the connection was killed by a [BluetoothIsTurnedOff] exception.
 */
sealed class DeviceDisconnected(val device: BluetoothDevice, val status: Int) : Throwable() {
    override fun toString(): String = "DeviceDisconnected(device=$device, status=$status)"

    /**
     * Fired when device disconnect. Unlike the other sealed classes, this [Throwable] doesn't
     * provides the bluetooth operation which has failed.
     */
    class SimpleDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "SimpleDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while fetching RSSI
     */
    class RssiDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "RssiDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while discovering services
     */
    class DiscoverServicesDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "DiscoverServicesDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while requesting MTU
     */
    class MtuDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "MtuDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading phy
     */
    class ReadPhyDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "ReadPhyDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading phy
     */
    class SetPreferredPhyDeviceDisconnected(val connectionPhy: ConnectionPHY, val phyOptions: Int, bluetoothDevice: BluetoothDevice, status: Int) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "SetPreferredPhyDeviceDisconnected(connectionPhy=$connectionPhy, phyOptions=$phyOptions) ${super.toString()}"
    }

    /**
     * Exception fired when the current connectLegacy connection is lost
     */
    class GattDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "GattDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired if the device disconnects while changing the notification state (enable or disable)
     */
    class ChangeNotificationDeviceDisconnected(
        bluetoothDevice: BluetoothDevice, status: Int,
        val characteristic: BluetoothGattCharacteristic,
        val notificationValue: ByteArray,
        val checkIfAlreadyChanged: Boolean
    ) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "ChangeNotificationDeviceDisconnected(characteristic=$characteristic, notificationValue=${Arrays.toString(notificationValue)}, checkIfAlreadyChanged=$checkIfAlreadyChanged) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading characteristic
     */
    class CharacteristicReadDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "CharacteristicReadDeviceDisconnected(service=$service, characteristic=$characteristic) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while writing characteristic
     */
    class CharacteristicWriteDeviceDisconnected(
        bluetoothDevice: BluetoothDevice,
        status: Int,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray
    ) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "CharacteristicWriteDeviceDisconnected(service=$service, characteristic=$characteristic, value=${Arrays.toString(value)}) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while listening changes on this [characteristic]
     */
    class ListenChangesDeviceDisconnected(
        bluetoothDevice: BluetoothDevice,
        status: Int,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic
    ) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "ListenChangesDeviceDisconnected(service=$service, characteristic=$characteristic) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading descriptor
     */
    class DescriptorReadDeviceDisconnected(
        device: BluetoothDevice,
        status: Int,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor
    ) : DeviceDisconnected(device, status) {
        override fun toString(): String =
            "DescriptorReadDeviceDisconnected(service=$service, characteristic=$characteristic, descriptor=$descriptor) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while writing descriptor
     */
    class DescriptorWriteDeviceDisconnected(
        device: BluetoothDevice,
        status: Int,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray
    ) : DeviceDisconnected(device, status) {
        override fun toString(): String =
            "DescriptorWriteDeviceDisconnected(service=$service, characteristic=$characteristic, descriptor=$descriptor, value=${Arrays.toString(value)}) ${super.toString()}"
    }
}

/**
 * Top error corresponding to an error write preparing I/O. Each I/O method like
 * [android.bluetooth.BluetoothGatt.write] have an specific implementation of
 * [CannotInitialize], in this case it's [CannotInitializeCharacteristicWrite].
 */
sealed class CannotInitialize(val device: BluetoothDevice) : Throwable() {
    override fun toString(): String = "CannotInitialize(device=$device)"

    /**
     * Fired when RSSI request cannot be proceeded
     */
    class CannotInitializeRssiReading(device: BluetoothDevice) : CannotInitialize(device) {
        override fun toString(): String = "CannotInitializeRssiReading() ${super.toString()}"
    }

    /**
     * Fired when service discover request cannot be proceeded
     */
    class CannotInitializeServicesDiscovering(device: BluetoothDevice) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeServicesDiscovering() ${super.toString()}"
    }

    /**
     * Fired when MTU request cannot be proceeded
     */
    class CannotInitializeMtuRequesting(device: BluetoothDevice) : CannotInitialize(device) {
        override fun toString(): String = "CannotInitializeMtuRequesting() ${super.toString()}"
    }

    /**
     * Fired when read request cannot be proceeded
     */
    class CannotInitializeCharacteristicReading(
        device: BluetoothDevice,
        val service: BluetoothGattService?,
        val characteristic: BluetoothGattCharacteristic,
        val properties: Int,
        val internalService: Any,
        val clientIf: Any,
        val foundDevice: Any?,
        val isDeviceBusy: Any
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeCharacteristicReading(service=$service, characteristic=$characteristic, properties=$properties, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }

    /**
     * Fired when write request cannot be proceeded
     */
    class CannotInitializeCharacteristicWrite(
        device: BluetoothDevice,
        val service: BluetoothGattService?,
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val properties: Int,
        val internalService: Any,
        val clientIf: Any,
        val foundDevice: Any?,
        val isDeviceBusy: Any
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeCharacteristicWrite(service=$service, characteristic=$characteristic, value=${Arrays.toString(value)}, properties=$properties, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }

    /**
     * Fired when notification request cannot be proceeded
     */
    class CannotInitializeCharacteristicNotification(
        device: BluetoothDevice,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val internalService: Any,
        val clientIf: Any,
        val foundDevice: Any?
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeCharacteristicNotification(service=$service, characteristic=$characteristic, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice) ${super.toString()}"
    }

    /**
     * Fired when read request cannot be proceeded
     */
    class CannotInitializeDescriptorReading(
        device: BluetoothDevice,
        val service: BluetoothGattService?,
        val characteristic: BluetoothGattCharacteristic?,
        val descriptor: BluetoothGattDescriptor,
        val internalService: Any,
        val clientIf: Any,
        val foundDevice: Any?,
        val isDeviceBusy: Any
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeDescriptorReading(service=$service, characteristic=$characteristic, descriptor=$descriptor, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }

    /**
     * Fired when write request cannot be proceeded
     */
    class CannotInitializeDescriptorWrite(
        device: BluetoothDevice,
        val service: BluetoothGattService?,
        val characteristic: BluetoothGattCharacteristic?,
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray,
        val internalService: Any,
        val clientIf: Any,
        val foundDevice: Any?,
        val isDeviceBusy: Any
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeDescriptorWrite(service=$service, characteristic=$characteristic, descriptor=$descriptor, value=${Arrays.toString(value)}, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }
}

/**
 * Fired when an Read or Write operation fails.
 * The [status] field is filled with an [Int] provided by the Android framework. Android sources are
 * undocumented but [status] seems to refers to theses consts :
 * [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h]
 */
sealed class IOFailed(val status: Int, val device: BluetoothDevice) : Throwable() {
    override fun toString(): String = "IOFailed(status=$status, device=$device)"

    /**
     * Fired when RSSI request returns an error
     */
    class RssiReadingFailed(status: Int, device: BluetoothDevice) : IOFailed(status, device) {
        override fun toString(): String = "RssiReadingFailed() ${super.toString()}"
    }

    /**
     * Fired when service discovering request returns an error
     */
    class ServiceDiscoveringFailed(status: Int, device: BluetoothDevice) : IOFailed(status, device) {
        override fun toString(): String = "ServiceDiscoveringFailed() ${super.toString()}"
    }

    /**
     * Fired when MTU request returns an error
     */
    class MtuRequestingFailed(status: Int, device: BluetoothDevice) : IOFailed(status, device) {
        override fun toString(): String = "MtuRequestingFailed() ${super.toString()}"
    }

    /**
     * Fired when PHY request returns an error
     */
    class PhyReadFailed(val connectionPhy: ConnectionPHY, status: Int, device: BluetoothDevice) : IOFailed(status, device) {
        override fun toString(): String = "PhyReadFailed(connectionPhy=$connectionPhy) ${super.toString()}"
    }

    /**
     * Fired when PHY preferred changes returns an error
     */
    class SetPreferredPhyFailed(val connectionPhy: ConnectionPHY, val phyOptions: Int, status: Int, device: BluetoothDevice) : IOFailed(status, device) {
        override fun toString(): String = "SetPreferredPhyFailed(connectionPhy=$connectionPhy, phyOptions=$phyOptions) ${super.toString()}"
    }

    /**
     * Fired when read request returns an error
     */
    class CharacteristicReadingFailed(status: Int, device: BluetoothDevice, val service: BluetoothGattService, val characteristic: BluetoothGattCharacteristic) :
        IOFailed(status, device) {
        override fun toString(): String =
            "CharacteristicReadingFailed(service=$service, characteristic=$characteristic) ${super.toString()}"
    }

    /**
     * Fired when write request returns an error
     */
    class CharacteristicWriteFailed(
        status: Int,
        device: BluetoothDevice,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "CharacteristicWriteFailed(service=$service, characteristic=$characteristic, value=${Arrays.toString(value)}) ${super.toString()}"
    }

    /**
     * Fired when read request returns an error
     */
    class DescriptorReadingFailed(
        status: Int,
        device: BluetoothDevice,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "DescriptorReadingFailed(service=$service, characteristic=$characteristic, descriptor=$descriptor) ${super.toString()}"
    }

    /**
     * Fired when write request returns an error
     */
    class DescriptorWriteFailed(
        status: Int,
        device: BluetoothDevice,
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "DescriptorWriteFailed(service=$service, characteristic=$characteristic, descriptor=$descriptor, value=${Arrays.toString(value)}) ${super.toString()}"
    }
}