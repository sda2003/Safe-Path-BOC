package com.hades.safepath.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hades.safepath.data.model.ChatMessage
import com.hades.safepath.databinding.FragmentChatRoomBinding
import com.hades.safepath.databinding.ItemChatMessageBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.bumptech.glide.Glide

@AndroidEntryPoint
class ChatRoomFragment : Fragment() {

    private var _binding: FragmentChatRoomBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var districtId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatRoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val roomId = arguments?.getString("roomId") ?: "safepath_global"
        val roomName = arguments?.getString("roomName") ?: "SafePath Community"
        
        binding.toolbar.title = roomName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val inputStream = requireContext().contentResolver.openInputStream(it)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val isAnonymous = binding.checkboxAnonymous.isChecked
                    
                    // Compress and resize
                    val ratio = 800f / originalBitmap.width
                    val newHeight = (originalBitmap.height * ratio).toInt()
                    val resized = if (originalBitmap.width > 800) {
                        Bitmap.createScaledBitmap(originalBitmap, 800, newHeight, true)
                    } else {
                        originalBitmap
                    }

                    val baos = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val bytes = baos.toByteArray()

                    android.widget.Toast.makeText(requireContext(), "Uploading image...", android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.sendImageMessage(roomId, bytes, "User", isAnonymous).observe(viewLifecycleOwner) { result ->
                        result.onFailure { error ->
                            android.widget.Toast.makeText(requireContext(), "Failed to send image: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        
        binding.btnPickImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        val adapter = ChatAdapter(viewModel.currentUserId)
        val layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        binding.rvChat.layoutManager = layoutManager
        binding.rvChat.adapter = adapter

        viewModel.observeMessages(roomId).observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.rvChat.smoothScrollToPosition(messages.size - 1)
            }
        }

        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                val isAnonymous = binding.checkboxAnonymous.isChecked
                val senderName = viewModel.currentUserId?.let { "User" } ?: "User"
                if (viewModel.currentUserId == null) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Sign in to send messages",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                binding.btnSend.isEnabled = false
                viewModel.sendMessage(roomId, content, senderName, isAnonymous).observe(viewLifecycleOwner) { result ->
                    binding.btnSend.isEnabled = true
                    result.onSuccess {
                        binding.etMessage.text.clear()
                    }
                    result.onFailure { error ->
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to send: ${error.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ChatAdapter(private val currentUserId: String?) : androidx.recyclerview.widget.ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        holder.binding.tvSender.text = msg.senderName
        
        if (msg.imageUrl != null) {
            holder.binding.tvMessage.visibility = View.GONE
            holder.binding.ivMedia.visibility = View.VISIBLE
            Glide.with(holder.binding.root)
                .load(msg.imageUrl)
                .into(holder.binding.ivMedia)
        } else {
            holder.binding.tvMessage.visibility = View.VISIBLE
            holder.binding.ivMedia.visibility = View.GONE
            holder.binding.tvMessage.text = msg.message
        }
        
        val isMe = msg.userId == currentUserId
        val params = holder.binding.cardMessage.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        
        if (isMe) {
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            holder.binding.cardMessage.setCardBackgroundColor(android.graphics.Color.parseColor("#D72B2B"))
            holder.binding.tvSender.setTextColor(android.graphics.Color.WHITE)
            holder.binding.tvMessage.setTextColor(android.graphics.Color.WHITE)
        } else {
            params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            holder.binding.cardMessage.setCardBackgroundColor(android.graphics.Color.parseColor("#2A2A2A"))
            holder.binding.tvSender.setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            holder.binding.tvMessage.setTextColor(android.graphics.Color.parseColor("#F9FAFB"))
        }
        holder.binding.cardMessage.layoutParams = params
    }

    companion object {
        private val DiffCallback = object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }
}
