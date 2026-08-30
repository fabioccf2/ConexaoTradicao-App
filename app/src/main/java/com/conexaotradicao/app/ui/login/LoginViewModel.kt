package com.conexaotradicao.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conexaotradicao.app.data.repository.AuthRepository
import com.conexaotradicao.app.util.Resource
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableLiveData<Resource<Unit>>()
    val state: LiveData<Resource<Unit>> = _state

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = Resource.Error("Preencha e-mail e senha.")
            return
        }
        _state.value = Resource.Loading
        viewModelScope.launch {
            _state.value = authRepository.login(email, password)
        }
    }

    fun signInWithGoogle(idToken: String) {
        _state.value = Resource.Loading
        viewModelScope.launch {
            _state.value = authRepository.signInWithGoogle(idToken)
        }
    }
}
