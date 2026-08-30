package com.conexaotradicao.app.ui.chat

/**
 * Guarda qual conversa está aberta na tela agora (setado pelo [ChatFragment] em
 * onResume/onPause), pra o [com.conexaotradicao.app.data.repository.ChatNotifier] não
 * disparar uma notificação de uma conversa que o usuário já está vendo.
 */
object ChatScreenTracker {
    var openConversationId: String? = null
}
