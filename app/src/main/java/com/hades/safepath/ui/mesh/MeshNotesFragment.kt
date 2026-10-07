package com.hades.safepath.ui.mesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.hades.safepath.R
import com.hades.safepath.data.entity.MeshNoteEntity
import com.hades.safepath.databinding.FragmentMeshNotesBinding
import com.hades.safepath.databinding.ItemMeshNoteBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MeshNotesFragment : Fragment() {

    private var _binding: FragmentMeshNotesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MeshNotesViewModel by viewModels()
    private val adapter = MeshNotesAdapter()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            viewModel.startMesh()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.mesh_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeshNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvNotes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotes.adapter = adapter

        binding.btnSend.setOnClickListener {
            val text = binding.etNote.text?.toString().orEmpty()
            val type = selectedType()
            viewModel.postNote(text, type, "Anonymous")
            binding.etNote.text?.clear()
        }

        ensureMeshPermissionsAndStart()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notes.collect { notes ->
                        adapter.submitList(notes)
                    }
                }
                launch {
                    var wasOnline = viewModel.isOnline.value
                    viewModel.isOnline.collect { online ->
                        updateBanner(online, viewModel.peerCount.value)
                        if (online && !wasOnline) viewModel.flushNow()
                        wasOnline = online
                    }
                }
                launch {
                    viewModel.peerCount.collect { peers ->
                        updateBanner(viewModel.isOnline.value, peers)
                    }
                }
                launch {
                    viewModel.events.collect { msg ->
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updateBanner(online: Boolean, peers: Int) {
        binding.tvBanner.text = when {
            online && peers > 0 ->
                getString(R.string.mesh_banner_online_peers, peers)
            online ->
                getString(R.string.mesh_banner_online)
            peers > 0 ->
                getString(R.string.mesh_banner_offline_peers, peers)
            else ->
                getString(R.string.mesh_banner_offline)
        }
    }

    private fun selectedType(): String = when (binding.chipType.checkedChipId) {
        R.id.chip_hazard -> "hazard"
        R.id.chip_need -> "need"
        R.id.chip_safe -> "safe"
        else -> "info"
    }

    private fun ensureMeshPermissionsAndStart() {
        val needed = meshPermissions().filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            viewModel.startMesh()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun meshPermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_ADVERTISE
        }
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

private class MeshNotesAdapter :
    ListAdapter<MeshNoteEntity, MeshNotesAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMeshNoteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val binding: ItemMeshNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: MeshNoteEntity) {
            binding.tvType.text = note.type.uppercase()
            binding.tvText.text = note.text
            val whenText = DateUtils.getRelativeTimeSpanString(
                note.createdAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            binding.tvSender.text = "${note.senderName} · $whenText"
            val sync = if (note.syncedToCloud) "synced" else "pending sync"
            binding.tvMeta.text = "${note.source} · ${note.hops} hops · $sync"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MeshNoteEntity>() {
            override fun areItemsTheSame(a: MeshNoteEntity, b: MeshNoteEntity) =
                a.noteId == b.noteId

            override fun areContentsTheSame(a: MeshNoteEntity, b: MeshNoteEntity) =
                a == b
        }
    }
}
