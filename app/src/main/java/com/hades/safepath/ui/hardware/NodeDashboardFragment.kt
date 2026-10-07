package com.hades.safepath.ui.hardware

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentNodeDashboardBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NodeDashboardFragment : Fragment() {

    private var _binding: FragmentNodeDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NodeDashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNodeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnInjectDemoPacket.setOnClickListener {
            viewModel.injectDemoPacket()
        }

        viewModel.markOfflineAfterTimeout()

        viewModel.telemetry.observe(viewLifecycleOwner) { ui ->
            bindTelemetry(ui)
        }

        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.toolbar.subtitle = msg
        }
    }

    private fun bindTelemetry(ui: NodeTelemetryUi) {
        binding.tvNodeOnline.text = if (ui.online) "Node: ONLINE" else "Node: OFFLINE"
        binding.tvNodeId.text = ui.nodeId

        val dot = binding.viewNodeStatusDot.background as? GradientDrawable
            ?: GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                binding.viewNodeStatusDot.background = it
            }
        dot.setColor(if (ui.online) Color.parseColor("#22C55E") else Color.parseColor("#EF4444"))

        binding.tvTemperature.text = ui.temperatureC?.let { String.format("%.1f °C", it) } ?: "-- °C"
        binding.tvHumidity.text = ui.humidityPct?.let { String.format("%.0f %%", it) } ?: "-- %"

        binding.tvRiskBadge.text = ui.riskLabel
        binding.tvRiskBadge.setBackgroundColor(
            when (ui.risk) {
                0 -> Color.parseColor("#22C55E")
                1 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            }
        )

        binding.tvPlaceName.text = ui.placeName
        binding.tvCoordinates.text = if (ui.lat != null && ui.lon != null) {
            String.format("%.5f, %.5f", ui.lat, ui.lon)
        } else {
            "—, —"
        }

        binding.tvLastUpdated.text = viewModel.formatLastUpdated(ui.updatedAtMs)

        val shot = ui.cameraBitmap
        val url = ui.cameraUrl
        if (shot != null && !shot.isRecycled) {
            binding.imgNodeCamera.setImageBitmap(shot)
        } else if (!url.isNullOrBlank()) {
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .into(binding.imgNodeCamera)
        } else {
            binding.imgNodeCamera.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_image)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
