package com.conexaotradicao.app.ui.eventdetail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.conexaotradicao.app.R
import com.conexaotradicao.app.data.model.Event
import com.conexaotradicao.app.data.model.ParticipationStatus
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.conexaotradicao.app.databinding.DialogRateBinding
import com.conexaotradicao.app.databinding.FragmentEventDetailBinding
import com.conexaotradicao.app.util.ImageUtils
import com.conexaotradicao.app.util.Resource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tela 3 — Detalhes do Evento (RF05, RF06, RF07, RF09, RF10). */
class EventDetailFragment : Fragment() {

    private var _binding: FragmentEventDetailBinding? = null
    private val binding get() = _binding!!

    private val args: EventDetailFragmentArgs by navArgs()

    private val viewModel: EventDetailViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                EventDetailViewModel(
                    eventId = args.eventId,
                    currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
                    eventRepository = RepositoryProvider.eventRepository(requireContext())
                ) as T
        }
    }

    private lateinit var cutAdapter: CutAdapter
    private lateinit var participantsAdapter: ParticipantRatingAdapter

    private var currentEvent: Event? = null

    // Escopo próprio (em vez de lifecycleScope, que exigiria adicionar a dependência
    // lifecycle-runtime-ktx — mesmo motivo documentado em MainActivity.kt) só pra processar
    // as fotos escolhidas (RF10) fora da main thread. Cancelado junto com onDestroyView.
    private var fragmentScope = CoroutineScope(Dispatchers.Main.immediate + Job())

    // RF10 — seletor de fotos do sistema (Android Photo Picker): não precisa de permissão
    // de armazenamento (diferente de um Intent de galeria tradicional), funciona a partir do
    // Android 8 via backport da própria biblioteca. Limitado a 3 fotos por evento (ver
    // ImageUtils/decisão de guardar Base64 direto no Firestore, Parte 3.16).
    private val pickPhotosLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
            onPhotosPicked(uris)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope = CoroutineScope(Dispatchers.Main.immediate + Job())

        cutAdapter = CutAdapter { cutId, checked -> viewModel.toggleCut(cutId, checked) }
        binding.cutList.layoutManager = LinearLayoutManager(requireContext())
        binding.cutList.adapter = cutAdapter

        // RF10 — lista de compradores que o produtor pode avaliar depois de finalizar.
        participantsAdapter = ParticipantRatingAdapter { item ->
            showRatingDialog(getString(R.string.rate_client_message)) { stars ->
                viewModel.rateClient(item.participation.userId, stars)
            }
        }
        binding.participantsList.layoutManager = LinearLayoutManager(requireContext())
        binding.participantsList.adapter = participantsAdapter

        viewModel.event.observe(viewLifecycleOwner) { event ->
            event ?: return@observe
            currentEvent = event
            val dateFormat = SimpleDateFormat("dd 'de' MMMM", Locale("pt", "BR"))
            binding.eventTitle.text = "${event.city}/${event.state}"
            binding.eventSubtitle.text = dateFormat.format(Date(event.dateMillis))
            binding.eventProducer.text = event.producerName
            binding.eventRating.text = String.format(Locale("pt", "BR"), "★ %.1f", event.producerRatingAverage)
            updateRoleSpecificUi()
            updateScheduleButtonState()
        }

        viewModel.cuts.observe(viewLifecycleOwner) { cutAdapter.submitList(it) }

        // RF06/RF07 — pré-marca (e mantém marcado ao rolar) os cortes que o comprador já
        // tinha escolhido numa participação existente, em vez de sempre abrir em branco.
        viewModel.selectedCutIds.observe(viewLifecycleOwner) { cutAdapter.setCheckedIds(it) }

        // RF10 — se já avaliou o produtor, status da participação etc.
        viewModel.myParticipation.observe(viewLifecycleOwner) { updateRoleSpecificUi() }

        viewModel.participants.observe(viewLifecycleOwner) { list ->
            participantsAdapter.submitList(list)
            updateRoleSpecificUi()
        }

        viewModel.scheduleState.observe(viewLifecycleOwner) { resource ->
            updateScheduleButtonState()
            when (resource) {
                is Resource.Success -> {
                    Toast.makeText(requireContext(), "Carneação agendada! Confira em Perfil.", Toast.LENGTH_SHORT).show()
                }
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        // RF07 — cancelar um agendamento já confirmado.
        viewModel.cancelState.observe(viewLifecycleOwner) { resource ->
            updateScheduleButtonState()
            when (resource) {
                is Resource.Success ->
                    Toast.makeText(requireContext(), "Agendamento cancelado.", Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        // RF10 — "Finalizar Evento" (produtor).
        viewModel.finalizeState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success ->
                    Toast.makeText(requireContext(), getString(R.string.toast_event_finalized), Toast.LENGTH_LONG).show()
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        // RF10 — avaliação (nos dois sentidos: produtor↔comprador).
        viewModel.rateState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success ->
                    Toast.makeText(requireContext(), getString(R.string.toast_rating_sent), Toast.LENGTH_SHORT).show()
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        // Já agendado? (mesmo flag usado pra liberar Localização, RNF05) — troca o texto/estado
        // do botão de agendar e mostra o botão de cancelar.
        viewModel.locationUnlocked.observe(viewLifecycleOwner) { updateScheduleButtonState() }

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // RF07
        binding.btnSchedule.setOnClickListener { viewModel.schedule() }
        binding.btnCancelSchedule.setOnClickListener { viewModel.cancel() }

        // RF10 — botão "Finalizar Evento" (só o produtor dono do evento vê).
        binding.btnFinalizeEvent.setOnClickListener { showFinalizeConfirmDialog() }

        // RF10 — botão "Avaliar Produtor" (só o comprador vê, depois de finalizado).
        binding.btnRateProducer.setOnClickListener {
            showRatingDialog(getString(R.string.rate_producer_message)) { stars -> viewModel.rateProducer(stars) }
        }

        // RF08 — abre o chat do evento. Título mostra quem é o "outro lado" da conversa:
        // pro comprador, o nome do produtor; pro próprio produtor (que também pode abrir
        // esse chat pra ver as mensagens), como a conversa é única por evento (não é uma
        // thread por comprador), mostramos a cidade/UF do evento em vez do nome dele mesmo.
        binding.btnCallProducer.setOnClickListener {
            val event = viewModel.event.value ?: return@setOnClickListener
            val isProducer = event.producerId == FirebaseAuth.getInstance().currentUser?.uid
            val chatTitle = if (isProducer) "${event.city}/${event.state}" else event.producerName
            findNavController().navigate(
                EventDetailFragmentDirections.actionEventDetailToChat(
                    eventId = event.id,
                    producerId = event.producerId,
                    producerName = chatTitle
                )
            )
        }

        // RF09 — abre a localização no app de mapas do dispositivo, só depois de confirmar
        // presença (RNF05) ou se quem está olhando é o próprio produtor do evento.
        binding.btnLocation.setOnClickListener {
            val event = viewModel.event.value ?: return@setOnClickListener
            val lat = event.latitude
            val lng = event.longitude
            val isProducer = event.producerId == FirebaseAuth.getInstance().currentUser?.uid
            val unlocked = isProducer || viewModel.locationUnlocked.value == true

            when {
                lat == null || lng == null ->
                    Toast.makeText(requireContext(), "O produtor ainda não definiu a localização exata.", Toast.LENGTH_SHORT).show()
                !unlocked ->
                    Toast.makeText(
                        requireContext(),
                        "Localização exata disponível após confirmar presença (RNF05).",
                        Toast.LENGTH_SHORT
                    ).show()
                else -> {
                    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${event.address ?: event.city})")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        }
    }

    /**
     * RF07 — reflete no botão "Agendar Carneação"/"Carneação Agendada ✓" e no botão
     * "Cancelar Agendamento" (só visível depois de agendar) o estado atual, considerando os
     * LiveData que podem mudar isso: se já está agendado, se agendar/cancelar está em
     * andamento (pra não deixar clicar duas vezes) e — RF10 — se o evento já foi finalizado
     * pelo produtor, caso em que nem faz mais sentido agendar presença (a carneação já
     * aconteceu): os dois botões somem, ponto final, sem depender do estado de agendamento.
     */
    private fun updateScheduleButtonState() {
        if (currentEvent?.finalized == true) {
            binding.btnSchedule.visibility = View.GONE
            binding.btnCancelSchedule.visibility = View.GONE
            return
        }

        val scheduled = viewModel.locationUnlocked.value == true
        val loading = viewModel.scheduleState.value is Resource.Loading ||
            viewModel.cancelState.value is Resource.Loading
        binding.btnSchedule.visibility = View.VISIBLE
        binding.btnSchedule.isEnabled = !loading && !scheduled
        binding.btnSchedule.text =
            if (scheduled) getString(R.string.btn_already_scheduled)
            else getString(R.string.btn_schedule)
        binding.btnCancelSchedule.isEnabled = !loading
        binding.btnCancelSchedule.visibility = if (scheduled) View.VISIBLE else View.GONE
    }

    /**
     * RF10 — mostra/esconde tudo que depende de "sou o produtor desse evento?" e "o evento já
     * foi finalizado?": botão de finalizar (produtor, antes de finalizar), fotos (os dois
     * lados, depois de finalizar), botão de avaliar o produtor (comprador, depois de
     * finalizar e ainda sem avaliar) e a lista de compradores pra avaliar (produtor, depois
     * de finalizar). Chamado sempre que qualquer uma dessas três fontes de dado muda —
     * `event`, `myParticipation` ou `participants`.
     */
    private fun updateRoleSpecificUi() {
        val event = currentEvent ?: return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val isProducer = currentUserId != null && event.producerId == currentUserId

        binding.btnFinalizeEvent.visibility =
            if (isProducer && !event.finalized) View.VISIBLE else View.GONE

        val hasPhotos = event.finalized && event.photoBase64List.isNotEmpty()
        binding.photosTitle.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        binding.photosScroll.visibility = if (hasPhotos) View.VISIBLE else View.GONE
        if (hasPhotos) renderPhotos(event.photoBase64List)

        val myParticipation = viewModel.myParticipation.value
        val canRateProducer = !isProducer && event.finalized && myParticipation != null &&
            myParticipation.status == ParticipationStatus.CONCLUIDO && !myParticipation.alreadyRated
        binding.btnRateProducer.visibility = if (canRateProducer) View.VISIBLE else View.GONE

        val participants = viewModel.participants.value.orEmpty()
        val showParticipants = isProducer && event.finalized && participants.isNotEmpty()
        binding.participantsTitle.visibility = if (showParticipants) View.VISIBLE else View.GONE
        binding.participantsList.visibility = if (showParticipants) View.VISIBLE else View.GONE
    }

    /** RF10 — desenha as fotos (já decodificadas de Base64) num carrossel horizontal simples. */
    private fun renderPhotos(photosBase64: List<String>) {
        binding.photosContainer.removeAllViews()
        val sizePx = (96 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()
        for (base64 in photosBase64) {
            val bitmap = ImageUtils.base64ToBitmap(base64) ?: continue
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = marginPx }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(bitmap)
            }
            binding.photosContainer.addView(imageView)
        }
    }

    /** RF10 — confirma antes de finalizar (ação sem volta: conclui todas as participações
     * agendadas), com a opção de escolher fotos ou finalizar sem nenhuma. */
    private fun showFinalizeConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.finalize_confirm_title))
            .setMessage(getString(R.string.finalize_confirm_message))
            .setPositiveButton(getString(R.string.btn_choose_photos_and_finalize)) { _, _ ->
                pickPhotosLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            .setNeutralButton(getString(R.string.btn_finalize_without_photos)) { _, _ ->
                viewModel.finalizeEvent(emptyList())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** RF10 — processa (redimensiona/comprime/Base64, fora da main thread) as fotos escolhidas
     * no Photo Picker e só então finaliza o evento com elas. Lista vazia (usuário cancelou o
     * seletor sem escolher nada) não finaliza nada — ele volta pro botão "Finalizar Evento". */
    private fun onPhotosPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        Toast.makeText(requireContext(), "Processando fotos…", Toast.LENGTH_SHORT).show()
        fragmentScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                uris.mapNotNull { ImageUtils.uriToCompressedBase64(requireContext(), it) }
            }
            viewModel.finalizeEvent(encoded)
        }
    }

    /** RF10 — diálogo de avaliação em estrelas, reaproveitado tanto pro comprador avaliar o
     * produtor quanto pro produtor avaliar cada comprador. */
    private fun showRatingDialog(message: String, onRated: (Int) -> Unit) {
        val dialogBinding = DialogRateBinding.inflate(layoutInflater)
        dialogBinding.rateMessage.text = message

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.btn_rate))
            .setView(dialogBinding.root)
            .setPositiveButton("Enviar") { _, _ ->
                val stars = dialogBinding.ratingBar.rating.toInt().coerceIn(1, 5)
                onRated(stars)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fragmentScope.cancel()
        _binding = null
    }
}
