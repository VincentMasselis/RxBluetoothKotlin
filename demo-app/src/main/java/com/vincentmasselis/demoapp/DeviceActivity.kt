package com.vincentmasselis.demoapp

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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

        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> binding.readBatteryButton.clicks().map { state.gatt }
                }
            }
            .subscribe { gatt ->
                binding.batteryTextView.text = ""
                Maybe
                    .defer {
                        if (gatt.source.services.isEmpty()) gatt.discoverServices()
                        else Maybe.just(gatt.source.services)
                    }
                    // Battery characteristic
                    .flatMap { gatt.read(gatt.source.findCharacteristic(UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB"))!!) }
                    .map { it[0].toInt() }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { binding.batteryTextView.text = "$it%" },
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
