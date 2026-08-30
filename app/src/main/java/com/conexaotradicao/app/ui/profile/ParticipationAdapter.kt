package com.conexaotradicao.app.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conexaotradicao.app.data.model.ParticipationWithCuts
import com.conexaotradicao.app.databinding.ItemParticipationHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Item de "Meus Próximos Eventos"/Histórico do perfil (RF02, RF07, RF10). Mostra os cortes
 * selecionados e, ao tocar, leva pro Detalhe do Evento correspondente (`onClick`).
 */
class ParticipationAdapter(private val onClick: (ParticipationWithCuts) -> Unit) :
    ListAdapter<ParticipationWithCuts, ParticipationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipationHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), onClick)

    class ViewHolder(private val binding: ItemParticipationHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

        fun bind(item: ParticipationWithCuts, onClick: (ParticipationWithCuts) -> Unit) {
            val participation = item.participation
            binding.participationDate.text = dateFormat.format(Date(participation.scheduledAtMillis))
            binding.participationStatus.text = participation.status.name
            binding.participationCuts.text = item.cutNames
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: "Nenhum corte selecionado"
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ParticipationWithCuts>() {
        override fun areItemsTheSame(oldItem: ParticipationWithCuts, newItem: ParticipationWithCuts) =
            oldItem.participation.id == newItem.participation.id
        override fun areContentsTheSame(oldItem: ParticipationWithCuts, newItem: ParticipationWithCuts) =
            oldItem == newItem
    }
}
