package com.hades.safepath.ui.alert

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentImminentAlertBinding

class ImminentAlertFragment : Fragment() {

    private var _binding: FragmentImminentAlertBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImminentAlertBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startGlowAnimation()

        binding.btnLeaveNow.setOnClickListener {
            val alertId = arguments?.getString("alertId")
            val bundle = Bundle().apply {
                putString("alertId", alertId)
            }
            findNavController().navigate(R.id.action_imminentAlert_to_evacuationMap, bundle)
        }
    }

    private fun startGlowAnimation() {
        val glow = ObjectAnimator.ofFloat(binding.tvAlertBadge, "alpha", 0.7f, 1f, 0.7f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
        }
        glow.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
