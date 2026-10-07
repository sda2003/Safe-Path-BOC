package com.hades.safepath.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.hades.safepath.R
import com.hades.safepath.databinding.FragmentCommunityBinding
import com.hades.safepath.util.NetworkMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var networkMonitor: NetworkMonitor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOnlineChat.setOnClickListener {
            findNavController().navigate(
                R.id.action_community_to_chatRoom,
                bundleOf("roomId" to "safepath_global", "roomName" to "SafePath Community")
            )
        }

        binding.btnMeshNotes.setOnClickListener {
            findNavController().navigate(R.id.action_community_to_meshNotes)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkMonitor.observeOnline().collect { online ->
                    binding.tvStatus.text = if (online) {
                        getString(R.string.community_status_online)
                    } else {
                        getString(R.string.community_status_offline)
                    }
                    binding.btnOnlineChat.isEnabled = online
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
