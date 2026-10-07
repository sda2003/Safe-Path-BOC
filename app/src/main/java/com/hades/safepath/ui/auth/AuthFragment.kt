package com.hades.safepath.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayoutMediator
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentAuthBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AuthPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Sign In" else "Sign Up"
        }.attach()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { state ->
                binding.progressAuth.isVisible = state is AuthState.Loading
                when (state) {
                    is AuthState.Success -> {
                        if (findNavController().currentDestination?.id == R.id.authFragment) {
                            findNavController().navigate(R.id.action_global_homeFragment)
                        }
                    }
                    is AuthState.AwaitingEmailOtp -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.signup_check_email, state.email),
                            Toast.LENGTH_LONG
                        ).show()
                        if (findNavController().currentDestination?.id == R.id.authFragment) {
                            findNavController().navigate(
                                R.id.action_auth_to_check_email,
                                bundleOf("email" to state.email)
                            )
                        }
                    }
                    is AuthState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
