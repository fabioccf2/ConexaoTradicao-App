package com.conexaotradicao.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conexaotradicao.app.R
import com.conexaotradicao.app.data.model.Animal
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.model.EventCardItem
import com.conexaotradicao.app.databinding.ItemEventCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cards de evento na Home (RF03): data, cidade/UF, animal, cortes, produtor e nota. */
class EventAdapter(private val onClick: (Event) -> Unit) :
    ListAdapter<EventCardItem, EventAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemEventCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM", Locale("pt", "BR"))

        fun bind(item: EventCardItem) {
            val event = item.event
            binding.eventDate.text = dateFormat.format(Date(event.dateMillis))
            binding.eventCity.text = "${event.city}/${event.state}"
            binding.eventProducer.text = event.producerName
            binding.eventRating.text = String.format(Locale("pt", "BR"), "★ %.1f", event.producerRatingAverage)
            binding.eventCuts.text = event.mainCutsSummary
            // Ícones do tipo de animal descritos na AA1 (boi/porco). ic_cow_flat/ic_pig_flat
            // são as artes prontas que o usuário forneceu (substituem tanto os ícones
            // genéricos do Android de antes quanto o primeiro rascunho vetorial).
            binding.eventAnimalIcon.setImageResource(
                if (event.animal == Animal.GADO) R.drawable.ic_cow_flat else R.drawable.ic_pig_flat
            )
            // Rótulo de texto embaixo do ícone — deixa o tipo de animal inconfundível,
            // inclusive pra pegar na hora um evento cadastrado com o tipo errado.
            binding.eventAnimalLabel.text =
                if (event.animal == Animal.GADO) "Gado" else "Porco"

            // RF07 — o usuário já agendou presença nesse evento? Destaca o card inteiro em
            // verde clarinho, pra bater o olho e ver de cara quais carneações já estão
            // garantidas, sem precisar abrir cada uma ou ir até o Perfil conferir.
            // RF10 — evento já finalizado tem prioridade visual sobre isso (o card fica
            // opaco/neutro em vez de verde, já que "agendado" deixou de fazer sentido pra
            // ele — HomeViewModel também já joga esses eventos pro fim da lista).
            val context = binding.root.context
            when {
                event.finalized -> {
                    binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.beige))
                    binding.root.setStrokeColor(ContextCompat.getColor(context, R.color.beige_dark))
                    binding.root.alpha = 0.7f
                }
                item.isScheduled -> {
                    binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.moss_green_light))
                    binding.root.setStrokeColor(ContextCompat.getColor(context, R.color.moss_green))
                    binding.root.alpha = 1f
                }
                else -> {
                    binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
                    binding.root.setStrokeColor(ContextCompat.getColor(context, R.color.beige_dark))
                    binding.root.alpha = 1f
                }
            }
            binding.eventFinalizedBadge.visibility = if (event.finalized) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(event) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<EventCardItem>() {
        override fun areItemsTheSame(oldItem: EventCardItem, newItem: EventCardItem) =
            oldItem.event.id == newItem.event.id
        override fun areContentsTheSame(oldItem: EventCardItem, newItem: EventCardItem) =
            oldItem == newItem
    }
}
