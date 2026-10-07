package com.hades.safepath.ui.actions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentImmediateActionsBinding
import com.hades.safepath.databinding.ItemActionBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImmediateActionsFragment : Fragment() {

    private var _binding: FragmentImmediateActionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ActionsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImmediateActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ActionsAdapter { item, checkbox ->
            item.isCompleted = !item.isCompleted
            updateCheckboxUI(checkbox, item.isCompleted)
            viewModel.updateAction(item)
        }

        binding.rvActions.layoutManager = LinearLayoutManager(context)
        binding.rvActions.adapter = adapter

        viewModel.actions.observe(viewLifecycleOwner) { actions ->
            adapter.submitList(actions)
        }

        binding.btnReachedShelter.setOnClickListener {
            viewModel.markSafe()
            findNavController().navigate(R.id.action_actions_to_home)
        }
    }

    private fun updateCheckboxUI(view: ImageView, checked: Boolean) {
        view.setImageResource(
            if (checked) R.drawable.ic_checkbox_checked_green
            else R.drawable.ic_checkbox_unchecked
        )
        // Scale bounce animation
        view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(100) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ActionsAdapter(private val onItemClick: (ActionItem, ImageView) -> Unit) :
    androidx.recyclerview.widget.ListAdapter<ActionItem, ActionsAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemActionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.tvNumber.text = item.id.toString()
        holder.binding.tvActionText.text = item.text
        
        holder.binding.ivCheckbox.setImageResource(
            if (item.isCompleted) R.drawable.ic_checkbox_checked_green
            else R.drawable.ic_checkbox_unchecked
        )

        holder.binding.ivCheckbox.setOnClickListener {
            onItemClick(item, holder.binding.ivCheckbox)
        }
    }

    companion object {
        private val DiffCallback = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ActionItem>() {
            override fun areItemsTheSame(oldItem: ActionItem, newItem: ActionItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ActionItem, newItem: ActionItem): Boolean =
                oldItem == newItem
        }
    }
}
