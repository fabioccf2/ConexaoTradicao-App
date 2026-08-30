package com.conexaotradicao.app.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.repository.ChatRepository
import kotlinx.coroutines.launch

/** Tela 4 — Chat com o Produtor (RF08). conversationId = eventId (uma conversa por evento). */
class ChatViewModel(
    private val conversationId: String,
    private val currentUserId: String?,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val messages = chatRepository.observeMessages(conversationId).asLiveData()

    init {
        viewModelScope.launch {
            chatRepository.listenRemote(conversationId).collect { /* apenas dispara upsert em Room */ }
        }
    }

    fun send(text: String) {
        val uid = currentUserId ?: return
        if (text.isBlank()) return
        viewModelScope.launch { chatRepository.send(conversationId, uid, text) }
    }
}
