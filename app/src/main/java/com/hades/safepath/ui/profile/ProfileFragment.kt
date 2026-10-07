package com.hades.safepath.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.hades.safepath.R
import com.hades.safepath.auth.VerificationRepository
import com.hades.safepath.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(val id: String, val name: String, val district: String)

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    @Inject
    lateinit var verificationRepository: VerificationRepository

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = supabaseClient.auth.currentUserOrNull()
        if (user != null) {
            binding.tvEmail.text = user.email ?: "No email linked"

            lifecycleScope.launch {
                try {
                    val profile = supabaseClient.postgrest["users"]
                        .select { filter { eq("id", user.id) } }
                        .decodeSingleOrNull<UserProfile>()
                    
                    if (profile != null) {
                        binding.tvName.text = profile.name
                        binding.tvDistrict.text = "📍 ${profile.district}"
                    } else {
                        binding.tvName.text = "Anonymous User"
                        binding.tvDistrict.text = "Unknown District"
                    }
                } catch (e: Exception) {
                    binding.tvName.text = "Anonymous User"
                }
            }
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                verificationRepository.signOut()
                findNavController().navigate(R.id.action_global_authFragment)
            }
        }
        
        setupLanguageSpinner()
    }
    
    private fun setupLanguageSpinner() {
        val languages = listOf("English", "Español", "தமிழ்")
        val locales = listOf("en", "es", "ta")
        
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            languages
        )
        binding.spinnerLanguage.adapter = adapter
        
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val currentLocaleStr = if (!currentLocales.isEmpty) currentLocales.get(0)?.language ?: "en" else "en"
        val selectedIndex = locales.indexOf(currentLocaleStr).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(selectedIndex)
        
        binding.spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLocale = locales[position]
                if (selectedLocale != currentLocaleStr) {
                    val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(selectedLocale)
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
