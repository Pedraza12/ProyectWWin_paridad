package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.BugReport
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.data.model.Project
import com.example.proyectwin.data.model.Similarity
import com.example.proyectwin.domain.repository.BugReportsRepository
import com.example.proyectwin.domain.repository.ProjectsRepository
import com.example.proyectwin.domain.repository.SimilaritiesRepository
import com.example.proyectwin.domain.repository.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AdminUiState {
    data object Loading : AdminUiState()
    data class Success(
        val users: List<GeneralUser>,
        val projects: List<Project>,
        val bugReports: List<BugReport>,
        val similarities: List<Similarity>
    ) : AdminUiState()
    data class Error(val message: String) : AdminUiState()
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val usersRepository: UsersRepository,
    private val projectsRepository: ProjectsRepository,
    private val bugReportsRepository: BugReportsRepository,
    private val similaritiesRepository: SimilaritiesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminUiState>(AdminUiState.Loading)
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    /**
     * Listado COMPLETO de pares para la pantalla de Similitudes del admin.
     * El listado global de la API solo devuelve los pares con AMBOS proyectos
     * aprobados (1 de 10 en la base actual), así que se completa consultando
     * cada propuesta con `proyecto_id` (vigentes con contraparte aprobada) y
     * `historial=true` (evidencia de versiones anteriores), endpoints que ya
     * existen. Se deduplica por id: no se inventa ni se hardcodea nada.
     */
    private val _similitudesCompletas = MutableStateFlow<List<Similarity>?>(null)
    val similitudesCompletas: StateFlow<List<Similarity>?> = _similitudesCompletas.asStateFlow()

    private var cargandoCompletas = false

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = AdminUiState.Loading
            val usuarios = async { usersRepository.listar() }
            val proyectos = async { projectsRepository.listar() }
            val reportes = async { bugReportsRepository.listar() }
            val similitudes = async { similaritiesRepository.listar() }

            val u = usuarios.await()
            val p = proyectos.await()
            val b = reportes.await()
            val s = similitudes.await()

            val fallo = listOf(u, p, b, s).firstOrNull { it.isFailure }
            if (fallo != null) {
                _uiState.value = AdminUiState.Error(
                    fallo.exceptionOrNull()?.message ?: "Error al cargar los datos",
                )
                return@launch
            }
            _uiState.value = AdminUiState.Success(
                users = u.getOrThrow(),
                projects = p.getOrThrow(),
                bugReports = b.getOrThrow(),
                similarities = s.getOrThrow(),
            )
        }
    }

    fun refresh() {
        loadAll()
        _similitudesCompletas.value = null
        cargandoCompletas = false
    }

    /**
     * Completa el listado global con los pares de cada propuesta del sistema.
     * Se ejecuta al abrir la pantalla de Similitudes (no en cada refresh del
     * panel) para no multiplicar llamadas en Inicio/Usuarios/Perfil.
     */
    fun cargarSimilitudesCompletas() {
        if (cargandoCompletas || _similitudesCompletas.value != null) return
        cargandoCompletas = true
        viewModelScope.launch {
            try {
                val proyectos = (_uiState.value as? AdminUiState.Success)?.projects
                    ?: projectsRepository.listar().getOrDefault(emptyList())

                val porId = LinkedHashMap<Int, Similarity>()
                similaritiesRepository.listar().getOrDefault(emptyList()).forEach { porId[it.id] = it }

                if (proyectos.size <= MAX_PROYECTOS_AGREGADOS) {
                    // Por lotes pequeños: con muchos proyectos juntos, las
                    // últimas peticiones esperan en cola más que el timeout de
                    // lectura y se perdían pares (el servidor de desarrollo
                    // atiende pocas a la vez). Cuatro a la vez + un reintento
                    // por consulta mantiene el listado completo y rápido.
                    proyectos.map { it.id }.chunked(TAMANO_LOTE).forEach { lote ->
                        val paresDelLote = coroutineScope {
                            lote.map { id ->
                                async {
                                    listarConReintento(id, historial = false) +
                                        listarConReintento(id, historial = true)
                                }
                            }.awaitAll()
                        }
                        paresDelLote.flatten().forEach { porId[it.id] = it }
                    }
                }

                _similitudesCompletas.value = porId.values.sortedWith(
                    compareByDescending<Similarity> { it.vigente }.thenByDescending { it.id },
                )
            } finally {
                cargandoCompletas = false
            }
        }
    }

    private suspend fun listarConReintento(idProyecto: Int, historial: Boolean): List<Similarity> {
        repeat(2) { intento ->
            val resultado = similaritiesRepository.listar(
                proyectoId = idProyecto,
                historial = if (historial) true else null,
            )
            resultado.onSuccess { return it }
            if (intento == 0) kotlinx.coroutines.delay(300)
        }
        return emptyList()
    }

    /** Alta real (admin): documento obligatorio y credenciales del backend. */
    fun createUser(
        nombre: String,
        apellido: String,
        tipoDocumento: String,
        numeroDocumento: String,
        correo: String,
        rol: String,
        onResult: (Result<com.example.proyectwin.data.model.CredencialesUsuario>) -> Unit,
    ) {
        viewModelScope.launch {
            usersRepository.crear(
                nombre = nombre,
                apellido = apellido,
                tipoDocumento = tipoDocumento,
                numeroDocumento = numeroDocumento,
                correo = correo,
                rol = rol,
            ).fold(
                onSuccess = {
                    refresh()
                    onResult(Result.success(it))
                },
                onFailure = { onResult(Result.failure(it)) },
            )
        }
    }

    /** Descarga de credenciales PDF en curso (evita dobles envíos del botón). */
    private val _descargandoPdf = MutableStateFlow(false)
    val descargandoPdf: StateFlow<Boolean> = _descargandoPdf.asStateFlow()

    /** Descarga el PDF de credenciales (bytes) para guardarlo en el dispositivo. */
    fun credencialesPdf(ids: List<Int>, onResult: (Result<ByteArray>) -> Unit) {
        if (_descargandoPdf.value) return
        _descargandoPdf.value = true
        viewModelScope.launch {
            try {
                onResult(usersRepository.descargarCredencialesPdf(ids))
            } finally {
                _descargandoPdf.value = false
            }
        }
    }

    fun deleteUser(id: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            usersRepository.eliminar(id).fold(
                onSuccess = {
                    refresh()
                    onResult(true)
                },
                onFailure = {
                    onResult(false)
                }
            )
        }
    }

    fun clearError() {
        if (_uiState.value is AdminUiState.Error) {
            _uiState.value = AdminUiState.Loading
        }
    }

    private companion object {
        /** Tope de proyectos a recorrer para no disparar cientos de llamadas. */
        const val MAX_PROYECTOS_AGREGADOS = 80

        /** Consultas simultáneas por lote (2 peticiones por proyecto). */
        const val TAMANO_LOTE = 4
    }
}
