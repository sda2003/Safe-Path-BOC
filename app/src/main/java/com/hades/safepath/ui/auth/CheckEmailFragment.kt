package com.hades.safepath.ui.auth

import android.content.Intent
import android.net.Uri
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
import com.hades.safepath.R
import com.hades.safepath.auth.VerificationChannel
import com.hades.safepath.databinding.FragmentCheckEmailBinding
import com.hades.safepath.ui.verification.VerificationUiState
import com.hades.safepath.ui.verification.VerificationViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CheckEmailFragment : Fragment() {

    private var _binding: FragmentCheckEmailBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels({ requireActivity() })
    private val verificationViewModel: VerificationViewModel by viewModels({ requireActivity() })

    private val email: String by lazy { arguments?.getString("email").orEmpty() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCheckEmailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvSubtitle.text = getString(R.string.check_email_subtitle, email)

        binding.btnVerifyCode.setOnClickListener {
            val code = binding.etOtp.text?.toString().orEmpty().trim()
            if (code.length < 4) {
                showError(getString(R.string.error_invalid_email_otp))
                return@setOnClickListener
            }
            hideError()
            verificationViewModel.verifyOtp(code, email, VerificationChannel.EMAIL)
        }

        binding.btnResend.setOnClickListener {
            authViewModel.resendSignupEmailOtp(email)
        }

        binding.btnOpenEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { startActivity(intent) }.onFailure {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:")))
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.open_email_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnGoSignIn.setOnClickListener {
            findNavController().navigate(R.id.action_check_email_to_auth)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            verificationViewModel.uiState.collect { state ->
                when (state) {
                    is VerificationUiState.Loading -> {
                        binding.progress.isVisible = true
                        binding.btnVerifyCode.isEnabled = false
                        hideError()
                    }
                    is VerificationUiState.Verified -> {
                        binding.progress.isVisible = false
                        binding.btnVerifyCode.isEnabled = true
                        if (findNavController().currentDestination?.id == R.id.checkEmailFragment) {
                            findNavController().navigate(R.id.action_check_email_to_home)
                        }
                    }
                    is VerificationUiState.Error -> {
                        binding.progress.isVisible = false
                        binding.btnVerifyCode.isEnabled = true
                        showError(state.message)
                    }
                    else -> {
                        binding.progress.isVisible = false
                        binding.btnVerifyCode.isEnabled = true
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            authViewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Loading -> binding.progress.isVisible = true
                    is AuthState.Success -> {
                        binding.progress.isVisible = false
                        if (findNavController().currentDestination?.id == R.id.checkEmailFragment) {
                            findNavController().navigate(R.id.action_check_email_to_home)
                        }
                    }
                    is AuthState.Error -> {
                        binding.progress.isVisible = false
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        authViewModel.clearError()
                    }
                    is AuthState.AwaitingEmailOtp -> {
                        binding.progress.isVisible = false
                        Toast.makeText(requireContext(), R.string.code_resent, Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
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
