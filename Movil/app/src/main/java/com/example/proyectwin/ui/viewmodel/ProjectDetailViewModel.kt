package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.ApprenticeProfile
import com.example.proyectwin.data.model.ClassGroupStatus
import com.example.proyectwin.data.model.Comment
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.ProjectHistory
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.data.model.TeamMember
import com.example.proyectwin.data.model.ProjectStatus
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.data.mapper.toDraft
import com.example.proyectwin.domain.repository.CommentsRepository
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.MembersRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Todo lo que compone el detalle de una propuesta. */
data class ProjectDetailData(
    val project: Project,
    val equipo: List<TeamMember> = emptyList(),
    val comentarios: List<Comment> = emptyList(),
    val historial: List<ProjectHistory> = emptyList(),
    /** Compañeros de la ficha que aún no están en el equipo (ids de aprendiz). */
    val candidatos: List<ApprenticeProfile> = emptyList(),
    /** Coincidencias vigentes del motor para esta propuesta. */
    val similitudes: List<Similarity> = emptyList(),
    /** Evidencia histórica (`vigente=false`) de versiones anteriores. */
    val similitudesHistoricas: List<Similarity> = emptyList(),
    val esCreador: Boolean = false,
    val perteneceAlEquipo: Boolean = false,
    val fichaActiva: Boolean = false,
    val esAdmin: Boolean = false,
    /** Instructor asignado a la propuesta o responsable de su ficha. */
    val esInstructorACargo: Boolean = false,
    /** Usuario autenticado (para saber si una observación es propia). */
    val usuarioActualId: Int? = null,
) {
    /** Editable por el aprendiz cuando la propuesta y su ficha lo permiten. */
    val puedeEditar: Boolean
        get() = esAdmin || ((esCreador || perteneceAlEquipo) && fichaActiva && project.esEditable)

    val puedeEnviar: Boolean
        get() = esAdmin || ((esCreador || perteneceAlEquipo) && fichaActiva && project.puedeEnviar)

    /** El equipo solo lo administra el creador (regla del backend). */
    val puedeGestionarEquipo: Boolean
        get() = esAdmin || (esCreador && fichaActiva && project.esEditable)

    /** Comentar exige `puedeEscribir` (ficha activa), sin importar el estado. */
    val puedeComentar: Boolean
        get() = esAdmin || esInstructorACargo || ((esCreador || perteneceAlEquipo) && fichaActiva)

    val puedeEliminar: Boolean
        get() = esAdmin || (esCreador && fichaActiva)

    /** Revisar (aprobar/rechazar) solo en revisión y dentro de su alcance. */
    val puedeRevisar: Boolean
        get() = (esAdmin || esInstructorACargo) &&
            ProjectStatus.fromValue(project.estado) == ProjectStatus.PENDIENTE

    /**
     * Editar/eliminar una observación: el autor (o un admin). Para no-admin el
     * backend exige además `puedeEscribir` (ficha activa), igual que comentar.
     */
    fun puedeGestionarComentario(comentario: Comment): Boolean =
        esAdmin || (comentario.usuarioId == usuarioActualId && puedeComentar)
}

sealed class ProjectDetailUiState {
    data object Loading : ProjectDetailUiState()
    data class Success(val data: ProjectDetailData) : ProjectDetailUiState()
    data class Error(val message: String) : ProjectDetailUiState()
}

/** Acciones del detalle (doble-submit protegido por [ProjectActionState.Loading]). */
sealed class ProjectActionState {
    data object Idle : ProjectActionState()
    data object Loading : ProjectActionState()
    data object Success : ProjectActionState()
    data object Deleted : ProjectActionState()
    data class Error(val message: String) : ProjectActionState()
}

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectsRepository: ProjectsRepository,
    private val commentsRepository: CommentsRepository,
    private val similaritiesRepository: SimilaritiesRepository,
    private val membersRepository: MembersRepository,
    private val fichasRepository: FichasRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectDetailUiState>(ProjectDetailUiState.Loading)
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    private val _accion = MutableStateFlow<ProjectActionState>(ProjectActionState.Idle)
    val accion: StateFlow<ProjectActionState> = _accion.asStateFlow()

    private var proyectoId: Int = 0

    fun resetAccion() {
        _accion.value = ProjectActionState.Idle
    }

    fun load(id: Int) {
        proyectoId = id
        viewModelScope.launch {
            _uiState.value = ProjectDetailUiState.Loading
            val proyecto = projectsRepository.obtener(id).getOrElse { e ->
                _uiState.value = ProjectDetailUiState.Error(e.message ?: "No se pudo cargar la propuesta.")
                return@launch
            }

            val equipo = projectsRepository.equipo(id).getOrDefault(emptyList())
            val comentarios = commentsRepository.listar(id).getOrDefault(emptyList())
            val historial = projectsRepository.historial(id).getOrDefault(emptyList())
            // El motor: vigentes del proyecto e históricas autorizadas.
            val similitudes = similaritiesRepository.listar(proyectoId = id).getOrDefault(emptyList())
            val historicas = similaritiesRepository.listar(proyectoId = id, historial = true).getOrDefault(emptyList())
            val usuario = sessionManager.currentUser.first()

            // Candidatos vía /apprentices (accesible al aprendiz); /general-users
            // es solo de admin y devolvía 403 para los demás roles.
            val candidatos = if (proyecto.fichaId != null) {
                membersRepository.listarAprendices()
                    .getOrDefault(emptyList())
                    .filter { candidato ->
                        candidato.idClassGroup == proyecto.fichaId &&
                            candidato.idUsuario != usuario?.id &&
                            equipo.none { it.usuario.id == candidato.idUsuario }
                    }
            } else {
                emptyList()
            }

            // Instructor: asignado a la propuesta o responsable de su ficha.
            val esInstructorACargo = if (usuario?.role == UserRole.INSTRUCTOR.value) {
                val miFila = membersRepository.instructorPorUsuario(usuario.id).getOrNull()
                val asignado = miFila != null && proyecto.instructorId == miFila.id
                val deSuFicha = !asignado && miFila != null && proyecto.fichaId != null &&
                    fichasRepository.obtener(proyecto.fichaId).getOrNull()?.instructorId == miFila.id
                asignado || deSuFicha
            } else {
                false
            }

            _uiState.value = ProjectDetailUiState.Success(
                ProjectDetailData(
                    project = proyecto,
                    equipo = equipo,
                    comentarios = comentarios,
                    historial = historial,
                    candidatos = candidatos,
                    similitudes = similitudes,
                    similitudesHistoricas = historicas,
                    esCreador = usuario?.id != null && usuario.id == proyecto.studentId,
                    perteneceAlEquipo = equipo.any { it.usuario.id == usuario?.id },
                    fichaActiva = proyecto.fichaEstado == null ||
                        proyecto.fichaEstado == ClassGroupStatus.ACTIVO.value,
                    esAdmin = usuario?.role == UserRole.ADMINISTRADOR.value,
                    esInstructorACargo = esInstructorACargo,
                    usuarioActualId = usuario?.id,
                ),
            )
        }
    }

    fun recargar() {
        if (proyectoId != 0) load(proyectoId)
    }

    fun enviar() {
        ejecutar { projectsRepository.enviar(proyectoId) }
    }

    /** Aprobación/rechazo (instructor a cargo o admin) con observación opcional. */
    fun revisar(aprobar: Boolean, observacion: String? = null) {
        val actual = (_uiState.value as? ProjectDetailUiState.Success)?.data ?: return
        val estado = if (aprobar) ProjectStatus.APROBADO.value else ProjectStatus.RECHAZADO.value
        ejecutar {
            projectsRepository.actualizar(
                proyectoId,
                actual.project.toDraft().copy(
                    estado = estado,
                    observacion = observacion?.trim()?.ifBlank { null },
                ),
            )
        }
    }

    fun eliminar() {
        if (_accion.value is ProjectActionState.Loading) return
        viewModelScope.launch {
            _accion.value = ProjectActionState.Loading
            projectsRepository.eliminar(proyectoId).fold(
                onSuccess = { _accion.value = ProjectActionState.Deleted },
                onFailure = { e ->
                    _accion.value = ProjectActionState.Error(e.message ?: "No se pudo eliminar la propuesta.")
                },
            )
        }
    }

    fun comentar(texto: String, respuestaA: Int? = null) {
        ejecutar { commentsRepository.crear(proyectoId, texto, respuestaA) }
    }

    /** Edición del propio texto (o de un admin); el backend valida permisos. */
    fun editarComentario(id: Int, idProyecto: Int, texto: String, respuestaA: Int?) {
        ejecutar { commentsRepository.actualizar(id, idProyecto, texto, respuestaA) }
    }

    /** Elimina la observación (el hilo de respuestas cae en cascada). */
    fun eliminarComentario(id: Int) {
        ejecutar { commentsRepository.eliminar(id) }
    }

    fun agregarIntegrante(idAprendiz: Int) {
        ejecutar { projectsRepository.agregarAlEquipo(proyectoId, idAprendiz) }
    }

    fun quitarIntegrante(idPivote: Int) {
        ejecutar { projectsRepository.quitarDelEquipo(idPivote) }
    }

    /** Ejecuta una acción y recarga el detalle conservando el resultado para la UI. */
    private fun ejecutar(bloque: suspend () -> Result<*>) {
        if (_accion.value is ProjectActionState.Loading) return // doble toque
        viewModelScope.launch {
            _accion.value = ProjectActionState.Loading
            bloque().fold(
                onSuccess = {
                    _accion.value = ProjectActionState.Success
                    recargar()
                },
                onFailure = { e ->
                    _accion.value = ProjectActionState.Error(
                        (e as? ApiException)?.message ?: "No se pudo completar la acción.",
                    )
                },
            )
        }
    }
}
