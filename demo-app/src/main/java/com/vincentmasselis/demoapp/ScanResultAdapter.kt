package com.vincentmasselis.demoapp

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import no.nordicsemi.android.support.v18.scanner.ScanResult

class ScanResultAdapter(private val inflater: LayoutInflater, private val recyclerView: RecyclerView) : RecyclerView.Adapter<ScanResultViewHolder>(), View.OnClickListener {

    private val scanResults = mutableListOf<BluetoothDevice>()

    fun append(scanResult: BluetoothDevice) {
        scanResults
            .indexOfFirst { it.address == scanResult.address }
            .takeIf { it != -1 }
            ?.let { index ->
                scanResults[index] = scanResult
                notifyItemChanged(index)
                return
            }

        scanResults += scanResult
        notifyItemInserted(scanResults.size - 1)
    }

    fun append(connectedDevices: List<BluetoothDevice>) {
        for (device in connectedDevices) {
            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) {
                append(device)
            }
        }
    }

    override fun getItemCount(): Int = scanResults.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder = ScanResultViewHolder(inflater.inflate(R.layout.cell_scan_result, parent, false))

    override fun onViewAttachedToWindow(holder: ScanResultViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.setOnClickListener(this)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        val scanResult = scanResults[position]
        holder.bind(scanResult.name ?: "N/A", scanResult.address)
    }

    override fun onViewDetachedFromWindow(holder: ScanResultViewHolder) {
        holder.itemView.setOnClickListener(null)
        super.onViewDetachedFromWindow(holder)
    }

    override fun onClick(view: View) {
        recyclerView.context.startActivity(DeviceActivity.intent(recyclerView.context, scanResults[recyclerView.getChildAdapterPosition(view)]))
    }
}