package com.hades.safepath.ui.survival

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.card.MaterialCardView
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentSurvivalDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SurvivalDetailFragment : Fragment() {

    private var _binding: FragmentSurvivalDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SurvivalViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurvivalDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val topicId = arguments?.getString("topicId") ?: return

        viewModel.topics.observe(viewLifecycleOwner) { topics ->
            val topic = topics.find { it.id == topicId } ?: return@observe
            
            binding.tvTopicTitle.text = topic.title
            binding.tvTopicSummary.text = topic.summary

            // Clear previous steps if re-rendering
            if (binding.llSteps.childCount > 2) {
                binding.llSteps.removeViews(2, binding.llSteps.childCount - 2)
            }

            // Map of step index → isCompleted
            val stepChecked = mutableMapOf<Int, Boolean>()

            topic.steps.forEachIndexed { index, step ->
                val stepView = layoutInflater.inflate(R.layout.item_survival_step, binding.llSteps, false)
                val tvNumber = stepView.findViewById<TextView>(R.id.tvStepNumber)
                val tvText   = stepView.findViewById<TextView>(R.id.tvStepText)
                val ivCheck  = stepView.findViewById<ImageView>(R.id.ivStepCheck)
                val card     = stepView.findViewById<MaterialCardView>(R.id.cardStep)

                tvNumber.text = (index + 1).toString()
                tvText.text = step
                stepChecked[index] = false

                ivCheck.setOnClickListener {
                    val nowChecked = !(stepChecked[index] ?: false)
                    stepChecked[index] = nowChecked

                    if (nowChecked) {
                        ivCheck.setImageResource(R.drawable.ic_checkbox_checked_green)
                        card.setCardBackgroundColor(Color.parseColor("#0D1A0D")) // very dark green tint
                        card.strokeColor = Color.parseColor("#22C55E")
                        tvText.paintFlags = tvText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                        tvText.alpha = 0.5f
                        // Bounce animation
                        ivCheck.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100)
                            .withEndAction { ivCheck.animate().scaleX(1f).scaleY(1f).setDuration(100) }
                    } else {
                        ivCheck.setImageResource(R.drawable.ic_checkbox_empty)
                        card.setCardBackgroundColor(Color.parseColor("#1C1C1C"))
                        card.strokeColor = Color.parseColor("#2A2A2A")
                        tvText.paintFlags = tvText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                        tvText.alpha = 1f
                    }
                }

                binding.llSteps.addView(stepView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
