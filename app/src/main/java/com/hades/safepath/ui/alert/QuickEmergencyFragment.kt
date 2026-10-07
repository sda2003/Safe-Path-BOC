package com.hades.safepath.ui.alert

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.hades.safepath.databinding.FragmentQuickEmergencyBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.hades.safepath.intent.IntentRecognitionEngine
import com.hades.safepath.voice.SpeechRecognitionService
import com.hades.safepath.voice.VoiceState
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.view.MotionEvent

@AndroidEntryPoint
class QuickEmergencyFragment : Fragment() {

    @Inject
    lateinit var intentEngine: IntentRecognitionEngine

    @Inject
    lateinit var speechService: SpeechRecognitionService

    private var _binding: FragmentQuickEmergencyBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted
            } else {
                Toast.makeText(requireContext(), "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuickEmergencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnFlood.setOnClickListener { handleCommand("FLOOD_REPORT") }
        binding.btnFire.setOnClickListener { handleCommand("FIRE_EMERGENCY") }
        binding.btnLandslide.setOnClickListener { handleCommand("LANDSLIDE_REPORT") }
        binding.btnMedical.setOnClickListener { handleCommand("MEDICAL_EMERGENCY") }
        binding.btnTrapped.setOnClickListener { handleCommand("TRAPPED_PERSONS") }
        binding.btnBuildingCollapse.setOnClickListener { handleCommand("BUILDING_COLLAPSE") }
        binding.btnNeedFoodWater.setOnClickListener { handleCommand("NEED_SUPPLIES") }
        binding.btnNeedRescue.setOnClickListener { handleCommand("NEED_RESCUE") }
        binding.btnSafeShelter.setOnClickListener { handleCommand("FIND_SHELTER") }
        binding.btnReportHazard.setOnClickListener { handleCommand("REPORT_HAZARD") }
        binding.btnSos.setOnClickListener { handleCommand("SOS") }

        setupVoiceCommand()
    }

    private fun setupVoiceCommand() {
        binding.btnVoiceCommand.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasMicPermission()) {
                        binding.btnVoiceCommand.text = "Listening..."
                        speechService.startListening()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.btnVoiceCommand.text = "🎤 Hold to Speak Command"
                    speechService.stopListening()
                    true
                }
                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            speechService.voiceState.collect { state ->
                when (state) {
                    is VoiceState.Success -> {
                        Toast.makeText(requireContext(), "Heard: ${state.text}", Toast.LENGTH_SHORT).show()
                        handleCommand(state.text)
                    }
                    is VoiceState.Error -> {
                        Toast.makeText(requireContext(), "Voice Error: ${state.message}", Toast.LENGTH_SHORT).show()
                    }
                    else -> {} // Idle, Listening, Processing
                }
            }
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleCommand(intentCode: String) {
        val match = intentEngine.analyzeText(intentCode)
        Toast.makeText(requireContext(), "Intent Matched: ${match.type} (Severity: ${match.severity})", Toast.LENGTH_SHORT).show()
        // In the future (Phase 6+), this match can be passed to Voice/Alert systems
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechService.destroy()
        _binding = null
    }
}
