package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.KnowledgeNetwork
import com.example.proyectwin.data.model.TrainingProgram
import com.example.proyectwin.domain.repository.CatalogsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogosData(
    val redes: List<KnowledgeNetwork> = emptyList(),
    val programas: List<TrainingProgram> = emptyList(),
)

sealed class CatalogosUiState {
    data object Loading : CatalogosUiState()
    data class Success(val data: CatalogosData) : CatalogosUiState()
    data class Error(val message: String) : CatalogosUiState()
}

sealed class CatalogosAction {
    data object Idle : CatalogosAction()
    data object Loading : CatalogosAction()
    data class Message(val text: String) : CatalogosAction()
    data class Error(val message: String) : CatalogosAction()
}

/** CRUD de redes de conocimiento y programas de formación (admin). */
@HiltViewModel
class CatalogosViewModel @Inject constructor(
    private val catalogsRepository: CatalogsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogosUiState>(CatalogosUiState.Loading)
    val uiState: StateFlow<CatalogosUiState> = _uiState.asStateFlow()

    private val _accion = MutableStateFlow<CatalogosAction>(CatalogosAction.Idle)
    val accion: StateFlow<CatalogosAction> = _accion.asStateFlow()

    fun resetAccion() {
        _accion.value = CatalogosAction.Idle
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = CatalogosUiState.Loading
            val redes = catalogsRepository.listarRedes()
            val programas = catalogsRepository.listarProgramas()
            val fallo = redes.exceptionOrNull() ?: programas.exceptionOrNull()
            if (fallo != null) {
                _uiState.value = CatalogosUiState.Error(fallo.message ?: "No se pudieron cargar los catálogos.")
            } else {
                _uiState.value = CatalogosUiState.Success(
                    CatalogosData(redes.getOrDefault(emptyList()), programas.getOrDefault(emptyList())),
                )
            }
        }
    }

    fun crearRed(nombre: String) = ejecutar("Red creada.") { catalogsRepository.crearRed(nombre) }
    fun actualizarRed(id: Int, nombre: String) = ejecutar("Red actualizada.") { catalogsRepository.actualizarRed(id, nombre) }
    fun eliminarRed(id: Int) = ejecutar("Red eliminada.") { catalogsRepository.eliminarRed(id) }

    fun crearPrograma(nombre: String, nivel: String, redId: Int) =
        ejecutar("Programa creado.") { catalogsRepository.crearPrograma(nombre, nivel, redId) }

    fun actualizarPrograma(id: Int, nombre: String, nivel: String, redId: Int) =
        ejecutar("Programa actualizado.") { catalogsRepository.actualizarPrograma(id, nombre, nivel, redId) }

    fun eliminarPrograma(id: Int) = ejecutar("Programa eliminado.") { catalogsRepository.eliminarPrograma(id) }

    private fun ejecutar(mensajeExito: String, bloque: suspend () -> Result<*>) {
        if (_accion.value is CatalogosAction.Loading) return // doble toque
        viewModelScope.launch {
            _accion.value = CatalogosAction.Loading
            bloque().fold(
                onSuccess = {
                    _accion.value = CatalogosAction.Message(mensajeExito)
                    load()
                },
                onFailure = { e ->
                    _accion.value = CatalogosAction.Error(e.message ?: "No se pudo completar la operación.")
                },
            )
        }
    }
}
