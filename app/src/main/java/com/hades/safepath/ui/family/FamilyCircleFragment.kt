package com.hades.safepath.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hades.safepath.databinding.FragmentFamilyCircleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FamilyCircleFragment : Fragment() {

    private var _binding: FragmentFamilyCircleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FamilyCircleViewModel by viewModels()
    private lateinit var adapter: FamilyContactAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFamilyCircleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FamilyContactAdapter { contact ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove contact?")
                .setMessage("${contact.contactName} will be removed from your Family Circle.")
                .setPositiveButton("Remove") { _, _ ->
                    viewModel.removeContact(contact.id)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnAddContact.setOnClickListener {
            val name = binding.etName.text?.toString().orEmpty()
            val phone = binding.etPhone.text?.toString().orEmpty()
            viewModel.addContact(name, phone)
        }

        binding.btnImSafe.setOnClickListener {
            viewModel.sendImSafe { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progress.isVisible = state.loading
                binding.tvTierInfo.text = getString(
                    com.hades.safepath.R.string.family_circle_tier_info,
                    state.tier.uppercase(),
                    state.contacts.size,
                    state.maxContacts
                )
                binding.tvStatusMessage.isVisible = !state.message.isNullOrBlank()
                binding.tvStatusMessage.text = state.message
                binding.tvError.isVisible = !state.error.isNullOrBlank()
                binding.tvError.text = state.error
                adapter.submitList(state.contacts)
                binding.btnAddContact.isEnabled = state.verified && state.contacts.size < state.maxContacts
                binding.btnImSafe.isEnabled = state.verified
            }
        }

        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
