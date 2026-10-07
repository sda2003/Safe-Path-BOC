package com.hades.safepath.ui.verification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButtonToggleGroup
import com.hades.safepath.R
import com.hades.safepath.auth.EmailNormalizer
import com.hades.safepath.auth.PhoneNumberNormalizer
import com.hades.safepath.auth.VerificationChannel
import com.hades.safepath.auth.VerificationDestination
import com.hades.safepath.databinding.FragmentPhoneVerificationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneVerificationFragment : Fragment() {

    private var _binding: FragmentPhoneVerificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VerificationViewModel by viewModels({ requireActivity() })

    private var selectedChannel = VerificationChannel.PHONE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startOnEmail = arguments?.getBoolean("start_on_email") == true
        val prefillEmail = arguments?.getString("prefill_email").orEmpty()

        binding.toggleChannel.addOnButtonCheckedListener { _: MaterialButtonToggleGroup, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedChannel = when (checkedId) {
                R.id.btn_channel_email -> VerificationChannel.EMAIL
                else -> VerificationChannel.PHONE
            }
            updateChannelUi()
            hideError()
        }

        if (startOnEmail || prefillEmail.isNotBlank()) {
            binding.toggleChannel.check(R.id.btn_channel_email)
            if (prefillEmail.isNotBlank()) {
                binding.etEmail.setText(prefillEmail)
            }
        }

        binding.tvTryEmail.setOnClickListener {
            binding.toggleChannel.check(R.id.btn_channel_email)
            binding.etEmail.requestFocus()
            hideError()
        }

        binding.btnSendCode.setOnClickListener {
            sendCodeForCurrentChannel()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VerificationUiState.Loading -> {
                        binding.progress.isVisible = true
                        binding.btnSendCode.isEnabled = false
                        hideError()
                        binding.tvTryEmail.isVisible = false
                    }
                    is VerificationUiState.OtpSent -> {
                        binding.progress.isVisible = false
                        binding.btnSendCode.isEnabled = true
                        if (findNavController().currentDestination?.id == R.id.phoneVerificationFragment) {
                            findNavController().navigate(
                                R.id.action_phone_verification_to_otp_verification,
                                bundleOf(
                                    "destination" to state.destination,
                                    "channel" to state.channel.name
                                )
                            )
                        }
                    }
                    is VerificationUiState.Error -> {
                        binding.progress.isVisible = false
                        binding.btnSendCode.isEnabled = true
                        showError(state.message)
                        binding.tvTryEmail.isVisible =
                            state.suggestEmailFallback && selectedChannel == VerificationChannel.PHONE
                    }
                    else -> {
                        binding.progress.isVisible = false
                        binding.btnSendCode.isEnabled = true
                    }
                }
            }
        }
    }

    private fun sendCodeForCurrentChannel() {
        when (selectedChannel) {
            VerificationChannel.PHONE -> {
                val raw = binding.etPhone.text?.toString().orEmpty()
                val normalized = PhoneNumberNormalizer.normalize(raw)
                if (normalized == null) {
                    showError(getString(R.string.error_invalid_phone))
                    return
                }
                hideError()
                viewModel.requestOtp(VerificationDestination.Phone(normalized))
            }
            VerificationChannel.EMAIL -> {
                val raw = binding.etEmail.text?.toString().orEmpty()
                val normalized = EmailNormalizer.normalize(raw)
                if (normalized == null) {
                    showError(getString(R.string.error_invalid_email))
                    return
                }
                hideError()
                viewModel.requestOtp(VerificationDestination.Email(normalized))
            }
        }
    }

    private fun updateChannelUi() {
        val isPhone = selectedChannel == VerificationChannel.PHONE
        binding.tilPhone.isVisible = isPhone
        binding.tilEmail.isVisible = !isPhone
        binding.tvSubtitle.text = getString(
            if (isPhone) R.string.verify_phone_subtitle else R.string.verify_email_subtitle
        )
        binding.tvTryEmail.isVisible = false
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.isVisible = true
    }

    private fun hideError() {
        binding.tvError.isVisible = false
        binding.tvTryEmail.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
