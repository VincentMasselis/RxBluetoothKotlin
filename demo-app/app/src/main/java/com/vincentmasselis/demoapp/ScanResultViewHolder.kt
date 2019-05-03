package com.vincentmasselis.demoapp

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.cell_scan_result.*

class ScanResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), LayoutContainer {

    override val containerView: View? get() = itemView

    fun bind(name: String, address: String) {
        scan_result_name.text = name
        scan_result_address.text = address
    }
}