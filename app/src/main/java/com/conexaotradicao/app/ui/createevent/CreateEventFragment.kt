package com.conexaotradicao.app.ui.createevent

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.conexaotradicao.app.data.model.Animal
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.conexaotradicao.app.databinding.FragmentCreateEventBinding
import com.conexaotradicao.app.databinding.ItemCutInputBinding
import com.conexaotradicao.app.util.Resource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tela de cadastro de evento pelo produtor (RF05). Acessada pelo botão "+" da Home. */
class CreateEventFragment : Fragment() {

    private var _binding: FragmentCreateEventBinding? = null
    private val binding get() = _binding!!

    private var selectedDateMillis: Long = 0L
    private val cutRows = mutableListOf<ItemCutInputBinding>()

    private var capturedLatitude: Double? = null
    private var capturedLongitude: Double? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                fetchCurrentLocation()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissão de localização negada. O evento será publicado sem localização exata.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val viewModel: CreateEventViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                CreateEventViewModel(
                    currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
                    eventRepository = RepositoryProvider.eventRepository(requireContext()),
                    userDao = RepositoryProvider.userDao(requireContext())
                ) as T
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        binding.inputDate.setOnClickListener { showDatePicker() }

        // Já começa com um corte em branco pra preencher
        addCutRow()
        binding.btnAddCut.setOnClickListener { addCutRow() }

        binding.btnUseLocation.setOnClickListener { requestLocation() }

        binding.btnPublish.setOnClickListener { publish() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }

        viewModel.state.observe(viewLifecycleOwner) { resource ->
            binding.progress.visibility = if (resource is Resource.Loading) View.VISIBLE else View.GONE
            binding.btnPublish.isEnabled = resource !is Resource.Loading

            when (resource) {
                is Resource.Success -> {
                    Toast.makeText(requireContext(), "Evento publicado!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day, 8, 0)
                selectedDateMillis = calendar.timeInMillis
                val format = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                binding.inputDate.setText(format.format(Date(selectedDateMillis)))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** RF09 — captura a localização exata do evento (revelada só após confirmação de presença, RNF05). */
    private fun requestLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun fetchCurrentLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        binding.locationStatus.text = "Obtendo localização…"
        // PRIORITY_HIGH_ACCURACY força o provedor de GPS, que é o que o emulador simula
        // via Extended Controls > Location — o provedor de rede muitas vezes não é mockado.
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                onLocationCaptured(location.latitude, location.longitude)
            } else {
                // getCurrentLocation às vezes retorna nulo em emulador; tenta a última
                // localização conhecida como fallback antes de desistir.
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) {
                            onLocationCaptured(last.latitude, last.longitude)
                        } else {
                            binding.locationStatus.text = "Não foi possível obter a localização agora. " +
                                "No emulador, confirme em Configurações > Localização que o serviço está " +
                                "ativado, defina a posição em Extended Controls > Location > Set Location " +
                                "e tente de novo."
                        }
                    }
                    .addOnFailureListener {
                        binding.locationStatus.text = "Não foi possível obter a localização agora. Tente novamente."
                    }
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Falha ao obter localização.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onLocationCaptured(latitude: Double, longitude: Double) {
        capturedLatitude = latitude
        capturedLongitude = longitude
        binding.locationStatus.text =
            "📍 Localização capturada — será revelada a quem confirmar presença (RNF05)."
    }

    private fun addCutRow() {
        val rowBinding = ItemCutInputBinding.inflate(layoutInflater, binding.cutsContainer, false)
        rowBinding.btnRemoveCut.setOnClickListener {
            binding.cutsContainer.removeView(rowBinding.root)
            cutRows.remove(rowBinding)
        }
        cutRows.add(rowBinding)
        binding.cutsContainer.addView(rowBinding.root)
    }

    private fun publish() {
        val animal = if (binding.radioPorco.isChecked) Animal.PORCO else Animal.GADO
        val city = binding.inputCity.text?.toString().orEmpty()
        val state = binding.inputState.text?.toString().orEmpty()
        val address = binding.inputAddress.text?.toString().orEmpty()

        val cuts = cutRows.map { row ->
            val name = row.inputCutName.text?.toString().orEmpty()
            val price = row.inputCutPrice.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            name to price
        }

        viewModel.publish(animal, selectedDateMillis, city, state, address, cuts, capturedLatitude, capturedLongitude)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cutRows.clear()
        _binding = null
    }
}
