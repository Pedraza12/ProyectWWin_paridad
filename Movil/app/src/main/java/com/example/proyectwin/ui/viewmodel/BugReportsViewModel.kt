package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.model.BugReport
import com.example.proyectwin.data.model.BugReportStatus
import com.example.proyectwin.domain.repository.BugReportsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BugReportsUiState {
    data object Loading : BugReportsUiState()
    data class Success(val reports: List<BugReport>) : BugReportsUiState()
    data class Error(val message: String) : BugReportsUiState()
}

/**
 * Listado, detalle, alta y cambio de estado de reportes de falla.
 * El backend ya acorta `GET /bug-reports` al usuario del token (admin ve todos).
 */
@HiltViewModel
class BugReportsViewModel @Inject constructor(
    private val bugReportsRepository: BugReportsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BugReportsUiState>(BugReportsUiState.Loading)
    val uiState: StateFlow<BugReportsUiState> = _uiState.asStateFlow()

    private val _detalle = MutableStateFlow<BugReport?>(null)
    val detalle: StateFlow<BugReport?> = _detalle.asStateFlow()

    /** Error de la carga del detalle: evita el spinner infinito sin reintento. */
    private val _detalleError = MutableStateFlow<String?>(null)
    val detalleError: StateFlow<String?> = _detalleError.asStateFlow()

    private val _accionError = MutableStateFlow<String?>(null)
    val accionError: StateFlow<String?> = _accionError.asStateFlow()

    private val _creado = MutableStateFlow(false)
    val creado: StateFlow<Boolean> = _creado.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = BugReportsUiState.Loading
            bugReportsRepository.listar().fold(
                onSuccess = { lista -> _uiState.value = BugReportsUiState.Success(lista) },
                onFailure = { e ->
                    _uiState.value = BugReportsUiState.Error(
                        e.message ?: "Error al cargar los reportes",
                    )
                },
            )
        }
    }

    fun cargarDetalle(id: Int) {
        viewModelScope.launch {
            _detalle.value = null
            _detalleError.value = null
            bugReportsRepository.obtener(id).fold(
                onSuccess = { _detalle.value = it },
                onFailure = { e ->
                    _detalleError.value = e.message ?: "No se pudo cargar el reporte."
                },
            )
        }
    }

    /** PUT completo con estado y respuesta (como hace el admin del frontend). */
    fun cambiarEstado(id: Int, estado: BugReportStatus, respuesta: String? = null) {
        viewModelScope.launch {
            _accionError.value = null
            val actual = bugReportsRepository.obtener(id).getOrElse { e ->
                _accionError.value = e.message ?: "Error al actualizar el reporte"
                return@launch
            }
            bugReportsRepository.actualizar(
                id,
                actual.copy(estado = estado.value, respuesta = respuesta ?: actual.respuesta),
            ).fold(
                onSuccess = { actualizado ->
                    _detalle.value = actualizado
                    load()
                },
                onFailure = { e ->
                    _accionError.value = e.message ?: "Error al actualizar el reporte"
                },
            )
        }
    }

    fun eliminar(id: Int, onEliminado: () -> Unit = {}) {
        viewModelScope.launch {
            _accionError.value = null
            bugReportsRepository.eliminar(id).fold(
                onSuccess = {
                    load()
                    onEliminado()
                },
                onFailure = { e -> _accionError.value = e.message ?: "Error al eliminar el reporte" },
            )
        }
    }

    fun crear(
        titulo: String?,
        descripcion: String,
        tipo: String,
        numeroFicha: String? = null,
        motivo: String? = null,
    ) {
        viewModelScope.launch {
            _accionError.value = null
            _creado.value = false
            bugReportsRepository.crear(titulo, descripcion, tipo, numeroFicha, motivo).fold(
                onSuccess = {
                    _creado.value = true
                    load()
                },
                onFailure = { e ->
                    _accionError.value = when {
                        e is ApiException.Http && e.fieldErrors.isNotEmpty() ->
                            e.fieldErrors.values.joinToString(" ")
                        else -> e.message ?: "Error al crear el reporte"
                    }
                },
            )
        }
    }

    fun limpiarCreado() {
        _creado.value = false
    }

    fun limpiarError() {
        _accionError.value = null
    }
}
