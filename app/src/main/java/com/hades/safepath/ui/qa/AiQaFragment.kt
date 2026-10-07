package com.hades.safepath.ui.qa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.hades.safepath.R
import com.hades.safepath.data.entity.AiQaCacheEntity
import com.hades.safepath.databinding.FragmentAiQaBinding
import com.hades.safepath.databinding.ItemAiQaBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AiQaFragment : Fragment() {

    private var _binding: FragmentAiQaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AiQaViewModel by viewModels()
    private val adapter = AiQaHistoryAdapter { item ->
        binding.tvAnswerLabel.isVisible = true
        binding.cardAnswer.isVisible = true
        binding.tvTopic.text = item.topic.uppercase()
        binding.tvQuestionEcho.text = item.question
        binding.tvAnswer.text = item.answer
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiQaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter

        viewModel.suggestions.forEach { suggestion ->
            val chip = Chip(requireContext()).apply {
                text = suggestion
                isCheckable = false
                setOnClickListener {
                    binding.etQuestion.setText(suggestion)
                    viewModel.ask(suggestion)
                }
            }
            binding.chipSuggestions.addView(chip)
        }

        binding.btnAsk.setOnClickListener {
            viewModel.ask(binding.etQuestion.text?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.history.collect { adapter.submitList(it) }
                }
                launch {
                    viewModel.isOnline.collect { online ->
                        binding.tvBanner.text = if (online) {
                            getString(R.string.ai_qa_banner_online)
                        } else {
                            getString(R.string.ai_qa_banner_offline)
                        }
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        binding.progress.isVisible = loading
                        binding.btnAsk.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.latestAnswer.collect { success ->
                        if (success == null) return@collect
                        binding.tvAnswerLabel.isVisible = true
                        binding.cardAnswer.isVisible = true
                        binding.tvTopic.text = success.topic.uppercase()
                        binding.tvQuestionEcho.text = success.question
                        binding.tvAnswer.text = success.answer
                    }
                }
                launch {
                    viewModel.events.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class AiQaHistoryAdapter(
    private val onClick: (AiQaCacheEntity) -> Unit
) : ListAdapter<AiQaCacheEntity, AiQaHistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAiQaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemAiQaBinding,
        private val onClick: (AiQaCacheEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AiQaCacheEntity) {
            binding.tvTopic.text = item.topic.uppercase()
            binding.tvQuestion.text = item.question
            binding.tvAnswerPreview.text = item.answer
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AiQaCacheEntity>() {
            override fun areItemsTheSame(a: AiQaCacheEntity, b: AiQaCacheEntity) = a.id == b.id
            override fun areContentsTheSame(a: AiQaCacheEntity, b: AiQaCacheEntity) = a == b
        }
    }
}
