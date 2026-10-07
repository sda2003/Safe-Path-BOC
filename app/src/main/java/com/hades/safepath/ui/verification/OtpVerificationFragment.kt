package com.hades.safepath.ui.verification

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hades.safepath.R
import com.hades.safepath.auth.VerificationChannel
import com.hades.safepath.databinding.FragmentOtpVerificationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OtpVerificationFragment : Fragment() {

    private var _binding: FragmentOtpVerificationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VerificationViewModel by viewModels({ requireActivity() })
    private val destination: String by lazy {
        arguments?.getString("destination")
            ?: arguments?.getString("phone").orEmpty()
    }
    private val channel: VerificationChannel by lazy {
        VerificationChannel.fromName(arguments?.getString("channel"))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSubtitle.text = getString(R.string.verify_otp_subtitle, destination)

        binding.btnVerify.setOnClickListener {
            val code = binding.etOtp.text?.toString().orEmpty().trim()
            if (code.length < 4) {
                showError(
                    if (channel == VerificationChannel.EMAIL) {
                        getString(R.string.error_invalid_email_otp)
                    } else {
                        getString(R.string.error_invalid_otp)
                    }
                )
                return@setOnClickListener
            }
            hideError()
            viewModel.verifyOtp(code, destination, channel)
        }

        binding.btnResend.setOnClickListener {
            viewModel.resendOtp(destination, channel)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is VerificationUiState.Loading -> {
                        binding.progress.isVisible = true
                        binding.btnVerify.isEnabled = false
                        binding.btnResend.isEnabled = false
                        hideError()
                    }
                    is VerificationUiState.Verified -> {
                        binding.progress.isVisible = false
                        binding.btnVerify.isEnabled = true
                        binding.btnResend.isEnabled = true
                        if (findNavController().currentDestination?.id == R.id.otpVerificationFragment) {
                            findNavController().navigate(R.id.action_otp_to_verification_success)
                        }
                    }
                    is VerificationUiState.Error -> {
                        binding.progress.isVisible = false
                        binding.btnVerify.isEnabled = true
                        binding.btnResend.isEnabled = true
                        showError(state.message)
                    }
                    else -> {
                        binding.progress.isVisible = false
                        binding.btnVerify.isEnabled = true
                        binding.btnResend.isEnabled = true
                    }
                }
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.isVisible = true
    }

    private fun hideError() {
        binding.tvError.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
