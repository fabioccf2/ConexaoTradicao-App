package com.conexaotradicao.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.conexaotradicao.app.R
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.conexaotradicao.app.databinding.DialogEditProfileBinding
import com.conexaotradicao.app.databinding.FragmentProfileBinding
import com.conexaotradicao.app.ui.home.EventAdapter
import com.conexaotradicao.app.util.Resource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

/** Tela 5 — Perfil (RF02, RF10). */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(
                    userId = FirebaseAuth.getInstance().currentUser?.uid,
                    profileRepository = RepositoryProvider.profileRepository(requireContext())
                ) as T
        }
    }

    private lateinit var upcomingAdapter: ParticipationAdapter
    private lateinit var historyAdapter: ParticipationAdapter
    private lateinit var producerEventsAdapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RF10 — sempre que a tela de Perfil abre, rebusca a nota em estrelas no Firestore
        // (pode ter mudado num outro aparelho desde o último login neste). Ver
        // ProfileViewModel.refresh()/ProfileRepository.refreshUser().
        viewModel.refresh()

        // Tocar num item (agendado ou histórico) leva direto pro Detalhe do Evento (RF06).
        val openEventDetail: (com.conexaotradicao.app.data.model.ParticipationWithCuts) -> Unit = { item ->
            findNavController().navigate(
                ProfileFragmentDirections.actionProfileToEventDetail(item.participation.eventId)
            )
        }

        upcomingAdapter = ParticipationAdapter(openEventDetail)
        binding.upcomingList.layoutManager = LinearLayoutManager(requireContext())
        binding.upcomingList.adapter = upcomingAdapter

        historyAdapter = ParticipationAdapter(openEventDetail)
        binding.participationList.layoutManager = LinearLayoutManager(requireContext())
        binding.participationList.adapter = historyAdapter

        // RF10 — "Meus Eventos Finalizados" (produtor): mesmo card/adapter da Home, só que
        // aqui a lista já vem só com os eventos que o próprio usuário criou e já finalizou.
        producerEventsAdapter = EventAdapter { event ->
            findNavController().navigate(ProfileFragmentDirections.actionProfileToEventDetail(event.id))
        }
        binding.producerEventsList.layoutManager = LinearLayoutManager(requireContext())
        binding.producerEventsList.adapter = producerEventsAdapter

        viewModel.user.observe(viewLifecycleOwner) { user ->
            binding.userName.text = user?.name.orEmpty()
            binding.userRating.text = user?.let {
                String.format(java.util.Locale("pt", "BR"), "★ %.1f (%d avaliações)", it.ratingAverage, it.ratingCount)
            }.orEmpty()
        }

        viewModel.upcomingParticipations.observe(viewLifecycleOwner) { list ->
            upcomingAdapter.submitList(list)
            binding.emptyUpcoming.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.pastParticipations.observe(viewLifecycleOwner) { list ->
            historyAdapter.submitList(list)
            binding.emptyHistory.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }

        // RF10 — some inteira (título incluso) pra quem nunca finalizou um evento como
        // produtor, em vez de mostrar "Meus Eventos Finalizados" vazio pra toda conta
        // compradora.
        viewModel.myFinalizedEvents.observe(viewLifecycleOwner) { list ->
            producerEventsAdapter.submitList(list)
            binding.producerEventsSection.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.editProfileState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success -> Toast.makeText(requireContext(), "Nome atualizado!", Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        binding.btnEditProfile.setOnClickListener { showEditNameDialog() }

        binding.btnLogout.setOnClickListener {
            RepositoryProvider.authRepository(requireContext()).logout()
            requireActivity().finishAffinity()
            startActivity(
                android.content.Intent(requireContext(), com.conexaotradicao.app.AuthActivity::class.java)
            )
        }
    }

    private fun showEditNameDialog() {
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialogBinding.inputEditName.setText(viewModel.user.value?.name.orEmpty())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.btn_edit_profile))
            .setView(dialogBinding.root)
            .setPositiveButton("Salvar") { _, _ ->
                viewModel.updateName(dialogBinding.inputEditName.text?.toString().orEmpty())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
