package com.hades.safepath.ui.personalize

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentPersonalizePackBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PersonalizePackFragment : Fragment() {

    private var _binding: FragmentPersonalizePackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PersonalizePackViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizePackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cbKids.setOnCheckedChangeListener { _, checked ->
            binding.tilKidsAges.isVisible = checked
        }
        binding.cbPets.setOnCheckedChangeListener { _, checked ->
            binding.tilPetType.isVisible = checked
        }

        binding.btnGenerate.setOnClickListener {
            val size = binding.etHouseholdSize.text?.toString()?.toIntOrNull() ?: 0
            viewModel.submit(
                householdSize = size,
                hasKids = binding.cbKids.isChecked,
                kidsAges = binding.etKidsAges.text?.toString().orEmpty(),
                hasElderly = binding.cbElderly.isChecked,
                hasPets = binding.cbPets.isChecked,
                petType = binding.etPetType.text?.toString().orEmpty(),
                medicalConditions = binding.etMedical.text?.toString().orEmpty()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is PersonalizeUiState.Loading -> {
                        binding.progress.isVisible = true
                        binding.btnGenerate.isEnabled = false
                        binding.tvError.isVisible = false
                    }
                    is PersonalizeUiState.Generated -> {
                        binding.progress.isVisible = false
                        binding.btnGenerate.isEnabled = true
                        if (state.usedDefaultFallback) {
                            Toast.makeText(
                                requireContext(),
                                R.string.using_default_checklist,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        findNavController().navigate(R.id.action_personalize_to_pack_view)
                    }
                    is PersonalizeUiState.AlreadyGenerated -> {
                        binding.progress.isVisible = false
                        binding.btnGenerate.isEnabled = false
                        findNavController().navigate(R.id.action_personalize_to_pack_view)
                    }
                    is PersonalizeUiState.FormError -> {
                        binding.progress.isVisible = false
                        binding.btnGenerate.isEnabled = true
                        binding.tvError.isVisible = true
                        binding.tvError.text = state.message
                    }
                    else -> {
                        binding.progress.isVisible = false
                        binding.btnGenerate.isEnabled = true
                    }
                }
            }
        }

        viewModel.checkExisting()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
