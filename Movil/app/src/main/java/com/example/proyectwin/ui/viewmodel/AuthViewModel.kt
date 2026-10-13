package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object Loading : AuthUiState()
    data object LoggedOut : AuthUiState()
    data class LoggedIn(val user: GeneralUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/** Estado de una operación puntual (cambio de clave, recuperación, etc.). */
sealed class AuthActionState {
    data object Idle : AuthActionState()
    data object Loading : AuthActionState()
    data object Success : AuthActionState()
    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AuthActionState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _passwordChange = MutableStateFlow<AuthActionState>(AuthActionState.Idle)
    val passwordChange: StateFlow<AuthActionState> = _passwordChange.asStateFlow()

    private val _forgotState = MutableStateFlow<AuthActionState>(AuthActionState.Idle)
    val forgotState: StateFlow<AuthActionState> = _forgotState.asStateFlow()

    private val _resetState = MutableStateFlow<AuthActionState>(AuthActionState.Idle)
    val resetState: StateFlow<AuthActionState> = _resetState.asStateFlow()

    /** Mensaje informativo del último envío (p. ej. reset_url en modo local). */
    private val _forgotMessage = MutableStateFlow<String?>(null)
    val forgotMessage: StateFlow<String?> = _forgotMessage.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.value = if (user != null) AuthUiState.LoggedIn(user) else AuthUiState.LoggedOut
            }
        }
        // Revalida el token contra /auth/me al abrir la app. Si el servidor no
        // responde se conserva la sesión local; un 401 (token vencido/revocado)
        // o un 403 de /auth/me (cuenta suspendida) invalidan la sesión con el
        // logout central, sin depender del evento global (que puede perderse
        // si se emite antes de que la UI lo esté observando).
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            if (user != null) {
                val error = authRepository.refreshSession().exceptionOrNull()
                if (error is ApiException.Http && (error.status == 401 || error.status == 403)) {
                    logout()
                }
            }
        }
    }

    fun login(username: String, password: String) {
        if (_isSubmitting.value) return // doble toque
        viewModelScope.launch {
            _isSubmitting.value = true
            authRepository.login(username, password).fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.LoggedIn(user)
                },
                onFailure = { e ->
                    _uiState.value = AuthUiState.Error(e.message ?: "Error de conexión")
                },
            )
            _isSubmitting.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState.LoggedOut
        }
    }

    /** 403 del middleware: la sesión sigue, pero hay que cambiar la temporal. */
    fun markMustChangePassword() {
        viewModelScope.launch {
            sessionManager.setMustChangePassword(true)
            val actual = _uiState.value
            if (actual is AuthUiState.LoggedIn && !actual.user.mustChangePassword) {
                _uiState.value = actual.copy(user = actual.user.copy(mustChangePassword = true))
            }
        }
    }

    fun changePassword(passwordActual: String, password: String, passwordConfirmation: String) {
        if (_passwordChange.value == AuthActionState.Loading) return // doble toque
        viewModelScope.launch {
            _passwordChange.value = AuthActionState.Loading
            authRepository.changePassword(passwordActual, password, passwordConfirmation).fold(
                onSuccess = {
                    _passwordChange.value = AuthActionState.Success
                    val actual = _uiState.value
                    if (actual is AuthUiState.LoggedIn) {
                        _uiState.value = actual.copy(user = actual.user.copy(mustChangePassword = false))
                    }
                },
                onFailure = { e ->
                    val fieldErrors = (e as? com.example.proyectwin.data.api.ApiException.Http)?.fieldErrors.orEmpty()
                    _passwordChange.value = AuthActionState.Error(
                        e.message ?: "No se pudo cambiar la contraseña.",
                        fieldErrors,
                    )
                },
            )
        }
    }

    fun forgotPassword(correo: String) {
        if (_forgotState.value == AuthActionState.Loading) return
        viewModelScope.launch {
            _forgotState.value = AuthActionState.Loading
            _forgotMessage.value = null
            authRepository.forgotPassword(correo).fold(
                onSuccess = { mensaje ->
                    _forgotMessage.value = mensaje
                    _forgotState.value = AuthActionState.Success
                },
                onFailure = { e ->
                    _forgotState.value = AuthActionState.Error(e.message ?: "No se pudo enviar el enlace.")
                },
            )
        }
    }

    fun resetPassword(correo: String, token: String, password: String, passwordConfirmation: String) {
        if (_resetState.value == AuthActionState.Loading) return
        viewModelScope.launch {
            _resetState.value = AuthActionState.Loading
            authRepository.resetPassword(token, correo, password, passwordConfirmation).fold(
                onSuccess = { _resetState.value = AuthActionState.Success },
                onFailure = { e ->
                    val fieldErrors = (e as? com.example.proyectwin.data.api.ApiException.Http)?.fieldErrors.orEmpty()
                    _resetState.value = AuthActionState.Error(
                        e.message ?: "No se pudo restablecer la contraseña.",
                        fieldErrors,
                    )
                },
            )
        }
    }

    fun resetPasswordChangeState() {
        _passwordChange.value = AuthActionState.Idle
    }

    fun resetForgotState() {
        _forgotState.value = AuthActionState.Idle
        _forgotMessage.value = null
    }

    fun resetResetState() {
        _resetState.value = AuthActionState.Idle
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.LoggedOut
        }
    }

    suspend fun getToken(): String? = sessionManager.getToken()

    fun getSessionManager(): SessionManager = sessionManager
}
