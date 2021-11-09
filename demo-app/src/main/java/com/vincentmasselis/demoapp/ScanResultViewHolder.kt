package com.vincentmasselis.demoapp

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.vincentmasselis.demoapp.databinding.CellScanResultBinding

class ScanResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding by lazy { CellScanResultBinding.bind(itemView) }

    fun bind(name: String, address: String) {
        binding.scanResultName.text = name
        binding.scanResultAddress.text = address
    }
}