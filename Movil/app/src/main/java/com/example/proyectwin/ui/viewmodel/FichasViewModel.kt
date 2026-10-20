package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.ClassGroupStatus
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.domain.repository.FichasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FichasUiState {
    data object Loading : FichasUiState()
    data class Success(val fichas: List<Ficha>) : FichasUiState()
    data class Error(val message: String) : FichasUiState()
}

data class FichasActionState(
    val selectedFicha: Ficha? = null,
    val codigoValido: Boolean? = null,
    /** Código que el usuario digitó y que el servidor confirmó en la preview. */
    val codigoValidado: String? = null,
    val joinSuccess: Boolean = false,
    val leftSuccess: Boolean = false,
    val message: String? = null,
    /** Eliminación/anulación en curso (bloquea doble toque). */
    val eliminando: Boolean = false,
)

@HiltViewModel
class FichasViewModel @Inject constructor(
    private val fichasRepository: FichasRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FichasUiState>(FichasUiState.Loading)
    val uiState: StateFlow<FichasUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow(FichasActionState())
    val actionState: StateFlow<FichasActionState> = _actionState.asStateFlow()

    /** Detalle (roster) por ficha, cargado bajo demanda. */
    private val _detalles = MutableStateFlow<Map<Int, Ficha>>(emptyMap())
    val detalles: StateFlow<Map<Int, Ficha>> = _detalles.asStateFlow()

    fun cargarDetalle(fichaId: Int) {
        if (_detalles.value.containsKey(fichaId)) return
        viewModelScope.launch {
            fichasRepository.obtener(fichaId).onSuccess { ficha ->
                _detalles.value = _detalles.value + (fichaId to ficha)
            }
        }
    }

    /** Recarga silenciosa para volver de editar sin perder scroll ni expansión. */
    fun refrescar() {
        viewModelScope.launch {
            _detalles.value = emptyMap()
            fichasRepository.listar().onSuccess { fichas ->
                _uiState.value = FichasUiState.Success(fichas)
            }
        }
    }

    fun loadAllFichas() {
        viewModelScope.launch {
            _uiState.value = FichasUiState.Loading
            fichasRepository.listar().fold(
                onSuccess = { fichas -> _uiState.value = FichasUiState.Success(fichas) },
                onFailure = { e ->
                    _uiState.value = FichasUiState.Error(e.message ?: "Error al cargar las fichas")
                },
            )
        }
    }

    fun loadActiveFichas() {
        viewModelScope.launch {
            _uiState.value = FichasUiState.Loading
            fichasRepository.listar().fold(
                onSuccess = { fichas ->
                    _uiState.value = FichasUiState.Success(
                        fichas.filter { it.estado == ClassGroupStatus.ACTIVO.value },
                    )
                },
                onFailure = { e ->
                    _uiState.value = FichasUiState.Error(e.message ?: "Error al cargar las fichas")
                },
            )
        }
    }

    fun loadFichaById(id: Int) {
        viewModelScope.launch {
            _uiState.value = FichasUiState.Loading
            fichasRepository.obtener(id).fold(
                onSuccess = { ficha ->
                    _actionState.value = _actionState.value.copy(selectedFicha = ficha)
                    _uiState.value = FichasUiState.Success(listOf(ficha))
                },
                onFailure = { e ->
                    _uiState.value = FichasUiState.Error(e.message ?: "Error al cargar la ficha")
                },
            )
        }
    }

    /** Valida el código contra el servidor (misma preview que el frontend). */
    fun validarCodigo(codigo: String) {
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(
                codigoValido = null,
                codigoValidado = null,
                selectedFicha = null,
            )
            fichasRepository.fichaPorCodigo(codigo).fold(
                onSuccess = { ficha ->
                    _actionState.value = _actionState.value.copy(
                        codigoValido = true,
                        // Se conserva el texto digitado: la preview puede no
                        // incluir `codigo` y no debemos unirnos con vacío.
                        codigoValidado = codigo,
                        selectedFicha = ficha,
                    )
                },
                onFailure = {
                    _actionState.value = _actionState.value.copy(
                        codigoValido = false,
                        codigoValidado = null,
                        selectedFicha = null,
                    )
                },
            )
        }
    }

    /**
     * Une a la ficha usando el código ya validado en la preview. No depende de
     * `selectedFicha.codigo` (el backend puede omitirlo en la respuesta).
     */
    fun joinFicha(codigo: String) {
        viewModelScope.launch {
            if (codigo.isBlank() || _actionState.value.selectedFicha == null) {
                _actionState.value = _actionState.value.copy(
                    message = "Valida el código de la ficha antes de unirte.",
                )
                return@launch
            }
            fichasRepository.unirmeAFicha(codigo).fold(
                onSuccess = { ficha ->
                    _actionState.value = _actionState.value.copy(
                        selectedFicha = ficha,
                        joinSuccess = true,
                        message = null,
                    )
                },
                onFailure = { e ->
                    _actionState.value = _actionState.value.copy(
                        message = e.message ?: "Error al unirse a la ficha",
                    )
                },
            )
        }
    }

    /**
     * Elimina o anula la ficha (admin cualquiera; instructor solo las suyas).
     * El backend decide: con aprendices/propuestas queda anulada; sin ellos se
     * borra físicamente. La lista se refresca con el resultado real.
     */
    fun eliminarFicha(fichaId: Int) {
        if (_actionState.value.eliminando) return // doble toque
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(eliminando = true, message = null)
            fichasRepository.eliminar(fichaId).fold(
                onSuccess = { resultado ->
                    _actionState.value = _actionState.value.copy(
                        eliminando = false,
                        message = if (resultado.anulada) {
                            "La ficha tenía aprendices o propuestas: quedó anulada."
                        } else {
                            "Ficha eliminada."
                        },
                    )
                    refrescar()
                },
                onFailure = { e ->
                    _actionState.value = _actionState.value.copy(
                        eliminando = false,
                        message = e.message ?: "No se pudo eliminar la ficha.",
                    )
                },
            )
        }
    }

    /** Salir de la ficha actual (el backend desvincula y notifica al instructor). */
    fun salirDeFicha() {
        viewModelScope.launch {
            _actionState.value = _actionState.value.copy(message = null, leftSuccess = false)
            fichasRepository.salirDeFicha().fold(
                onSuccess = {
                    _actionState.value = _actionState.value.copy(leftSuccess = true)
                },
                onFailure = { e ->
                    _actionState.value = _actionState.value.copy(
                        message = e.message ?: "No se pudo salir de la ficha",
                    )
                },
            )
        }
    }

    fun clearJoinSuccess() {
        _actionState.value = _actionState.value.copy(joinSuccess = false)
    }

    fun clearLeftSuccess() {
        _actionState.value = _actionState.value.copy(leftSuccess = false)
    }

    fun clearError() {
        _actionState.value = _actionState.value.copy(message = null)
        if (_uiState.value is FichasUiState.Error) {
            _uiState.value = FichasUiState.Loading
        }
    }
}
