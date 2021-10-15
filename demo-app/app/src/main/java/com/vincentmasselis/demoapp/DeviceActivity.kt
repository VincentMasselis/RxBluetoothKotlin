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

        states
            .switchMap { state ->
                when (state) {
                    States.Connecting -> Observable.empty()
                    is States.Connected -> read_battery_button.clicks().map { state.gatt }
                }
            }
            .subscribe { gatt ->
                battery_text_view.text = ""
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
                        { battery_text_view.text = "$it%" },
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
