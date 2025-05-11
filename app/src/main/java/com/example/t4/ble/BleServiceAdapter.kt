package com.example.t4.ble

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.t4.R

class BleServiceAdapter(
    private val services: MutableList<BleGattService> = mutableListOf(),
    private val characteristicListener: BleCharacteristicListener
) : RecyclerView.Adapter<BleServiceAdapter.ServiceViewHolder>() {
    
    interface BleCharacteristicListener {
        fun onReadCharacteristic(serviceUuid: String, characteristicUuid: String)
        fun onWriteCharacteristic(serviceUuid: String, characteristicUuid: String)
        fun onNotifyCharacteristic(serviceUuid: String, characteristicUuid: String, enable: Boolean)
    }
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateServices(newServices: List<BleGattService>) {
        services.clear()
        services.addAll(newServices)
        notifyDataSetChanged()
    }
    
    fun updateCharacteristicValue(serviceUuid: String, characteristicUuid: String, value: ByteArray) {
        for (i in services.indices) {
            val service = services[i]
            if (service.uuid.toString() == serviceUuid) {
                for (j in service.characteristics.indices) {
                    val characteristic = service.characteristics[j]
                    if (characteristic.uuid.toString() == characteristicUuid) {
                        characteristic.value = value
                        notifyItemChanged(i)
                        break
                    }
                }
                break
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_service, parent, false)
        return ServiceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = services[position]
        holder.bind(service)
    }
    
    override fun getItemCount(): Int = services.size
    
    inner class ServiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val serviceName: TextView = itemView.findViewById(R.id.serviceName)
        private val serviceUuid: TextView = itemView.findViewById(R.id.serviceUuid)
        private val characteristicsList: RecyclerView = itemView.findViewById(R.id.characteristicsList)
        
        @SuppressLint("SetTextI18n")
        fun bind(service: BleGattService) {
            serviceName.text = "${service.name} (${service.typeString})"
            serviceUuid.text = service.uuid.toString()
            
            // 设置特征列表
            characteristicsList.layoutManager = LinearLayoutManager(itemView.context)
            val adapter = BleCharacteristicAdapter(service.uuid.toString(), service.characteristics, characteristicListener)
            characteristicsList.adapter = adapter
        }
    }
}