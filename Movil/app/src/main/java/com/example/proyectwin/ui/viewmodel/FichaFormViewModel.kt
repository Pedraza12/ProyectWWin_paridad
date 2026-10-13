package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.api.ApiException
import com.example.proyectwin.data.local.SessionManager
import com.example.proyectwin.data.model.CredencialesUsuario
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.InstructorProfile
import com.example.proyectwin.data.model.TrainingProgram
import com.example.proyectwin.data.model.UserRole
import com.example.proyectwin.domain.repository.CatalogsRepository
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.MembersRepository
import com.example.proyectwin.domain.repository.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Aprendiz disponible para agregar al roster, venga de `/general-users`
 * (admin: incluye recién creados sin fila en `apprentices`) o de
 * `/apprentices` (instructor: su alcance). El id es el de `general_users`,
 * que es el que acepta el roster de la ficha.
 */
data class AprendizCandidato(
    val idUsuario: Int,
    val nombre: String,
    val correo: String,
    val foto: String? = null,
    /** Ficha actual del aprendiz (null = sin ficha). */
    val fichaActualId: Int? = null,
)

data class FichaFormData(
    val programas: List<TrainingProgram> = emptyList(),
    val instructores: List<InstructorProfile> = emptyList(),
    /** Fila de instructor del usuario actual (rol instructor). */
    val miInstructor: InstructorProfile? = null,
    val ficha: Ficha? = null,
    /** Aprendices visibles para agregar al roster (ids de usuario). */
    val candidatosAprendices: List<AprendizCandidato> = emptyList(),
)

sealed class FichaFormUiState {
    data object Loading : FichaFormUiState()
    data class Ready(val data: FichaFormData) : FichaFormUiState()
    data class Error(val message: String) : FichaFormUiState()
}

sealed class FichaSaveState {
    data object Idle : FichaSaveState()
    data object Loading : FichaSaveState()
    data object Success : FichaSaveState()
    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : FichaSaveState()
}

/** Alta/edición de fichas con catálogos, instructor y roster reales. */
@HiltViewModel
class FichaFormViewModel @Inject constructor(
    private val fichasRepository: FichasRepository,
    private val catalogsRepository: CatalogsRepository,
    private val membersRepository: MembersRepository,
    private val usersRepository: UsersRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FichaFormUiState>(FichaFormUiState.Loading)
    val uiState: StateFlow<FichaFormUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<FichaSaveState>(FichaSaveState.Idle)
    val saveState: StateFlow<FichaSaveState> = _saveState.asStateFlow()

    private val _creandoAprendiz = MutableStateFlow(false)
    val creandoAprendiz: StateFlow<Boolean> = _creandoAprendiz.asStateFlow()

    fun resetSaveState() {
        _saveState.value = FichaSaveState.Idle
    }

    fun load(fichaId: Int?) {
        viewModelScope.launch {
            _uiState.value = FichaFormUiState.Loading
            val usuario = sessionManager.currentUser.first()

            val programas = catalogsRepository.listarProgramas().getOrElse { e ->
                _uiState.value = FichaFormUiState.Error(e.message ?: "No se pudieron cargar los programas.")
                return@launch
            }
            val instructores = membersRepository.instructores().getOrDefault(emptyList())
            val miInstructor = instructores.firstOrNull { it.idUsuario == usuario?.id }
            val ficha = fichaId?.let { fichasRepository.obtener(it).getOrNull() }
            // Candidatos del alcance del usuario. El admin usa /general-users
            // (incluye aprendices recién creados, que aún no tienen fila en
            // `apprentices`); los demás roles usan /apprentices, que filtra por
            // su alcance y era el único endpoint accesible para ellos.
            val candidatos = cargarCandidatos(usuario?.role)

            _uiState.value = FichaFormUiState.Ready(
                FichaFormData(
                    programas = programas,
                    instructores = instructores,
                    miInstructor = miInstructor,
                    ficha = ficha,
                    candidatosAprendices = candidatos,
                ),
            )
        }
    }

    /** Relee los candidatos sin recargar catálogos ni la ficha. */
    fun refrescarCandidatos() {
        viewModelScope.launch {
            val actual = _uiState.value as? FichaFormUiState.Ready ?: return@launch
            val usuario = sessionManager.currentUser.first()
            _uiState.value = FichaFormUiState.Ready(
                actual.data.copy(candidatosAprendices = cargarCandidatos(usuario?.role)),
            )
        }
    }

    private suspend fun cargarCandidatos(rol: String?): List<AprendizCandidato> =
        if (rol == UserRole.ADMINISTRADOR.value) {
            usersRepository.listar(role = UserRole.APRENDIZ.value)
                .getOrDefault(emptyList())
                .filter { it.estado }
                .map {
                    AprendizCandidato(
                        idUsuario = it.id,
                        nombre = it.name,
                        correo = it.email,
                        foto = it.fotoPerfil,
                        fichaActualId = it.fichaId,
                    )
                }
        } else {
            membersRepository.listarAprendices()
                .getOrDefault(emptyList())
                .map {
                    AprendizCandidato(
                        idUsuario = it.idUsuario,
                        nombre = it.nombre.ifBlank { it.correo },
                        correo = it.correo,
                        foto = it.fotoUrl,
                        fichaActualId = it.idClassGroup,
                    )
                }
        }

    fun guardar(
        fichaId: Int?,
        nombre: String,
        numero: String,
        estado: String,
        idPrograma: Int,
        idInstructor: Int,
        aprendices: List<Int>?,
    ) {
        if (_saveState.value is FichaSaveState.Loading) return // doble toque
        viewModelScope.launch {
            _saveState.value = FichaSaveState.Loading
            val resultado = if (fichaId == null) {
                fichasRepository.crear(nombre, numero, estado, idPrograma, idInstructor, aprendices)
            } else {
                fichasRepository.actualizar(fichaId, nombre, numero, estado, idPrograma, idInstructor, aprendices)
            }
            resultado.fold(
                onSuccess = { _saveState.value = FichaSaveState.Success },
                onFailure = { e ->
                    _saveState.value = FichaSaveState.Error(
                        message = e.message ?: "No se pudo guardar la ficha.",
                        fieldErrors = (e as? ApiException.Http)?.fieldErrors.orEmpty(),
                    )
                },
            )
        }
    }

    /**
     * Crea la cuenta de un aprendiz y lo matricula en la ficha en edición.
     * Requiere que la ficha exista (se usa dentro de "Editar ficha").
     */
    fun crearAprendizEnFicha(
        fichaId: Int,
        nombre: String,
        apellido: String,
        tipoDocumento: String,
        numeroDocumento: String,
        correo: String,
        onResult: (Result<CredencialesUsuario?>) -> Unit,
    ) {
        if (_creandoAprendiz.value) return
        viewModelScope.launch {
            _creandoAprendiz.value = true
            val resultado = fichasRepository.crearAprendizEnFicha(
                fichaId = fichaId,
                nombre = nombre,
                apellido = apellido,
                tipoDocumento = tipoDocumento,
                numeroDocumento = numeroDocumento,
                correo = correo,
            ).map { credenciales ->
                // Recarga la ficha para que el roster quede al día de inmediato.
                val actualizado = _uiState.value
                if (actualizado is FichaFormUiState.Ready) {
                    val ficha = fichasRepository.obtener(fichaId).getOrNull()
                    val usuario = sessionManager.currentUser.first()
                    _uiState.value = FichaFormUiState.Ready(
                        actualizado.data.copy(
                            ficha = ficha ?: actualizado.data.ficha,
                            candidatosAprendices = cargarCandidatos(usuario?.role),
                        ),
                    )
                }
                credenciales
            }
            _creandoAprendiz.value = false
            onResult(resultado)
        }
    }
}
