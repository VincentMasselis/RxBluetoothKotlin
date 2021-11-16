package com.vincentmasselis.demoapp

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jakewharton.rxbinding4.view.clicks
import com.masselis.rxbluetoothkotlin.*
import com.vincentmasselis.demoapp.databinding.ActivityDeviceBinding
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.*

class DeviceActivity : AppCompatActivity() {

    private val device by lazy { intent.getParcelableExtra<BluetoothDevice>(DEVICE_EXTRA)!! }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) states.onNext(States.Connecting)
            else finish()
        }
    private val states = BehaviorSubject.createDefault<States>(States.Connecting)
    private lateinit var binding: ActivityDeviceBinding

    private var currTime = System.currentTimeMillis()

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        states
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                @Suppress("UNUSED_VARIABLE") val ignoreMe = when (it) {
                    States.Connecting -> {
                        binding.connectingGroup.visibility = View.VISIBLE
                        binding.connectedGroup.visibility = View.GONE
                    }
                    is States.Connected -> {
                        binding.connectingGroup.visibility = View.GONE
                        binding.connectedGroup.visibility = View.VISIBLE
                    }
                }
            }
            .disposeOnState(ActivityState.DESTROY, this)

        states
            .filter { it is States.Connecting }
            .switchMapSingle { device.connectRxGatt() }
            .onErrorComplete {
                if (it is NeedBluetoothConnectPermission) {
                    permissionLauncher.launch(BLUETOOTH_CONNECT)
                    true
                } else
                    false
            }
            .switchMapMaybe { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { binding.connectingGroup.visibility = View.VISIBLE }
            .doFinally { binding.connectingGroup.visibility = View.INVISIBLE }
            .subscribe(
                {
                    Toast.makeText(this, "Connected !", Toast.LENGTH_SHORT).show()
                    states.onNext(States.Connected(it))
                },
                {
                    val message =
                        when (it) {
                            is BluetoothIsTurnedOff -> "Bluetooth is turned off"
                            is DeviceDisconnected -> "Unable to connect to the device"
                            else -> "Error occurred: $it"
                        }
                    AlertDialog.Builder(this).setMessage(message).show()
                }
            )
            .disposeOnState(ActivityState.DESTROY, this)

        //if we've just connected and our Android version is lollipop or higher, change priority and MTU size for faster connection
        states
            .switchMapMaybe { state ->
                if (state is States.Connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    state.gatt.source.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    state.gatt.requestMtu(500)
                } else {
                    Maybe.empty()
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Toast.makeText(this, "MTU size changed !", Toast.LENGTH_SHORT).show()
            }
            .disposeOnState(ActivityState.DESTROY, this)

        //on button, write then read
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> binding.onButton.clicks().map { state.gatt }
                }
            }
            .switchMapMaybe { gatt ->
                (
                        if (gatt.source.services.isEmpty()) gatt.discoverServices()
                        else Maybe.just(gatt.source.services)
                        ).flatMap {
                        val myData = byteArrayOf((1).toByte())
                        gatt.write(gatt.source.findCharacteristic(UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9F"))!!,myData)
                    }
                    .flatMap { gatt.read(it) }
            }
            .observeOn(AndroidSchedulers.mainThread())

            .subscribe (
                {
                    val pic =
                        if (it[0].toInt() == 1) {
                            ContextCompat.getDrawable(
                                applicationContext, // Context
                                R.drawable.onlight // Drawable
                            )
                        }else{
                            ContextCompat.getDrawable(
                                applicationContext, // Context
                                R.drawable.offlight // Drawable
                            )
                        }
                    binding.imageView.setImageDrawable(pic)
                },
                {
                    val message =
                        when (it) {
                            is BluetoothIsTurnedOff -> "Bluetooth is turned off"
                            is DeviceDisconnected.CharacteristicReadDeviceDisconnected -> "Device disconnected while reading battery"
                            is CannotInitialize.CannotInitializeCharacteristicReading -> "Failed to initialize battery read"
                            is IOFailed.CharacteristicReadingFailed -> "Failed to read the battery"
                            else -> "Error occurred: $it"
                        }
                    AlertDialog.Builder(this).setMessage(message).show()
                })
            .disposeOnState(ActivityState.DESTROY, this)
        //offbutton, write then read
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> binding.offButton.clicks().map { state.gatt }
                }
            }
            .switchMapMaybe { gatt ->
                (
                        if (gatt.source.services.isEmpty()) gatt.discoverServices()
                        else  Maybe.just(gatt.source.services)
                        ).flatMap {
                        val myData = byteArrayOf((0).toByte())
                        gatt.write(gatt.source.findCharacteristic(UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9F"))!!,myData)
                    }
                    .flatMap { gatt.read(it) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe (
                {
                    val pic =
                        if (it[0].toInt() == 1) {
                            ContextCompat.getDrawable(
                                applicationContext, // Context
                                R.drawable.onlight // Drawable
                            )
                        }else{
                            ContextCompat.getDrawable(
                                applicationContext, // Context
                                R.drawable.offlight // Drawable
                            )
                        }
                    binding.imageView.setImageDrawable(pic)
                },
                {
                    val message =
                        when (it) {
                            is BluetoothIsTurnedOff -> "Bluetooth is turned off"
                            is DeviceDisconnected.CharacteristicReadDeviceDisconnected -> "Device disconnected while reading battery"
                            is CannotInitialize.CannotInitializeCharacteristicReading -> "Failed to initialize battery read"
                            is IOFailed.CharacteristicReadingFailed -> "Failed to read the battery"
                            else -> "Error occurred: $it"
                        }
                    AlertDialog.Builder(this).setMessage(message).show()
                })
            .disposeOnState(ActivityState.DESTROY, this)
        //button 1 state notification
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> states.map { state.gatt }
                }
            }
            .switchMap { gatt ->
                (
                        if (gatt.source.services.isEmpty()) gatt.discoverServices()
                        else  Maybe.just(gatt.source.services)
                        ).toObservable()
                    .flatMap {
                        val characteristic = gatt.source.findCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9F"))
                        gatt.enableNotification(characteristic!!)
                            .flatMapPublisher { gatt.listenChanges(characteristic) }
                            .toObservable()
                    }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    binding.espButton1.isChecked = it[0].toInt() == 0
                },
                {
                    val message =
                        when (it) {
                            is BluetoothIsTurnedOff -> "Bluetooth is turned off"
                            is DeviceDisconnected.CharacteristicReadDeviceDisconnected -> "Device disconnected while reading battery"
                            is CannotInitialize.CannotInitializeCharacteristicReading -> "Failed to initialize battery read"
                            is IOFailed.CharacteristicReadingFailed -> "Failed to read the battery"
                            else -> "Error occurred: $it"
                        }
                    AlertDialog.Builder(this).setMessage(message).show()
                })
            .disposeOnState(ActivityState.DESTROY, this)
        //button 2 state notification
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> states.map { state.gatt }
                }
            }
            .switchMap { gatt ->
                (
                        if (gatt.source.services.isEmpty()) gatt.discoverServices()
                        else  Maybe.just(gatt.source.services)
                        ).toObservable()
                    .flatMap {
                        val characteristic =
                            gatt.source.findCharacteristic(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9F"))
                        gatt.enableNotification(characteristic!!)
                            .flatMapPublisher { gatt.listenChanges(characteristic) }
                            .toObservable()
                    }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    binding.espButton2.isChecked = it[0].toInt() == 0
                },
                {
                    val message =
                        when (it) {
                            is BluetoothIsTurnedOff -> "Bluetooth is turned off"
                            is DeviceDisconnected.CharacteristicReadDeviceDisconnected -> "Device disconnected while reading battery"
                            is CannotInitialize.CannotInitializeCharacteristicReading -> "Failed to initialize battery read"
                            is IOFailed.CharacteristicReadingFailed -> "Failed to read the battery"
                            else -> "Error occurred: $it"
                        }
                    AlertDialog.Builder(this).setMessage(message).show()
                })
            .disposeOnState(ActivityState.DESTROY, this)
        //test button, read request
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> binding.testButton.clicks().map { state.gatt }
                }
            }
            .switchMapMaybe { gatt ->
                currTime = System.currentTimeMillis()
                (
                        if (gatt.source.services.isEmpty()) gatt.discoverServices()
                        else Maybe.just(gatt.source.services)
                        )
                    .flatMap {
                        gatt.read(gatt.source.findCharacteristic(UUID.fromString("6E400005-B5A3-F393-E0A9-E50E24DCCA9F"))!!)
                    }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe{
                val timed = System.currentTimeMillis() - currTime
                var message = it.decodeToString()
                message = message + "\n" + timed.toString()+"ms"

                AlertDialog.Builder(this).setMessage(message).show()
            }
            .disposeOnState(ActivityState.DESTROY, this)
    }

    override fun onDestroy() {
        (states.value as? States.Connected)?.gatt?.source?.disconnect()
        super.onDestroy()
    }

    private sealed class States {
        object Connecting : States()
        class Connected(val gatt: RxBluetoothGatt) : States()
    }

    companion object {
        fun intent(context: Context, device: BluetoothDevice): Intent =
            Intent(context, DeviceActivity::class.java)
                .putExtra(DEVICE_EXTRA, device)

        private const val DEVICE_EXTRA = "DEVICE_EXTRA"
    }
}
