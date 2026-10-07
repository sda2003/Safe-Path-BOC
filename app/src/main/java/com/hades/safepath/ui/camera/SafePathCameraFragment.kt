package com.hades.safepath.ui.camera

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import android.content.pm.PackageManager
import com.hades.safepath.R
import com.hades.safepath.camera.CameraSessionUi
import com.hades.safepath.camera.SafePathCameraNode
import com.hades.safepath.databinding.FragmentSafepathCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SafePathCameraFragment : Fragment() {

    private var _binding: FragmentSafepathCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SafePathCameraViewModel by viewModels()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.connect() }

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants.isNotEmpty() && grants.values.all { it }
        if (allowed) viewModel.capture()
        else viewModel.captureBlocked("Allow Bluetooth to capture a photo from SafePath-CAM-001.")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSafepathCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val node = nodeFromArgs()
        viewModel.ensureNode(node)
        requestBluetoothThenConnect()

        binding.btnRetryConnect.setOnClickListener { requestBluetoothThenConnect() }
        binding.btnOpenWifi.setOnClickListener { viewModel.openWifiSettings() }
        binding.btnLocation.setOnClickListener { viewModel.openLocationSettings() }
        binding.btnUseJoined.setOnClickListener { viewModel.useJoinedWifi() }
        binding.btnCapture.setOnClickListener { requestBluetoothThenCapture() }
        binding.btnRefreshStatus.setOnClickListener { viewModel.refreshStatus() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.session.collect { ui -> bindSession(ui) }
            }
        }
    }

    private fun bindSession(ui: CameraSessionUi) {
        val node = ui.node
        binding.tvNodeId.text = node?.nodeId ?: "—"
        binding.tvConnectionLabel.text = when {
            ui.connected && ui.status?.isOnline == true ->
                getString(R.string.camera_status_connected_online)
            ui.connected -> getString(R.string.camera_status_connected)
            else -> getString(R.string.camera_status_disconnected)
        }

        val dot = binding.viewStatusDot.background as? GradientDrawable
            ?: GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                binding.viewStatusDot.background = it
            }
        dot.setColor(
            if (ui.connected) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        )

        val hint = ui.wifiHint
        val showHelp = !hint.isNullOrBlank() && !ui.connected
        binding.tvWifiHint.isVisible = showHelp
        binding.tvWifiHint.text = hint
        binding.btnOpenWifi.isVisible = showHelp
        binding.btnLocation.isVisible = showHelp
        binding.btnUseJoined.isVisible = showHelp

        binding.progress.isVisible = ui.busy
        binding.btnCapture.isEnabled = !ui.busy
        binding.btnRefreshStatus.isEnabled = !ui.busy
        binding.btnRetryConnect.isEnabled = !ui.busy

        ui.bitmap?.let { binding.imgCapture.setImageBitmap(it) }

        val imageError = ui.imageError
        binding.tvLastUpdated.text = when {
            ui.busy -> ui.message ?: "Capturing over Bluetooth…"
            !imageError.isNullOrBlank() -> imageError
            ui.lastUpdatedMs != null ->
                getString(R.string.last_updated_at, timeFormat.format(Date(ui.lastUpdatedMs)))
            else -> getString(R.string.last_updated_none)
        }
        binding.tvLastUpdated.setTextColor(
            if (!imageError.isNullOrBlank() && !ui.busy) {
                ContextCompat.getColor(requireContext(), R.color.amber_light)
            } else {
                ContextCompat.getColor(requireContext(), R.color.text_muted)
            }
        )

        binding.tvMetaNode.text = getString(
            R.string.camera_meta_node,
            node?.nodeId ?: "—",
            node?.deviceName ?: "—"
        )
        binding.tvMetaLocation.text = if (node?.hasLocation == true) {
            getString(R.string.camera_meta_location, node.latitude, node.longitude)
        } else {
            getString(R.string.location_unavailable)
        }

        binding.tvMessage.text = ui.message.orEmpty()
        binding.tvMessage.isVisible = !ui.message.isNullOrBlank()
    }

    private fun requestBluetoothThenConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            viewModel.connect()
            return
        }
        val needed = bluetoothPermissions()
        if (needed.isEmpty()) viewModel.connect()
        else connectPermissionLauncher.launch(needed.toTypedArray())
    }

    private val connectPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allowed = grants.isNotEmpty() && grants.values.all { it }
        if (allowed) viewModel.connect()
        else viewModel.captureBlocked("Allow Bluetooth so SafePath can reach SafePath-CAM-001.")
    }

    private fun bluetoothPermissions(): List<String> {
        return listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothThenCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            viewModel.capture()
            return
        }
        val needed = listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ).filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) viewModel.capture()
        else capturePermissionLauncher.launch(needed.toTypedArray())
    }

    private fun requestWifiPermissionsThenConnect() {
        val needed = wifiPermissions().filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) viewModel.connect()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun wifiPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return list
    }

    private fun nodeFromArgs(): SafePathCameraNode {
        val args = requireArguments()
        val name = args.getString("deviceName").orEmpty().ifBlank { "SafePath-CAM-001" }
        return SafePathCameraNode(
            nodeId = args.getString("nodeId").orEmpty().ifBlank { "SP-CAM-001" },
            deviceName = name,
            bluetoothAddress = args.getString("bluetoothAddress").orEmpty(),
            wifiSsid = args.getString("wifiSsid").orEmpty().ifBlank { name },
            wifiPassword = args.getString("wifiPassword")
                ?: SafePathCameraNode.DEFAULT_WIFI_PASSWORD,
            wifiIp = args.getString("wifiIp") ?: SafePathCameraNode.DEFAULT_WIFI_IP,
            rssi = args.getInt("rssi", -70)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
