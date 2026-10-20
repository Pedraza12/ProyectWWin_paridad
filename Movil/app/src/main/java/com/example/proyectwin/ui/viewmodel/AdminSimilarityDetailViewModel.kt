package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminSimilarityData(
    val similarity: Similarity,
    val relacionadas: List<Similarity> = emptyList(),
    val comentarios1: List<Comment> = emptyList(),
    val comentarios2: List<Comment> = emptyList(),
)

sealed class AdminSimilarityUiState {
    data object Loading : AdminSimilarityUiState()
    data class Success(val data: AdminSimilarityData) : AdminSimilarityUiState()
    data class Error(val message: String) : AdminSimilarityUiState()
}

sealed class AdminSimilarityAction {
    data object Idle : AdminSimilarityAction()
    data object Loading : AdminSimilarityAction()
    data object Success : AdminSimilarityAction()
    data object Deleted : AdminSimilarityAction()
    data class Error(val message: String) : AdminSimilarityAction()
}

/** Detalle admin del par: ambas propuestas, observaciones y eliminación. */
@HiltViewModel
class AdminSimilarityDetailViewModel @Inject constructor(
    private val similaritiesRepository: SimilaritiesRepository,
    private val commentsRepository: CommentsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminSimilarityUiState>(AdminSimilarityUiState.Loading)
    val uiState: StateFlow<AdminSimilarityUiState> = _uiState.asStateFlow()

    private val _accion = MutableStateFlow<AdminSimilarityAction>(AdminSimilarityAction.Idle)
    val accion: StateFlow<AdminSimilarityAction> = _accion.asStateFlow()

    /** Refresco silencioso en curso (pull-to-refresh), sin reemplazar el contenido. */
    private val _refrescando = MutableStateFlow(false)
    val refrescando: StateFlow<Boolean> = _refrescando.asStateFlow()

    private var idPar: Int = 0

    fun resetAccion() {
        _accion.value = AdminSimilarityAction.Idle
    }

    fun load(id: Int) {
        idPar = id
        viewModelScope.launch { cargar(id, mostrarCarga = true) }
    }

    fun recargar() {
        if (idPar != 0) load(idPar)
    }

    /** Refresco silencioso (pull-to-refresh): mantiene el contenido visible. */
    fun refrescar() {
        val id = idPar
        if (id == 0 || _refrescando.value) return
        viewModelScope.launch {
            _refrescando.value = true
            try {
                cargar(id, mostrarCarga = false)
            } finally {
                _refrescando.value = false
            }
        }
    }

    private suspend fun cargar(id: Int, mostrarCarga: Boolean) {
        if (mostrarCarga) _uiState.value = AdminSimilarityUiState.Loading

        val par = similaritiesRepository.obtener(id).getOrElse { e ->
            if (mostrarCarga && idPar == id) {
                _uiState.value = AdminSimilarityUiState.Error(e.message ?: "No se pudo cargar la similitud.")
            }
            return
        }
        val relacionadas = similaritiesRepository
            .listar(relatedTo = "${par.projectId1},${par.projectId2}")
            .getOrDefault(emptyList())
            .filter { it.id != par.id }
        val comentarios1 = commentsRepository.listar(par.projectId1).getOrDefault(emptyList())
        val comentarios2 = commentsRepository.listar(par.projectId2).getOrDefault(emptyList())

        // Si el usuario ya navegó a otro par, la respuesta tardía se descarta.
        if (idPar != id) return

        _uiState.value = AdminSimilarityUiState.Success(
            AdminSimilarityData(
                similarity = par,
                relacionadas = relacionadas,
                comentarios1 = comentarios1,
                comentarios2 = comentarios2,
            ),
        )
    }

    fun comentar(idProyecto: Int, texto: String) {
        ejecutar(recargarTrasExito = true) { commentsRepository.crear(idProyecto, texto) }
    }

    fun eliminar() {
        if (_accion.value is AdminSimilarityAction.Loading) return
        viewModelScope.launch {
            _accion.value = AdminSimilarityAction.Loading
            similaritiesRepository.eliminar(idPar).fold(
                onSuccess = { _accion.value = AdminSimilarityAction.Deleted },
                onFailure = { e ->
                    _accion.value = AdminSimilarityAction.Error(e.message ?: "No se pudo eliminar el par.")
                },
            )
        }
    }

    private fun ejecutar(recargarTrasExito: Boolean, bloque: suspend () -> Result<*>) {
        if (_accion.value is AdminSimilarityAction.Loading) return
        viewModelScope.launch {
            _accion.value = AdminSimilarityAction.Loading
            bloque().fold(
                onSuccess = {
                    _accion.value = AdminSimilarityAction.Success
                    if (recargarTrasExito) recargar()
                },
                onFailure = { e ->
                    _accion.value = AdminSimilarityAction.Error(e.message ?: "No se pudo completar la acción.")
                },
            )
        }
    }
}
