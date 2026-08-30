package com.conexaotradicao.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.conexaotradicao.app.MainActivity

/**
 * Notificações locais (RF11). O plano Spark (gratuito) do Firebase não permite Cloud
 * Functions com saída de rede, que é o jeito "de livro" de disparar um push de verdade a
 * partir do servidor quando alguém manda uma mensagem. Em vez disso, o app escuta o
 * Firestore em tempo real enquanto está aberto ou em segundo plano (ver [com.conexaotradicao.app.data.repository.ChatNotifier])
 * e mostra a notificação localmente — o efeito prático pro usuário é o mesmo, sem custo de
 * infraestrutura. Se um dia o projeto migrar pro plano Blaze, dá pra trocar isso por Cloud
 * Functions + FCM de verdade sem mudar a tela nenhuma, só a origem do disparo.
 */
object NotificationHelper {
    const val CHANNEL_ID = "conexao_tradicao_default"

    // RF11 — mesma tag de depuração do ChatNotifier (ver ali o motivo).
    private const val TAG_RF11_DEBUG = "RF11_DEBUG"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Conexão & Tradição",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mensagens de chat e avisos de carneação"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, notificationId: Int, title: String, body: String) {
        ensureChannel(context)

        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            // Esse é o suspeito nº 1 do "não tá tendo notificação": no Android 13+
            // (TIRAMISU) sem a permissão POST_NOTIFICATIONS concedida, a notificação é
            // descartada bem aqui, sem erro nenhum em lugar nenhum — por isso parecia
            // "silencioso". Se essa linha aparecer no Logcat, o código está funcionando
            // certinho até aqui; falta só a permissão (Configurações → Apps → Conexão &
            // Tradição → Notificações) ou o modo Não Perturbe do sistema.
            Log.w(TAG_RF11_DEBUG, "showNotification($notificationId) ABORTADA: sem permissão POST_NOTIFICATIONS (SDK=${Build.VERSION.SDK_INT})")
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
        Log.i(TAG_RF11_DEBUG, "showNotification($notificationId): manager.notify() chamado com sucesso — title=\"$title\"")
    }
}
