package com.example.t4.ble

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.t4.R

class BleCharacteristicAdapter(
    private val serviceUuid: String,
    private val characteristics: List<BleGattCharacteristic>,
    private val listener: BleServiceAdapter.BleCharacteristicListener
) : RecyclerView.Adapter<BleCharacteristicAdapter.CharacteristicViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacteristicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ble_characteristic, parent, false)
        return CharacteristicViewHolder(view)
    }

    override fun onBindViewHolder(holder: CharacteristicViewHolder, position: Int) {
        val characteristic = characteristics[position]
        holder.bind(characteristic)
    }

    override fun getItemCount(): Int = characteristics.size

    inner class CharacteristicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val characteristicName: TextView = itemView.findViewById(R.id.characteristicName)
        private val characteristicUuid: TextView = itemView.findViewById(R.id.characteristicUuid)
        private val characteristicProperties: TextView = itemView.findViewById(R.id.characteristicProperties)
        private val characteristicActions: LinearLayout = itemView.findViewById(R.id.characteristicActions)
        private val readButton: Button = itemView.findViewById(R.id.readButton)
        private val writeButton: Button = itemView.findViewById(R.id.writeButton)
        private val notifyButton: Button = itemView.findViewById(R.id.notifyButton)
        private val characteristicValue: TextView = itemView.findViewById(R.id.characteristicValue)

        @SuppressLint("SetTextI18n")
        fun bind(characteristic: BleGattCharacteristic) {
            characteristicName.text = characteristic.name
            characteristicUuid.text = characteristic.uuid.toString()
            characteristicProperties.text = "属性: ${characteristic.propertiesString}"

            // 设置按钮可见性
            readButton.visibility = if (characteristic.isReadable) View.VISIBLE else View.GONE
            writeButton.visibility = if (characteristic.isWritable) View.VISIBLE else View.GONE
            notifyButton.visibility = if (characteristic.isNotifiable) View.VISIBLE else View.GONE

            // 如果有值，显示值
            if (characteristic.value != null) {
                characteristicValue.text = "值: ${characteristic.getValueAsString()}"
                characteristicValue.visibility = View.VISIBLE
            } else {
                characteristicValue.visibility = View.GONE
            }

            // 设置按钮点击事件
            readButton.setOnClickListener {
                listener.onReadCharacteristic(serviceUuid, characteristic.uuid.toString())
            }

            writeButton.setOnClickListener {
                listener.onWriteCharacteristic(serviceUuid, characteristic.uuid.toString())
            }

            var notifyEnabled = false
            notifyButton.setOnClickListener {
                notifyEnabled = !notifyEnabled
                notifyButton.text = if (notifyEnabled) "停止通知" else "通知"
                listener.onNotifyCharacteristic(serviceUuid, characteristic.uuid.toString(), notifyEnabled)
            }
        }
    }
}