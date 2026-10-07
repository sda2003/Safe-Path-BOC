package com.hades.safepath.ui.survival

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hades.safepath.R
import com.hades.safepath.data.model.SurvivalTopic
import com.hades.safepath.databinding.FragmentSurvivalLibraryBinding
import com.hades.safepath.databinding.ItemSurvivalTopicBinding
import dagger.hilt.android.AndroidEntryPoint
import com.bumptech.glide.Glide

@AndroidEntryPoint
class SurvivalLibraryFragment : Fragment() {

    private var _binding: FragmentSurvivalLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SurvivalViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurvivalLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SurvivalAdapter { topic ->
            findNavController().navigate(
                R.id.action_survival_to_detail,
                bundleOf("topicId" to topic.id)
            )
        }

        binding.rvSurvivalTopics.layoutManager = GridLayoutManager(context, 2)
        binding.rvSurvivalTopics.adapter = adapter

        viewModel.topics.observe(viewLifecycleOwner) { topics ->
            adapter.submitList(topics)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class SurvivalAdapter(private val onItemClick: (SurvivalTopic) -> Unit) :
    androidx.recyclerview.widget.ListAdapter<SurvivalTopic, SurvivalAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemSurvivalTopicBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSurvivalTopicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val topic = getItem(position)
        holder.binding.tvTopicTitle.text = topic.title
        holder.binding.tvArticleCount.text = "${topic.steps.size} steps"
        
        Glide.with(holder.itemView.context)
            .load(topic.iconRes)
            .into(holder.binding.ivTopicBg)

        holder.itemView.setOnClickListener {
            onItemClick(topic)
        }
    }

    companion object {
        private val DiffCallback = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<SurvivalTopic>() {
            override fun areItemsTheSame(oldItem: SurvivalTopic, newItem: SurvivalTopic): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SurvivalTopic, newItem: SurvivalTopic): Boolean =
                oldItem == newItem
        }
    }
}
