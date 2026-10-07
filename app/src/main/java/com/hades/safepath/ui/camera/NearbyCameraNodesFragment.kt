package com.hades.safepath.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hades.safepath.R
import com.hades.safepath.camera.SafePathCameraNode
import com.hades.safepath.databinding.FragmentNearbyCameraNodesBinding
import com.hades.safepath.databinding.ItemCameraNodeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NearbyCameraNodesFragment : Fragment() {

    private var _binding: FragmentNearbyCameraNodesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NearbyCameraNodesViewModel by viewModels()

    private val adapter = CameraNodeAdapter { node -> onConnectClicked(node) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            beginScan()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.nearby_nodes_title)
                .setMessage(R.string.ble_permission_denied_explain)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            binding.tvScanStatus.text = getString(R.string.ble_permission_denied_explain)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNearbyCameraNodesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvNodes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNodes.adapter = adapter

        binding.btnScan.setOnClickListener { requestPermissionsAndScan() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.nodes.collect { nodes ->
                        adapter.submitList(nodes)
                        val scanning = viewModel.scanning.value
                        binding.tvEmpty.isVisible = nodes.isEmpty() && !scanning
                        binding.rvNodes.isVisible = nodes.isNotEmpty()
                    }
                }
                launch {
                    viewModel.scanning.collect { scanning ->
                        binding.btnScan.text = if (scanning) {
                            getString(R.string.stop_scan_nodes)
                        } else {
                            getString(R.string.scan_safepath_nodes)
                        }
                        binding.tvScanStatus.text = when {
                            scanning -> getString(R.string.scanning_nodes)
                            viewModel.nodes.value.isEmpty() -> getString(R.string.tap_scan_hint)
                            else -> getString(R.string.nodes_found_count, viewModel.nodes.value.size)
                        }
                        binding.tvEmpty.isVisible =
                            viewModel.nodes.value.isEmpty() && !scanning
                    }
                }
                launch {
                    viewModel.scanError.collect { err ->
                        if (!err.isNullOrBlank()) {
                            binding.tvScanStatus.text = err
                            Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun requestPermissionsAndScan() {
        if (viewModel.scanning.value) {
            viewModel.stopScan()
            return
        }
        if (!viewModel.isBluetoothEnabled()) {
            binding.tvScanStatus.text = getString(R.string.bluetooth_disabled_hint)
            Toast.makeText(requireContext(), R.string.bluetooth_disabled_hint, Toast.LENGTH_LONG)
                .show()
            return
        }
        val needed = scanPermissions().filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            beginScan()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun beginScan() {
        viewModel.startScan()
    }

    private fun onConnectClicked(node: SafePathCameraNode) {
        viewModel.stopScan()
        viewModel.selectNode(node)
        findNavController().navigate(
            R.id.action_nearbyNodes_to_camera,
            bundleOf(
                "nodeId" to node.nodeId,
                "deviceName" to node.deviceName,
                "wifiSsid" to node.wifiSsid,
                "wifiIp" to node.wifiIp,
                "wifiPassword" to node.wifiPassword,
                "rssi" to node.rssi,
                "bluetoothAddress" to node.bluetoothAddress
            )
        )
    }

    private fun scanPermissions(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
            list += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        // Wi-Fi picker cannot see the ESP32 hotspot unless location (or nearby Wi-Fi) is granted.
        list += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return list
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class CameraNodeAdapter(
    private val onConnect: (SafePathCameraNode) -> Unit
) : ListAdapter<SafePathCameraNode, CameraNodeAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCameraNodeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding, onConnect)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(
        private val binding: ItemCameraNodeBinding,
        private val onConnect: (SafePathCameraNode) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: SafePathCameraNode) {
            binding.tvDeviceName.text = "📷 ${node.deviceName}"
            binding.tvNodeRole.text = binding.root.context.getString(
                R.string.camera_node_role_line,
                node.nodeId
            )
            binding.tvSignal.text = binding.root.context.getString(
                R.string.signal_label,
                node.signalLabel
            )
            binding.tvStatus.text = binding.root.context.getString(R.string.status_available)
            binding.btnConnect.setOnClickListener { onConnect(node) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SafePathCameraNode>() {
            override fun areItemsTheSame(a: SafePathCameraNode, b: SafePathCameraNode) =
                a.bluetoothAddress == b.bluetoothAddress && a.deviceName == b.deviceName

            override fun areContentsTheSame(a: SafePathCameraNode, b: SafePathCameraNode) =
                a == b
        }
    }
}
