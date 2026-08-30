package com.conexaotradicao.app.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.conexaotradicao.app.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth

/** Tela 4 — Chat com o Produtor (RF08). */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val args: ChatFragmentArgs by navArgs()

    private val viewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    conversationId = args.eventId,
                    currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
                    chatRepository = RepositoryProvider.chatRepository(requireContext())
                ) as T
        }
    }

    private lateinit var adapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.producerName.text = args.producerName
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        adapter = MessageAdapter(FirebaseAuth.getInstance().currentUser?.uid, args.producerId)
        binding.messageList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messageList.adapter = adapter

        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages) {
                if (messages.isNotEmpty()) binding.messageList.scrollToPosition(messages.size - 1)
            }
        }

        binding.inputBar.setEndIconOnClickListener {
            val text = binding.inputMessage.text?.toString().orEmpty()
            viewModel.send(text)
            binding.inputMessage.text?.clear()
        }
    }

    override fun onResume() {
        super.onResume()
        // Enquanto essa conversa está na tela, o ChatNotifier (RF11) não precisa avisar dela.
        ChatScreenTracker.openConversationId = args.eventId
    }

    override fun onPause() {
        super.onPause()
        if (ChatScreenTracker.openConversationId == args.eventId) {
            ChatScreenTracker.openConversationId = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
