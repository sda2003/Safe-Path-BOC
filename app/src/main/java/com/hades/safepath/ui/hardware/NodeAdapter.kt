package com.hades.safepath.ui.hardware

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hades.safepath.data.entity.HazardNodeEntity
import com.hades.safepath.databinding.ItemNodeBinding

class NodeAdapter : ListAdapter<HazardNodeEntity, NodeAdapter.NodeViewHolder>(NodeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NodeViewHolder(private val binding: ItemNodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: HazardNodeEntity) {
            binding.tvNodeId.text = "Node: ${node.nodeId}"
            binding.tvNodeStatus.text = "Risk: ${node.riskLevel}"
            
            val riskColor = when(node.riskLevel) {
                "CRITICAL" -> android.graphics.Color.RED
                "HIGH" -> android.graphics.Color.parseColor("#FF9800") // Orange
                "MODERATE" -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.GREEN
            }
            binding.tvNodeStatus.setTextColor(riskColor)
            
            val telemetry = """
                Battery: ${node.battery}% | Temp: ${node.temperature}°C
                Water Lvl: ${node.waterLevel}m | Rain: ${node.rainfall}mm
                Soil Moisture: ${node.soilMoisture}%
                Location: ${node.latitude}, ${node.longitude}
            """.trimIndent()
            
            binding.tvNodeTelemetry.text = telemetry
        }
    }
}

class NodeDiffCallback : DiffUtil.ItemCallback<HazardNodeEntity>() {
    override fun areItemsTheSame(oldItem: HazardNodeEntity, newItem: HazardNodeEntity): Boolean {
        return oldItem.nodeId == newItem.nodeId
    }

    override fun areContentsTheSame(oldItem: HazardNodeEntity, newItem: HazardNodeEntity): Boolean {
        return oldItem == newItem
    }
}
