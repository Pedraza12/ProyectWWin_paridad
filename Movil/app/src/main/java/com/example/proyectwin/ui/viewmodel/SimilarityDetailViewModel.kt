package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de las observaciones de UNA propuesta del par (solo lectura). */
data class EstadoObservaciones(
    val cargando: Boolean = false,
    val comentarios: List<Comment> = emptyList(),
    val error: String? = null,
)

/** Detalle de un par: la propuesta propia y la contraparte (solo lectura). */
data class SimilarityDetailData(
    val similarity: Similarity,
    val relacionadas: List<Similarity> = emptyList(),
    val miProyecto: Project? = null,
    val contraparte: Project? = null,
    val ambasMias: Boolean = false,
    /** Observaciones por `id_proyecto` consultado (nunca se mezclan entre sí). */
    val observaciones: Map<Int, EstadoObservaciones> = emptyMap(),
)

sealed class SimilarityDetailUiState {
    data object Loading : SimilarityDetailUiState()
    data class Success(val data: SimilarityDetailData) : SimilarityDetailUiState()
    data class Error(val message: String) : SimilarityDetailUiState()
}

@HiltViewModel
class SimilarityDetailViewModel @Inject constructor(
    private val similaritiesRepository: SimilaritiesRepository,
    private val commentsRepository: CommentsRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SimilarityDetailUiState>(SimilarityDetailUiState.Loading)
    val uiState: StateFlow<SimilarityDetailUiState> = _uiState.asStateFlow()

    private var idActual: Int = 0

    fun load(id: Int) {
        idActual = id
        viewModelScope.launch {
            _uiState.value = SimilarityDetailUiState.Loading

            // Autorización real: el backend responde 403 si no puede ver el par.
            val par = similaritiesRepository.obtener(id).getOrElse { e ->
                _uiState.value = SimilarityDetailUiState.Error(e.message ?: "No se pudo cargar la similitud.")
                return@launch
            }

            val relacionadas = similaritiesRepository
                .listar(relatedTo = "${par.projectId1},${par.projectId2}")
                .getOrDefault(emptyList())
                .filter { it.id != par.id }

            val usuarioId = sessionManager.currentUser.first()?.id
            val mio1 = esPropia(par.project1, usuarioId)
            val mio2 = esPropia(par.project2, usuarioId)
            val miProyecto = if (mio1) par.project1 else if (mio2) par.project2 else null

            // Observaciones (paridad con la web `DetalleSimilitud`): se consultan
            // solo las de LA propuesta del usuario en el par. Sin propuesta propia
            // (p. ej. instructor), se consulta cada propuesta por separado para no
            // mezclar observaciones de proyectos distintos.
            val idsObservaciones = if (miProyecto != null) {
                listOf(miProyecto.id)
            } else {
                listOfNotNull(par.project1?.id, par.project2?.id)
            }

            _uiState.value = SimilarityDetailUiState.Success(
                SimilarityDetailData(
                    similarity = par,
                    relacionadas = relacionadas,
                    miProyecto = miProyecto,
                    contraparte = if (mio1) par.project2 else if (mio2) par.project1 else null,
                    ambasMias = mio1 && mio2,
                    observaciones = idsObservaciones.associateWith { EstadoObservaciones(cargando = true) },
                ),
            )

            idsObservaciones.forEach { cargarObservaciones(par.id, it) }
        }
    }

    fun retry() {
        if (idActual != 0) load(idActual)
    }

    /** Reintenta las observaciones de una propuesta tras un error recuperable. */
    fun reintentarObservaciones(proyectoId: Int) {
        val actual = _uiState.value as? SimilarityDetailUiState.Success ?: return
        cargarObservaciones(actual.data.similarity.id, proyectoId)
    }

    private fun cargarObservaciones(similarityId: Int, proyectoId: Int) {
        viewModelScope.launch {
            actualizarObservaciones(similarityId, proyectoId, EstadoObservaciones(cargando = true))
            commentsRepository.listar(idProyecto = proyectoId).fold(
                onSuccess = { comentarios ->
                    actualizarObservaciones(
                        similarityId,
                        proyectoId,
                        EstadoObservaciones(comentarios = comentarios),
                    )
                },
                onFailure = { e ->
                    actualizarObservaciones(
                        similarityId,
                        proyectoId,
                        EstadoObservaciones(
                            error = e.message ?: "No se pudieron cargar las observaciones.",
                        ),
                    )
                },
            )
        }
    }

    private fun actualizarObservaciones(similarityId: Int, proyectoId: Int, estado: EstadoObservaciones) {
        // Si el usuario ya navegó a otro par, la respuesta tardía se descarta.
        if (similarityId != idActual) return
        val actual = _uiState.value as? SimilarityDetailUiState.Success ?: return
        if (actual.data.similarity.id != similarityId) return
        _uiState.value = actual.copy(
            data = actual.data.copy(
                observaciones = actual.data.observaciones + (proyectoId to estado),
            ),
        )
    }

    private fun esPropia(proyecto: Project?, usuarioId: Int?): Boolean {
        if (proyecto == null || usuarioId == null) return false
        return proyecto.studentId == usuarioId || proyecto.equipo.any { it.id == usuarioId }
    }
}
