package com.example.t4.ble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.t4.R

class BleDeviceAdapter(private val onClick: (BleDevice) -> Unit) :
    RecyclerView.Adapter<BleDeviceAdapter.ViewHolder>() {

    private val devices = mutableListOf<BleDevice>()

    fun updateList(newDevices: List<BleDevice>) {
        val oldSize = devices.size
        devices.clear()
        // 去重
        val uniqueDevices = newDevices.distinctBy { it.address }
        // 排序：E104设备优先（排在最前面），其他设备按照原顺序
        val sortedDevices = uniqueDevices.sortedBy { device ->
            if (device.name?.contains("E104") == true) 0 else 1
        }
        devices.addAll(sortedDevices)
        notifyItemRangeRemoved(0, oldSize)
        notifyItemRangeInserted(0, devices.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = devices.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(device: BleDevice) {
            itemView.apply {
                findViewById<TextView>(R.id.tvDeviceName).text = device.name ?: context.getString(R.string.unknown_device)
                findViewById<TextView>(R.id.tvMacAddress).text = device.address
                findViewById<TextView>(R.id.tvRssi).text = context.getString(R.string.rssi_format, device.rssi)
            }
        }
    }
}