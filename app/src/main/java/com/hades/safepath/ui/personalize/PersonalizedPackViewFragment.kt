package com.hades.safepath.ui.personalize

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentPersonalizedPackViewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PersonalizedPackViewFragment : Fragment() {

    private var _binding: FragmentPersonalizedPackViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PersonalizePackViewModel by viewModels({ requireActivity() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizedPackViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadDisplay()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.display.collect { display ->
                binding.tvFallbackNotice.isVisible = display.usedDefaultFallback
                binding.llChecklist.removeAllViews()
                display.items.forEach { item ->
                    val tv = TextView(requireContext()).apply {
                        val label = when (item.priority) {
                            1 -> "● CRITICAL"
                            2 -> "● IMPORTANT"
                            else -> "● OPTIONAL"
                        }
                        text = "$label — ${item.itemNameEn}"
                        setTextColor(resources.getColor(R.color.text_primary, null))
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    binding.llChecklist.addView(tv)
                }
                binding.llTips.removeAllViews()
                display.tips.forEach { tip ->
                    val tv = TextView(requireContext()).apply {
                        text = "• ${tip.stepTextEn}"
                        setTextColor(resources.getColor(R.color.text_secondary, null))
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }
                    binding.llTips.addView(tv)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
