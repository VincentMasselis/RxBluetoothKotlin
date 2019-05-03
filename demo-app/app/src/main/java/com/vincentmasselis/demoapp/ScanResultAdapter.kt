package com.vincentmasselis.demoapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import no.nordicsemi.android.support.v18.scanner.ScanResult

class ScanResultAdapter(private val inflater: LayoutInflater) : RecyclerView.Adapter<ScanResultViewHolder>() {

    private val scanResults = mutableListOf<ScanResult>()

    fun append(scanResult: ScanResult) {
        scanResults
            .indexOfFirst { it.device.address == scanResult.device.address }
            .takeIf { it != -1 }
            ?.let { index ->
                scanResults[index] = scanResult
                notifyItemChanged(index)
                return
            }

        scanResults += scanResult
        notifyItemInserted(scanResults.size - 1)
    }

    override fun getItemCount(): Int = scanResults.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder = ScanResultViewHolder(inflater.inflate(R.layout.cell_scan_result, parent, false))

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        val scanResult = scanResults[position]
        holder.bind(scanResult.device.name ?: "N/A", scanResult.device.address)
    }
}