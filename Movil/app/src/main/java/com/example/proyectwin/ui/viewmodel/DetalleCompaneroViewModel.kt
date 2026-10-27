package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.MembersRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import com.example.proyectwin.domain.repository.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

/** Vista de un compañero/instructor con lo que el backend autoriza mostrar. */
data class CompaneroData(
    val usuario: GeneralUser,
    val ficha: Ficha? = null,
    val proyectos: List<Project> = emptyList(),
    /** Similitud máxima vigente por propuesta (mismo criterio que la web). */
    val porcentajesSimilitud: Map<Int, Int> = emptyMap(),
)

sealed class CompaneroUiState {
    data object Loading : CompaneroUiState()
    data class Success(val data: CompaneroData) : CompaneroUiState()
    data class Error(val message: String) : CompaneroUiState()
}

@HiltViewModel
class DetalleCompaneroViewModel @Inject constructor(
    private val usersRepository: UsersRepository,
    private val fichasRepository: FichasRepository,
    private val projectsRepository: ProjectsRepository,
    private val membersRepository: MembersRepository,
    private val similaritiesRepository: SimilaritiesRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CompaneroUiState>(CompaneroUiState.Loading)
    val uiState: StateFlow<CompaneroUiState> = _uiState.asStateFlow()

    private var idUsuario: Int = 0

    fun load(id: Int) {
        idUsuario = id
        viewModelScope.launch {
            _uiState.value = CompaneroUiState.Loading

            // Perfil público: el backend valida el alcance (misma ficha, etc.).
            val perfil = usersRepository.perfil(id).getOrElse { e ->
                _uiState.value = CompaneroUiState.Error(e.message ?: "No se pudo cargar el perfil.")
                return@launch
            }

            // La ficha mostrada debe ser la del usuario consultado, no la del
            // solicitante (en la web, `DetalleCompanero` resuelve la fila del
            // aprendiz consultado). Para un instructor solo se puede mostrar la
            // ficha compartida con el aprendiz (la API acota class-groups).
            val ficha = when (perfil.role) {
                UserRole.APRENDIZ.value -> membersRepository.aprendizPorUsuario(id)
                    .getOrNull()
                    ?.idClassGroup
                    ?.let { fichasRepository.obtener(it).getOrNull() }

                UserRole.INSTRUCTOR.value -> sessionManager.currentUser.first()
                    ?.fichaId
                    ?.let { fichasRepository.obtener(it).getOrNull() }
                    ?.takeIf { it.instructorUsuarioId == id }

                else -> null
            }

            // Solo proyectos visibles para el solicitante; se filtra por autor/equipo.
            val proyectos = projectsRepository.listar().getOrDefault(emptyList())
                .filter { proyecto ->
                    proyecto.studentId == id || proyecto.equipo.any { miembro -> miembro.id == id }
                }

            // Badge de similitud de cada propuesta (la web usa el máximo vigente).
            val similitudes = similaritiesRepository.listar().getOrDefault(emptyList())
            val porcentajes = proyectos.mapNotNull { proyecto ->
                similitudes
                    .asSequence()
                    .filter { it.projectId1 == proyecto.id || it.projectId2 == proyecto.id }
                    .maxOfOrNull { it.similitud }
                    ?.let { (it * 100).roundToInt().coerceIn(0, 100) }
                    ?.let { proyecto.id to it }
            }.toMap()

            _uiState.value = CompaneroUiState.Success(
                CompaneroData(
                    usuario = perfil,
                    ficha = ficha,
                    proyectos = proyectos,
                    porcentajesSimilitud = porcentajes,
                ),
            )
        }
    }

    fun retry() {
        if (idUsuario != 0) load(idUsuario)
    }
}
