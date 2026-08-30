package com.conexaotradicao.app.data.remote

import com.conexaotradicao.app.R
import com.conexaotradicao.app.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe pushes do Firebase Cloud Messaging, caso um dia o projeto migre pro plano Blaze e
 * passe a usar Cloud Functions pra disparar notificações do servidor (confirmação do
 * produtor, lembretes de eventos agendados). Por enquanto, no plano Spark, quem cobre o
 * RF11 na prática é o [com.conexaotradicao.app.data.repository.ChatNotifier], que escuta o
 * Firestore diretamente pelo app — ambos os caminhos reaproveitam o mesmo
 * [NotificationHelper] pra mostrar a notificação.
 */
class NotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: return
        NotificationHelper.showNotification(
            context = this,
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            body = body
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO: enviar o token para users/{uid}.fcmToken no Firestore, se o projeto migrar
        // pro plano Blaze e passar a usar Cloud Functions pra notificações direcionadas.
    }
}
