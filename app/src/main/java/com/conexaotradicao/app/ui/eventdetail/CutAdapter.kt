package com.conexaotradicao.app.ui.eventdetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conexaotradicao.app.data.model.Cut
import com.conexaotradicao.app.databinding.ItemCutPriceBinding
import java.util.Locale

/**
 * Tabela "Preço (R$/KG)" com checkbox por corte (RF06). Guarda o conjunto de cortes
 * marcados (`checkedIds`) aqui dentro — não só no ViewModel — porque a RecyclerView recicla
 * e rebinda as views ao rolar a lista; sem isso, um item que saiu e voltou pra tela
 * "esquecia" visualmente que estava marcado mesmo a seleção real continuando correta.
 */
class CutAdapter(private val onToggle: (String, Boolean) -> Unit) :
    ListAdapter<Cut, CutAdapter.ViewHolder>(DiffCallback) {

    private var checkedIds: Set<String> = emptySet()

    /** Chamado pelo Fragment quando a seleção "oficial" (ViewModel) muda — tanto a seleção
     * manual do usuário quanto a pré-carregada de uma participação já existente (RF07). */
    fun setCheckedIds(ids: Set<String>) {
        if (ids == checkedIds) return
        checkedIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCutPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCutPriceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cut: Cut) {
            binding.cutName.text = cut.name
            binding.cutPrice.text = String.format(Locale("pt", "BR"), "R$ %.2f", cut.pricePerKg)
            binding.cutCheckbox.setOnCheckedChangeListener(null)
            binding.cutCheckbox.isChecked = cut.id in checkedIds
            binding.cutCheckbox.setOnCheckedChangeListener { _, checked -> onToggle(cut.id, checked) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Cut>() {
        override fun areItemsTheSame(oldItem: Cut, newItem: Cut) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Cut, newItem: Cut) = oldItem == newItem
    }
}
