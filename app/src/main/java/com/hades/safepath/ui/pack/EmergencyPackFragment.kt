package com.hades.safepath.ui.pack

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hades.safepath.R
import com.hades.safepath.ai.PersonalizedPackRepository
import com.hades.safepath.data.model.PackItem
import com.hades.safepath.databinding.FragmentEmergencyPackBinding
import com.hades.safepath.util.NetworkMonitor
import com.hades.safepath.utils.PackData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EmergencyPackFragment : Fragment() {

    @Inject lateinit var personalizedPackRepository: PersonalizedPackRepository
    @Inject lateinit var networkMonitor: NetworkMonitor

    private var _binding: FragmentEmergencyPackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PackViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyPackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("safepath_prefs", Context.MODE_PRIVATE)
        val disasterType = prefs.getString("last_alert_type", "general") ?: "general"

        val bannerText = when (disasterType.lowercase()) {
            "flood" -> "⚠ FLOOD ADVISORY"
            "tsunami" -> "⚠ TSUNAMI ADVISORY"
            "earthquake" -> "⚠ EARTHQUAKE ADVISORY"
            "fire" -> "⚠ FIRE ADVISORY"
            "landslide" -> "⚠ LANDSLIDE ADVISORY"
            else -> "⚠ EMERGENCY ADVISORY"
        }
        view.findViewById<android.widget.TextView>(R.id.tvAdvisoryBanner)?.text = bannerText

        val items = PackData.getItemsForDisaster(disasterType)
        viewModel.loadItems(items)

        val adapter = PackAdapter { item ->
            viewModel.toggleItem(item)
        }

        binding.rvPackItems.layoutManager = LinearLayoutManager(context)
        binding.rvPackItems.adapter = adapter

        viewModel.packItems.observe(viewLifecycleOwner) { packItems ->
            updateProgress(packItems)
            adapter.submitList(adapter.buildList(packItems))
        }

        lifecycleScope.launch {
            if (personalizedPackRepository.hasPersonalizedPack()) {
                binding.btnPersonalize.text = getString(R.string.view_personalized_pack)
            }
        }

        binding.btnPersonalize.setOnClickListener {
            lifecycleScope.launch {
                if (personalizedPackRepository.hasPersonalizedPack()) {
                    findNavController().navigate(R.id.action_pack_to_personalized_view)
                } else if (networkMonitor.isOnline()) {
                    findNavController().navigate(R.id.action_pack_to_personalize)
                } else {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.personalize_requires_internet),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.btnPackDone.setOnClickListener {
            val checkedCount = viewModel.getCheckedCount()
            val criticalCount = viewModel.getCriticalCount()

            if (checkedCount < criticalCount) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Not all critical items checked")
                    .setMessage("You still have ${criticalCount - checkedCount} critical item(s) unchecked. Are you sure you're ready?")
                    .setPositiveButton("I'm Ready") { _, _ ->
                        prefs.edit().putBoolean("pack_ready", true).apply()
                        showReadyConfirmation()
                    }
                    .setNegativeButton("Go Back", null)
                    .show()
            } else {
                prefs.edit().putBoolean("pack_ready", true).apply()
                showReadyConfirmation()
            }
        }
    }

    private fun updateProgress(packItems: List<PackItem>) {
        val critical = packItems.filter { it.section.equals("critical", ignoreCase = true) }
        val checked = critical.count { it.isChecked }
        binding.tvPackProgress.text = "$checked of ${critical.size} critical items checked"

        val progress = if (critical.isNotEmpty()) {
            (checked.toFloat() / critical.size * 100).toInt()
        } else {
            0
        }

        ObjectAnimator.ofInt(
            binding.progressBarPack,
            "progress",
            binding.progressBarPack.progress,
            progress
        ).setDuration(400).start()
    }

    private fun showReadyConfirmation() {
        Snackbar.make(binding.root, "✅ You are ready. Stay safe!", Snackbar.LENGTH_LONG)
            .setBackgroundTint(Color.parseColor("#1A8A4A"))
            .setTextColor(Color.WHITE)
            .show()
        binding.root.postDelayed({
            findNavController().navigate(R.id.evacuationMapFragment)
        }, 1500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PackAdapter(
    private val onItemToggled: (PackItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Any, RecyclerView.ViewHolder>(PackDiffCallback()) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    fun buildList(items: List<PackItem>): List<Any> {
        val result = mutableListOf<Any>()
        val sections = listOf("critical", "important", "optional")
        sections.forEach { section ->
            val sectionItems = items.filter { it.section.equals(section, ignoreCase = true) }
            if (sectionItems.isNotEmpty()) {
                result.add(section.uppercase())
                result.addAll(sectionItems)
            }
        }
        return result
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position) is String) TYPE_HEADER else TYPE_ITEM

    inner class HeaderViewHolder(val binding: com.hades.safepath.databinding.ItemPackHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class ItemViewHolder(val binding: com.hades.safepath.databinding.ItemPackItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(
                com.hades.safepath.databinding.ItemPackHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        } else {
            ItemViewHolder(
                com.hades.safepath.databinding.ItemPackItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                val section = getItem(position) as String
                holder.binding.tvSectionHeader.text = section
                holder.binding.tvSectionHeader.setTextColor(
                    when (section) {
                        "CRITICAL" -> Color.parseColor("#D72B2B")
                        "IMPORTANT" -> Color.parseColor("#F0A500")
                        else -> Color.parseColor("#6B7280")
                    }
                )
            }
            is ItemViewHolder -> {
                val item = getItem(position) as PackItem
                holder.binding.tvItemLabel.text = item.label
                holder.binding.tvSectionBadge.text = item.section.uppercase()
                holder.binding.tvSectionBadge.setTextColor(
                    when (item.section.lowercase()) {
                        "critical" -> Color.parseColor("#D72B2B")
                        "important" -> Color.parseColor("#F0A500")
                        else -> Color.parseColor("#6B7280")
                    }
                )
                holder.binding.ivCheckbox.setImageResource(
                    if (item.isChecked) R.drawable.ic_checkbox_checked_green
                    else R.drawable.ic_checkbox_empty
                )
                holder.binding.root.setOnClickListener {
                    onItemToggled(item)
                }
                holder.binding.ivCheckbox.setOnClickListener {
                    onItemToggled(item)
                }
            }
        }
    }
}

class PackDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Any>() {
    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is String && newItem is String) return oldItem == newItem
        if (oldItem is PackItem && newItem is PackItem) return oldItem.id == newItem.id
        return false
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem is PackItem && newItem is PackItem) {
            return oldItem.id == newItem.id &&
                oldItem.label == newItem.label &&
                oldItem.section == newItem.section &&
                oldItem.isChecked == newItem.isChecked
        }
        return oldItem == newItem
    }
}
