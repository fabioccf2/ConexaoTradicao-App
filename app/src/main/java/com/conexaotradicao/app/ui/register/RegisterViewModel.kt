package com.conexaotradicao.app.ui.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.repository.AuthRepository
import com.conexaotradicao.app.util.Resource
import kotlinx.coroutines.launch

class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun register(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.length < 6) {
            _state.value = Resource.Error("Preencha nome, e-mail e uma senha com 6+ caracteres.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            _state.value = authRepository.register(name, email, password)
        }
    }
}
