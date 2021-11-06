package com.vincentmasselis.demoapp



import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.jakewharton.rxbinding4.view.clicks
import com.vincentmasselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_device.*
import java.util.*

class DeviceActivity : AppCompatActivity() {

    private val device by lazy { intent.getParcelableExtra<BluetoothDevice>(DEVICE_EXTRA)!! }

    private val states = BehaviorSubject.createDefault<States>(States.Connecting)

    private var currTime = System.currentTimeMillis()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        states
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                @Suppress("UNUSED_VARIABLE") val ignoreMe = when (it) {
                    States.Connecting -> {
                        connecting_group.visibility = View.VISIBLE
                        connected_group.visibility = View.GONE
                    }
                    is States.Connected -> {
                        connecting_group.visibility = View.GONE
                        connected_group.visibility = View.VISIBLE
                    }
                }
            }
            .disposeOnState(ActivityState.DESTROY, this)

        states
            .filter { it is States.Connecting }
            .switchMapSingle { device.connectRxGatt() }
            .switchMapMaybe { gatt -> gatt.whenConnectionIsReady().map { gatt } }
            .doOnSubscribe { connecting_progress_bar.visibility = View.VISIBLE }
            .doFinally { connecting_progress_bar.visibility = View.INVISIBLE }
            .observeOn(AndroidSchedulers.mainThread())
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
        //on button. Send an "on" command to the ESP32 to turn the LED on, then read the LED state
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> onButton.clicks().map { state.gatt }
                }
            }
            .switchMapMaybe { gatt ->
                (
                    if (gatt.source.services.isEmpty()) gatt.discoverServices()
                    else Maybe.just(gatt.source.services)
                ).flatMap {
                        val myData = byteArrayOf((1).toByte())
                    gatt.write(gatt.source.findCharacteristic(UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9E"))!!,myData)
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
                    imageView.setImageDrawable(pic)
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
        //offbutton. Send an "off" command to the ESP32 to turn the LED off, then read the LED state
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> offButton.clicks().map { state.gatt }
                }
            }
            .switchMapMaybe { gatt ->
                (
                    if (gatt.source.services.isEmpty()) gatt.discoverServices()
                    else  Maybe.just(gatt.source.services)
                ).flatMap {
                    val myData = byteArrayOf((0).toByte())
                    gatt.write(gatt.source.findCharacteristic(UUID.fromString("6E400004-B5A3-F393-E0A9-E50E24DCCA9E"))!!,myData)
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
                    imageView.setImageDrawable(pic)
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
        //This is where we receive the button state of one of the ESP buttons in a notification
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
                val characteristic = gatt.source.findCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
                gatt.enableNotification(characteristic!!)
                    .flatMapPublisher { gatt.listenChanges(characteristic) }
                    .toObservable()
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    espButton1.isChecked = it[0].toInt() == 0
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
        //This is where we receive the button state of the other one of our ESP buttons via a notification
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
                    gatt.source.findCharacteristic(UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"))
                    gatt.enableNotification(characteristic!!)
                        .flatMapPublisher { gatt.listenChanges(characteristic) }
                        .toObservable()
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    espButton2.isChecked = it[0].toInt() == 0
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
        //Button for reading large data
        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> testButton.clicks().map { state.gatt }
                }
            }
            .switchMapMaybe { gatt ->
                currTime = System.currentTimeMillis()
                (
                    if (gatt.source.services.isEmpty()) gatt.discoverServices()
                    else Maybe.just(gatt.source.services)
                )
                .flatMap {
                    gatt.read(gatt.source.findCharacteristic(UUID.fromString("6E400005-B5A3-F393-E0A9-E50E24DCCA9E"))!!)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe{
                val timed = System.currentTimeMillis() - currTime
                var message = it.decodeToString()
                message = message + "\n" + timed.toString()

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

