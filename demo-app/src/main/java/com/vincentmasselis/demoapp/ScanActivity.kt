package com.vincentmasselis.demoapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding4.view.clicks
import com.vincentmasselis.demoapp.databinding.ActivityScanBinding
import com.vincentmasselis.rxbluetoothkotlin.*
import com.vincentmasselis.rxuikotlin.disposeOnState
import com.vincentmasselis.rxuikotlin.utils.ActivityState
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit

class ScanActivity : AppCompatActivity() {

    private var currentState = BehaviorSubject.createDefault<States>(States.NotScanning)
    private lateinit var binding: ActivityScanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

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


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_ENABLE_LOCATION -> if (resultCode == Activity.RESULT_OK) startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CODE_FINE_LOCATION -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) startScan()
        }
    }

    private var scanDisp: Disposable? = null
    private fun startScan() {
        currentState.onNext(States.StartingScan)
        scanDisp = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
            .rxScan(flushEvery = 1L to TimeUnit.SECONDS)
            .doOnNext { currentState.onNext(States.Scanning) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                (binding.scanRecyclerView.adapter as ScanResultAdapter).append(it)
            }, {
                currentState.onNext(States.NotScanning)
                when (it) {
                    is DeviceDoesNotSupportBluetooth -> AlertDialog.Builder(this)
                        .setMessage("The current device doesn't support bluetooth le").show()
                    is NeedLocationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        requestPermissions(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            PERMISSION_CODE_FINE_LOCATION
                        )
                    is BluetoothIsTurnedOff -> AlertDialog.Builder(this)
                        .setMessage("Bluetooth is turned off").show()
                    is LocationServiceDisabled -> startActivityForResult(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        REQUEST_CODE_ENABLE_LOCATION
                    )
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

    companion object {
        private const val PERMISSION_CODE_FINE_LOCATION = 1
        private const val REQUEST_CODE_ENABLE_LOCATION = 2
    }
}
