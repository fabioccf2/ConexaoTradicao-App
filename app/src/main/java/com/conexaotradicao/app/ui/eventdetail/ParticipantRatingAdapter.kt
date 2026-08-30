package com.conexaotradicao.app.ui.eventdetail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conexaotradicao.app.data.model.ParticipationWithUser
import com.conexaotradicao.app.databinding.ItemParticipantRatingBinding

/**
 * RF10 — lista de compradores de um evento finalizado, pro produtor avaliar cada um. Quem já
 * foi avaliado (`producerRated`) mostra "Avaliado ✓" no lugar do botão.
 */
class ParticipantRatingAdapter(private val onRate: (ParticipationWithUser) -> Unit) :
    ListAdapter<ParticipationWithUser, ParticipantRatingAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipantRatingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemParticipantRatingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ParticipationWithUser) {
            binding.participantName.text = item.buyerName
            val alreadyRated = item.participation.producerRated
            binding.participantStatus.visibility = if (alreadyRated) View.VISIBLE else View.GONE
            binding.btnRateParticipant.visibility = if (alreadyRated) View.GONE else View.VISIBLE
            binding.btnRateParticipant.setOnClickListener { onRate(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ParticipationWithUser>() {
        override fun areItemsTheSame(oldItem: ParticipationWithUser, newItem: ParticipationWithUser) =
            oldItem.participation.id == newItem.participation.id

        override fun areContentsTheSame(oldItem: ParticipationWithUser, newItem: ParticipationWithUser) =
            oldItem == newItem
    }
}
