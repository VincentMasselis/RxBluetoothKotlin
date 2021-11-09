@file:Suppress("MemberVisibilityCanBePrivate")

package com.masselis.rxbluetoothkotlin

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.os.Build
import androidx.annotation.RequiresApi
import com.masselis.rxbluetoothkotlin.CannotInitialize.CannotInitializeCharacteristicWrite
import com.masselis.rxbluetoothkotlin.DeviceDisconnected.CharacteristicWriteDeviceDisconnected
import java.util.*


// ------------------ Bluetooth exceptions for scanning and I/O

/**
 * Fire this error if the bluetooth is turned off on the current device. You can ask the user to
 * enable Bluetooth by starting an activity for result with this intent :
 * [android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE]
 */
public class BluetoothIsTurnedOff : Throwable() {
    override fun toString(): String = "BluetoothIsTurnedOff()"
}

// ------------------ Bluetooth exceptions for scanning only

/**
 * Fire this error if the current device doesn't support Bluetooth.
 */
public class DeviceDoesNotSupportBluetooth : Throwable() {
    override fun toString(): String = "DeviceDoesNotSupportBluetooth()"
}

/**
 * Error fired if the location permission is require for the current app. You have to request for
 * the missing permission [android.Manifest.permission.ACCESS_FINE_LOCATION].
 */
public class NeedLocationPermission : Throwable() {
    override fun toString(): String = "NeedLocationPermission()"
}

/**
 * Error fired if the bluetooth permission is require for the current app. You have to request for
 * the missing permission [android.Manifest.permission.BLUETOOTH_SCAN].
 */
@RequiresApi(Build.VERSION_CODES.S)
public class NeedBluetoothScanPermission : Throwable() {
    override fun toString(): String = "NeedBluetoothScanPermission()"
}

/**
 * Error fired if the bluetooth permission is require for the current app. You have to request for
 * the missing permission [android.Manifest.permission.BLUETOOTH_CONNECT].
 */
@RequiresApi(Build.VERSION_CODES.S)
public class NeedBluetoothConnectPermission : Throwable() {
    override fun toString(): String = "NeedBluetoothConnectPermission()"
}

/**
 * Fired if location service is disabled for the app. You can ask the user to enable Location
 * service by starting an activity for result with this intent :
 * [android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS]
 */
public class LocationServiceDisabled : Throwable() {
    override fun toString(): String = "LocationServiceDisabled()"
}

/**
 * Fired if an error append when scanning.
 * The [status] field is filled with an [Int] provided by the Android framework, it could be one of
 * the [android.bluetooth.le.ScanCallback.SCAN_FAILED_*] const
 */
public class ScanFailedException(public val status: Int) : Throwable() {
    override fun toString(): String = "ScanFailedException(status=$status)"
}


// ------------------ Bluetooth exceptions when connected

/**
 * Exception fired when trying to connect to a remote device but the [BluetoothDevice.connectGatt] method returned null.
 * According to the Android source code, this exception could fire if the device doesn't support Bluetooth or if an
 * exception occurs while getting the Bluetooth interface from the manufacturer.
 */
public class NullBluetoothGatt : Throwable() {
    override fun toString(): String = "NullBluetoothGatt()"
}

/**
 * Fired when a I/O operation takes more than 1 minute to be executed. Normally, a
 * [DeviceDisconnected] exception should be fired before this exception because the bluetooth
 * standard define a Timeout of 3 seconds but, sometimes, the Android BLE framework doesn't call the
 * callback and the [DeviceDisconnected] exception is not fired ¯\_(ツ)_/¯. Is this case, this
 * exception is fired.
 */
public class BluetoothTimeout : Throwable() {
    override fun toString(): String = "BluetoothTimeout()"
}

/**
 * Fired when trying to search a characteristic whereas services are not discovered
 */
public class LookingForCharacteristicButServicesNotDiscovered(
    public val device: BluetoothDevice,
    public val characteristicUUID: UUID
) : Throwable() {
    override fun toString(): String =
        "SearchingCharacteristicButServicesNotDiscovered(device=$device, characteristicUUID=$characteristicUUID)"
}

/**
 * Fired when calling [RxBluetoothGatt.enableNotification] or [RxBluetoothGatt.disableNotification] if the descriptor for notifications is not found.
 */
public class DescriptorNotFound(
    public val device: BluetoothDevice,
    public val characteristicUUID: UUID,
    public val descriptorUUID: UUID
) : Throwable() {
    override fun toString(): String =
        "DescriptorNotFound(device=$device, characteristicUUID=$characteristicUUID, descriptorUUID=$descriptorUUID)"
}

// ------------------ Bluetooth exceptions for I/O only

/**
 * Top error corresponding to a device disconnection. Each I/O method like [RxBluetoothGatt.write] have an specific implementation of [DeviceDisconnected], in this case it's
 * [CharacteristicWriteDeviceDisconnected].
 *
 * The [status] field is filled with an [Int] provided by the Android framework. Android sources are
 * undocumented but [status] seems to refers to theses consts :
 * [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h]
 */
public sealed class DeviceDisconnected(public val device: BluetoothDevice, public val status: Int) :
    Throwable() {
    override fun toString(): String = "DeviceDisconnected(device=$device, status=$status)"

    /**
     * Fired when device disconnect. Unlike the other sealed public classes, this [Throwable] doesn't
     * provides the bluetooth operation which has failed and caused a disconnection.
     */
    public class SimpleDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "SimpleDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while fetching RSSI
     */
    public class RssiDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "RssiDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while discovering services
     */
    public class DiscoverServicesDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "DiscoverServicesDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while requesting MTU
     */
    public class MtuDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "MtuDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading phy
     */
    public class ReadPhyDeviceDisconnected(bluetoothDevice: BluetoothDevice, status: Int) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String = "ReadPhyDeviceDisconnected() ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading phy
     */
    public class SetPreferredPhyDeviceDisconnected(
        public val connectionPhy: ConnectionPHY,
        public val phyOptions: Int,
        bluetoothDevice: BluetoothDevice,
        status: Int
    ) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "SetPreferredPhyDeviceDisconnected(connectionPhy=$connectionPhy, phyOptions=$phyOptions) ${super.toString()}"
    }

    /**
     * Fired if the device disconnects while changing the notification state (enable or disable)
     */
    public class ChangeNotificationDeviceDisconnected(
        bluetoothDevice: BluetoothDevice, status: Int,
        public val characteristic: BluetoothGattCharacteristic,
        public val value: ByteArray,
        public val checkIfAlreadyChanged: Boolean
    ) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "ChangeNotificationDeviceDisconnected(characteristic=$characteristic, notificationpublic value=${value.contentToString()}, checkIfAlreadyChanged=$checkIfAlreadyChanged) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading characteristic
     */
    public class CharacteristicReadDeviceDisconnected(
        bluetoothDevice: BluetoothDevice,
        status: Int,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic
    ) :
        DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "CharacteristicReadDeviceDisconnected(service=$service, characteristic=$characteristic) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while writing characteristic
     */
    public class CharacteristicWriteDeviceDisconnected(
        bluetoothDevice: BluetoothDevice,
        status: Int,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val value: ByteArray
    ) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "CharacteristicWriteDeviceDisconnected(service=$service, characteristic=$characteristic, public value=${value.contentToString()}) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while listening changes on this [characteristic]
     */
    public class ListenChangesDeviceDisconnected(
        bluetoothDevice: BluetoothDevice,
        status: Int,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic
    ) : DeviceDisconnected(bluetoothDevice, status) {
        override fun toString(): String =
            "ListenChangesDeviceDisconnected(service=$service, characteristic=$characteristic) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while reading descriptor
     */
    public class DescriptorReadDeviceDisconnected(
        device: BluetoothDevice,
        status: Int,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val descriptor: BluetoothGattDescriptor
    ) : DeviceDisconnected(device, status) {
        override fun toString(): String =
            "DescriptorReadDeviceDisconnected(service=$service, characteristic=$characteristic, descriptor=$descriptor) ${super.toString()}"
    }

    /**
     * Fired when device disconnect while writing descriptor
     */
    public class DescriptorWriteDeviceDisconnected(
        device: BluetoothDevice,
        status: Int,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val descriptor: BluetoothGattDescriptor,
        public val value: ByteArray
    ) : DeviceDisconnected(device, status) {
        override fun toString(): String =
            "DescriptorWriteDeviceDisconnected(service=$service, characteristic=$characteristic, descriptor=$descriptor, public value=${value.contentToString()}) ${super.toString()}"
    }
}

/**
 * Top error corresponding to an error write preparing I/O. Each I/O method like [RxBluetoothGatt.write] have an specific implementation of [CannotInitialize], in this case it's
 * [CannotInitializeCharacteristicWrite].
 */
public sealed class CannotInitialize(public val device: BluetoothDevice) : Throwable() {
    override fun toString(): String = "CannotInitialize(device=$device)"

    /**
     * Fired when RSSI request cannot be proceeded
     */
    public class CannotInitializeRssiReading(device: BluetoothDevice) : CannotInitialize(device) {
        override fun toString(): String = "CannotInitializeRssiReading() ${super.toString()}"
    }

    /**
     * Fired when service discover request cannot be proceeded
     */
    public class CannotInitializeServicesDiscovering(device: BluetoothDevice) :
        CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeServicesDiscovering() ${super.toString()}"
    }

    /**
     * Fired when MTU request cannot be proceeded
     */
    public class CannotInitializeMtuRequesting(device: BluetoothDevice) : CannotInitialize(device) {
        override fun toString(): String = "CannotInitializeMtuRequesting() ${super.toString()}"
    }

    /**
     * Fired when read request cannot be proceeded
     */
    public class CannotInitializeCharacteristicReading(
        device: BluetoothDevice,
        public val service: BluetoothGattService?,
        public val characteristic: BluetoothGattCharacteristic,
        public val properties: Int,
        public val internalService: Any?,
        public val clientIf: Any?,
        public val foundDevice: Any?,
        public val isDeviceBusy: Any?
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeCharacteristicReading(service=$service, characteristic=$characteristic, properties=$properties, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }

    /**
     * Fired when write request cannot be proceeded
     */
    public class CannotInitializeCharacteristicWrite(
        device: BluetoothDevice,
        public val service: BluetoothGattService?,
        public val characteristic: BluetoothGattCharacteristic,
        public val value: ByteArray,
        public val properties: Int,
        public val internalService: Any?,
        public val clientIf: Any?,
        public val foundDevice: Any?,
        public val isDeviceBusy: Any?
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeCharacteristicWrite(service=$service, characteristic=$characteristic, public value=${value.contentToString()}, properties=$properties, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }

    /**
     * Fired when notification request cannot be proceeded
     */
    public class CannotInitializeCharacteristicNotification(
        device: BluetoothDevice,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val internalService: Any?,
        public val clientIf: Any?,
        public val foundDevice: Any?
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeCharacteristicNotification(service=$service, characteristic=$characteristic, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice) ${super.toString()}"
    }

    /**
     * Fired when read request cannot be proceeded
     */
    public class CannotInitializeDescriptorReading(
        device: BluetoothDevice,
        public val service: BluetoothGattService?,
        public val characteristic: BluetoothGattCharacteristic?,
        public val descriptor: BluetoothGattDescriptor,
        public val internalService: Any?,
        public val clientIf: Any?,
        public val foundDevice: Any?,
        public val isDeviceBusy: Any?
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeDescriptorReading(service=$service, characteristic=$characteristic, descriptor=$descriptor, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }

    /**
     * Fired when write request cannot be proceeded
     */
    public class CannotInitializeDescriptorWrite(
        device: BluetoothDevice,
        public val service: BluetoothGattService?,
        public val characteristic: BluetoothGattCharacteristic?,
        public val descriptor: BluetoothGattDescriptor,
        public val value: ByteArray,
        public val internalService: Any?,
        public val clientIf: Any?,
        public val foundDevice: Any?,
        public val isDeviceBusy: Any?
    ) : CannotInitialize(device) {
        override fun toString(): String =
            "CannotInitializeDescriptorWrite(service=$service, characteristic=$characteristic, descriptor=$descriptor, public value=${value.contentToString()}, internalService=$internalService, clientIf=$clientIf, foundDevice=$foundDevice, isDeviceBusy=$isDeviceBusy) ${super.toString()}"
    }
}

/**
 * Fired when an Read or Write operation fails.
 * The [status] field is filled with an [Int] provided by the Android framework. Android sources are
 * undocumented but [status] seems to refers to theses consts :
 * [https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h]
 */
public sealed class IOFailed(public val status: Int, public val device: BluetoothDevice) :
    Throwable() {
    override fun toString(): String = "IOFailed(status=$status, device=$device)"

    /**
     * Fired when RSSI request returns an error
     */
    public class RssiReadingFailed(status: Int, device: BluetoothDevice) :
        IOFailed(status, device) {
        override fun toString(): String = "RssiReadingFailed() ${super.toString()}"
    }

    /**
     * Fired when service discovering request returns an error
     */
    public class ServiceDiscoveringFailed(status: Int, device: BluetoothDevice) :
        IOFailed(status, device) {
        override fun toString(): String = "ServiceDiscoveringFailed() ${super.toString()}"
    }

    /**
     * Fired when MTU request returns an error
     */
    public class MtuRequestingFailed(status: Int, device: BluetoothDevice) :
        IOFailed(status, device) {
        override fun toString(): String = "MtuRequestingFailed() ${super.toString()}"
    }

    /**
     * Fired when PHY request returns an error
     */
    public class PhyReadFailed(
        public val connectionPhy: ConnectionPHY,
        status: Int,
        device: BluetoothDevice
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "PhyReadFailed(connectionPhy=$connectionPhy) ${super.toString()}"
    }

    /**
     * Fired when PHY preferred changes returns an error
     */
    public class SetPreferredPhyFailed(
        public val connectionPhy: ConnectionPHY,
        public val phyOptions: Int,
        status: Int,
        device: BluetoothDevice
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "SetPreferredPhyFailed(connectionPhy=$connectionPhy, phyOptions=$phyOptions) ${super.toString()}"
    }

    /**
     * Fired when read request returns an error
     */
    public class CharacteristicReadingFailed(
        status: Int,
        device: BluetoothDevice,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic
    ) :
        IOFailed(status, device) {
        override fun toString(): String =
            "CharacteristicReadingFailed(service=$service, characteristic=$characteristic) ${super.toString()}"
    }

    /**
     * Fired when write request returns an error
     */
    public class CharacteristicWriteFailed(
        status: Int,
        device: BluetoothDevice,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val value: ByteArray
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "CharacteristicWriteFailed(service=$service, characteristic=$characteristic, public value=${value.contentToString()}) ${super.toString()}"
    }

    /**
     * Fired when read request returns an error
     */
    public class DescriptorReadingFailed(
        status: Int,
        device: BluetoothDevice,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val descriptor: BluetoothGattDescriptor
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "DescriptorReadingFailed(service=$service, characteristic=$characteristic, descriptor=$descriptor) ${super.toString()}"
    }

    /**
     * Fired when write request returns an error
     */
    public class DescriptorWriteFailed(
        status: Int,
        device: BluetoothDevice,
        public val service: BluetoothGattService,
        public val characteristic: BluetoothGattCharacteristic,
        public val descriptor: BluetoothGattDescriptor,
        public val value: ByteArray
    ) : IOFailed(status, device) {
        override fun toString(): String =
            "DescriptorWriteFailed(service=$service, characteristic=$characteristic, descriptor=$descriptor, public value=${value.contentToString()}) ${super.toString()}"
    }
}