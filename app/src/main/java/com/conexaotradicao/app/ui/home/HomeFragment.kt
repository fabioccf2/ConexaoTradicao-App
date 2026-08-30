package com.conexaotradicao.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.conexaotradicao.app.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth

/** Tela 2 — Próximos Eventos de Carneamento (Home). RF03 e RF04. */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(
                    eventRepository = RepositoryProvider.eventRepository(requireContext()),
                    currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                ) as T
        }
    }

    private lateinit var adapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter { event ->
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToEventDetail(event.id)
            )
        }
        binding.eventList.layoutManager = LinearLayoutManager(requireContext())
        binding.eventList.adapter = adapter

        binding.searchCity.doAfterTextChangedCompat { viewModel.onCityChanged(it) }
        binding.searchProduct.doAfterTextChangedCompat { viewModel.onProductChanged(it) }

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        // RF05 — botão "+" abre o cadastro de evento pelo produtor
        binding.fabCreateEvent.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeToCreateEvent())
        }

        viewModel.events.observe(viewLifecycleOwner) { events ->
            adapter.submitList(events)
            binding.emptyState.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        }
        viewModel.isRefreshing.observe(viewLifecycleOwner) { binding.swipeRefresh.isRefreshing = it }
    }

    private fun android.widget.EditText.doAfterTextChangedCompat(action: (String) -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = action(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
