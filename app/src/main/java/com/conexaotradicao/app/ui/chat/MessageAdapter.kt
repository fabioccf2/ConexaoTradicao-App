package com.conexaotradicao.app.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.conexaotradicao.app.R
import com.conexaotradicao.app.data.model.ChatMessage
import com.conexaotradicao.app.databinding.ItemMessageReceivedBinding
import com.conexaotradicao.app.databinding.ItemMessageSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Balões de mensagem diferenciados por remetente (RF08). Mensagem do produtor do evento
 * (o "Anfitrião" da carneação) fica sempre num balão verde escuro, sentado ou recebido, e o
 * nome dele aparece como "Anfitrião" em vez do nome pessoal quando outra pessoa está lendo.
 */
class MessageAdapter(private val currentUserId: String?, private val producerId: String?) :
    ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        val time = timeFormat.format(Date(message.timestampMillis))
        val isHost = producerId != null && message.senderId == producerId
        when (holder) {
            is SentViewHolder -> holder.bind(message.text.orEmpty(), time, isHost)
            // Mensagem recebida mostra quem mandou (a conversa é única por evento, pode ter
            // mais de um comprador nela — sem isso não dá pra saber quem é quem). Se for o
            // produtor, mostra "Anfitrião" no lugar do nome dele.
            is ReceivedViewHolder -> {
                val label = if (isHost) "Anfitrião" else message.senderName
                holder.bind(label, message.text.orEmpty(), time, isHost)
            }
        }
    }

    class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String, time: String, isHost: Boolean) {
            binding.messageText.text = text
            binding.messageTime.text = time
            binding.messageText.setBackgroundResource(
                if (isHost) R.drawable.bg_bubble_host_sent else R.drawable.bg_bubble_sent
            )
        }
    }

    class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(senderName: String, text: String, time: String, isHost: Boolean) {
            binding.messageSender.text = senderName
            binding.messageText.text = text
            binding.messageTime.text = time
            val context = binding.root.context
            if (isHost) {
                binding.messageText.setBackgroundResource(R.drawable.bg_bubble_host_received)
                binding.messageText.setTextColor(context.getColor(R.color.white))
            } else {
                binding.messageText.setBackgroundResource(R.drawable.bg_bubble_received)
                binding.messageText.setTextColor(context.getColor(R.color.text_primary))
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}
