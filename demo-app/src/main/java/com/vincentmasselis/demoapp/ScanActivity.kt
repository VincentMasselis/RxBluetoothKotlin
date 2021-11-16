package com.vincentmasselis.demoapp

import android.Manifest.permission.*
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding4.view.clicks
import com.masselis.rxbluetoothkotlin.*
import com.vincentmasselis.demoapp.databinding.ActivityScanBinding
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class ScanActivity : AppCompatActivity() {

    private var currentState = BehaviorSubject.createDefault<States>(States.NotScanning)
    private lateinit var binding: ActivityScanBinding
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startScan()
        }

    private val forResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startScan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // To display the bluetooth device name I need this permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED
        ) permissionLauncher.launch(BLUETOOTH_CONNECT)

        currentState
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                @Suppress("UNUSED_VARIABLE") val ignoreMe = when (it) {
                    States.NotScanning -> {
                        binding.notScanningGroup.visibility = View.VISIBLE
                        binding.startScanGroup.visibility = View.GONE
                        binding.scanningGroup.visibility = View.GONE
                    }
                    States.StartingScan -> {
                        binding.notScanningGroup.visibility = View.GONE
                        binding.startScanGroup.visibility = View.VISIBLE
                        binding.scanningGroup.visibility = View.GONE
                    }
                    States.Scanning -> {
                        binding.notScanningGroup.visibility = View.GONE
                        binding.startScanGroup.visibility = View.GONE
                        binding.scanningGroup.visibility = View.VISIBLE
                    }
                }
            }
            .disposeOnState(ActivityState.DESTROY, this)

        binding.scanRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scanRecyclerView.adapter =
            ScanResultAdapter(layoutInflater, binding.scanRecyclerView)

        binding.startScanButton.clicks()
            .subscribe { startScan() }
            .disposeOnState(ActivityState.DESTROY, this)

        binding.stopScanButton.clicks()
            .subscribe {
                scanDisp?.dispose()
                currentState.onNext(States.NotScanning)
            }
            .disposeOnState(ActivityState.DESTROY, this)
    }

    override fun onDestroy() {
        binding.scanRecyclerView.adapter = null
        super.onDestroy()
    }

    private var scanDisp: Disposable? = null
    private fun startScan() {
        currentState.onNext(States.StartingScan)
        scanDisp = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan(flushEvery = 1L to TimeUnit.SECONDS)
            .doOnNext { currentState.onNext(States.Scanning) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                val connecteddevices = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
                    .getConnectedDevices(BluetoothProfile.GATT_SERVER)
                (binding.scanRecyclerView.adapter as ScanResultAdapter).append(connecteddevices)
            }
            .subscribe({
                (binding.scanRecyclerView.adapter as ScanResultAdapter).append(it.device)
            }, {
                currentState.onNext(States.NotScanning)
                when (it) {
                    is DeviceDoesNotSupportBluetooth -> AlertDialog
                        .Builder(this)
                        .setMessage("The current device doesn't support bluetooth le")
                        .show()
                    is BluetoothIsTurnedOff ->
                        AlertDialog.Builder(this).setMessage("Bluetooth is turned off").show()
                    is NeedLocationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        permissionLauncher.launch(ACCESS_FINE_LOCATION)
                    is NeedBluetoothScanPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        permissionLauncher.launch(BLUETOOTH_SCAN)
                    is LocationServiceDisabled ->
                        forResultLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    else -> AlertDialog.Builder(this).setMessage("Error occurred: $it").show()
                }
            })
            .disposeOnState(ActivityState.PAUSE, this)
    }

    private sealed class States {
        object NotScanning : States()
        object StartingScan : States()
        object Scanning : States()
    }
}
