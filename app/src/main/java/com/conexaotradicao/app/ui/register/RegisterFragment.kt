package com.conexaotradicao.app.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.conexaotradicao.app.AuthActivity
import com.conexaotradicao.app.data.repository.RepositoryProvider
import com.conexaotradicao.app.databinding.FragmentRegisterBinding
import com.conexaotradicao.app.util.Resource

/** Cadastro de novo usuário (RF01) — segunda metade da Tela 1. */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                RegisterViewModel(RepositoryProvider.authRepository(requireContext())) as T
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener {
            viewModel.register(
                binding.inputName.text?.toString().orEmpty(),
                binding.inputEmail.text?.toString().orEmpty(),
                binding.inputPassword.text?.toString().orEmpty()
            )
        }

        binding.labelHasAccount.setOnClickListener {
            findNavController().navigate(RegisterFragmentDirections.actionRegisterToLogin())
        }

        viewModel.state.observe(viewLifecycleOwner) { resource ->
            binding.progress.visibility = if (resource is Resource.Loading) View.VISIBLE else View.GONE
            binding.btnRegister.isEnabled = resource !is Resource.Loading

            when (resource) {
                is Resource.Success -> (requireActivity() as AuthActivity).goToMain()
                is Resource.Error -> Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
