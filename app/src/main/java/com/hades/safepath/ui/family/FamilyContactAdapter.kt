package com.hades.safepath.ui.family

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hades.safepath.data.entity.FamilyContactEntity
import com.hades.safepath.databinding.ItemFamilyContactBinding

class FamilyContactAdapter(
    private val onRemove: (FamilyContactEntity) -> Unit
) : ListAdapter<FamilyContactEntity, FamilyContactAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFamilyContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemFamilyContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: FamilyContactEntity) {
            binding.tvContactName.text = contact.contactName
            binding.tvContactPhone.text = contact.contactPhone
            binding.btnRemove.setOnClickListener { onRemove(contact) }
        }
    }

    companion object Diff : DiffUtil.ItemCallback<FamilyContactEntity>() {
        override fun areItemsTheSame(a: FamilyContactEntity, b: FamilyContactEntity) = a.id == b.id
        override fun areContentsTheSame(a: FamilyContactEntity, b: FamilyContactEntity) = a == b
    }
}
