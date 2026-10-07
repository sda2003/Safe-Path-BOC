package com.hades.safepath.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.hades.safepath.databinding.FragmentSignUpBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpFragment : Fragment() {

    private var _binding: FragmentSignUpBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val districts = listOf("Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo", "Galle", "Gampaha", "Hambantota", "Jaffna", "Kalutara", "Kandy", "Kegalle", "Kilinochchi", "Kurunegala", "Mannar", "Matale", "Matara", "Moneragala", "Mullaitivu", "Nuwara Eliya", "Polonnaruwa", "Puttalam", "Ratnapura", "Trincomalee", "Vavuniya")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, districts)
        binding.spinnerDistrict.adapter = adapter

        binding.btnSignUp.setOnClickListener {
            if (validateSignUp()) {
                val name = binding.etName.text.toString().trim()
                val email = binding.etEmail.text.toString().trim()
                val password = binding.etPassword.text.toString()
                val district = binding.spinnerDistrict.selectedItem.toString()
                viewModel.signUp(name, email, password, district)
            }
        }
    }

    private fun validateSignUp(): Boolean {
        var valid = true
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirm.text.toString()

        if (name.isEmpty()) { binding.tilName.error = "Name required"; valid = false } else { binding.tilName.error = null }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.tilEmail.error = "Invalid email"; valid = false } else { binding.tilEmail.error = null }
        if (password.length < 8) { binding.tilPassword.error = "Min 8 characters"; valid = false } else { binding.tilPassword.error = null }
        if (password != confirm) { binding.tilConfirm.error = "Passwords don't match"; valid = false } else { binding.tilConfirm.error = null }
        
        return valid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
