package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.Ficha
import com.example.proyectwin.data.model.PaginaUsuarios
import com.example.proyectwin.data.model.TrainingProgram
import com.example.proyectwin.domain.repository.CatalogsRepository
import com.example.proyectwin.domain.repository.FichasRepository
import com.example.proyectwin.domain.repository.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filtros del listado admin de usuarios (null = sin filtrar, como la web:
 * los valores "todos" no viajan al backend).
 */
data class UserFilters(
    val search: String = "",
    val role: String? = null,      // aprendiz | instructor | admin
    val estado: String? = null,    // activo | suspendido
    val fichaId: String? = null,   // id de la ficha como texto, o "sin"
    val programa: String? = null,  // nombre del programa
) {
    val hayFiltros: Boolean
        get() = search.isNotBlank() || role != null || estado != null || fichaId != null || programa != null
}

sealed class UserManagementUiState {
    data object Loading : UserManagementUiState()
    data class Success(val pagina: PaginaUsuarios) : UserManagementUiState()
    data class Error(val message: String) : UserManagementUiState()
}

/**
 * Gestión de usuarios (admin): paginación del servidor + filtros reales del
 * contrato (`search`, `role`, `estado`, `ficha_id`, `programa`).
 */
@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val usersRepository: UsersRepository,
    private val fichasRepository: FichasRepository,
    private val catalogsRepository: CatalogsRepository,
) : ViewModel() {

    companion object {
        /** Filas por página (mismo valor que la web: PAGINA_TABLA = 8). */
        const val POR_PAGINA = 8

        /** La web espera 300 ms antes de consultar la búsqueda. */
        internal const val DEBOUNCE_BUSQUEDA_MS = 300L
    }

    private val _uiState = MutableStateFlow<UserManagementUiState>(UserManagementUiState.Loading)
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    private val _filtros = MutableStateFlow(UserFilters())
    val filtros: StateFlow<UserFilters> = _filtros.asStateFlow()

    /** Catálogos para las opciones de ficha y programa (mejor esfuerzo). */
    private val _fichas = MutableStateFlow<List<Ficha>>(emptyList())
    val fichas: StateFlow<List<Ficha>> = _fichas.asStateFlow()

    private val _programas = MutableStateFlow<List<TrainingProgram>>(emptyList())
    val programas: StateFlow<List<TrainingProgram>> = _programas.asStateFlow()

    /** Error de una acción puntual (p. ej. eliminar) para avisar una sola vez. */
    private val _errorAccion = MutableStateFlow<String?>(null)
    val errorAccion: StateFlow<String?> = _errorAccion.asStateFlow()

    /** Modo selección múltiple para el PDF de credenciales por lote. */
    private val _seleccionActiva = MutableStateFlow(false)
    val seleccionActiva: StateFlow<Boolean> = _seleccionActiva.asStateFlow()

    /** Ids marcados: persisten al cambiar de página y se limpian al filtrar. */
    private val _seleccionados = MutableStateFlow<Set<Int>>(emptySet())
    val seleccionados: StateFlow<Set<Int>> = _seleccionados.asStateFlow()

    /** `true` mientras se recarga conservando la lista (pull-to-refresh). */
    private val _refrescando = MutableStateFlow(false)
    val refrescando: StateFlow<Boolean> = _refrescando.asStateFlow()

    private var paginaActual = 1
    private var ultimaPagina = 1

    private var trabajoCarga: Job? = null
    private var trabajoBusqueda: Job? = null

    /** Descarta respuestas de solicitudes que ya no corresponden al estado. */
    private var versionSolicitud = 0

    init {
        cargar()
        cargarCatalogos()
    }

    // ------------------------------------------------------------- Filtros

    /** Búsqueda con retardo (evita una petición por tecla, como la web). */
    fun buscar(texto: String) {
        _filtros.update { it.copy(search = texto) }
        limpiarSeleccion()
        trabajoBusqueda?.cancel()
        trabajoBusqueda = viewModelScope.launch {
            delay(DEBOUNCE_BUSQUEDA_MS)
            paginaActual = 1
            cargar()
        }
    }

    fun filtrarRol(role: String?) = aplicarFiltro { it.copy(role = role) }
    fun filtrarEstado(estado: String?) = aplicarFiltro { it.copy(estado = estado) }
    fun filtrarFicha(fichaId: String?) = aplicarFiltro { it.copy(fichaId = fichaId) }
    fun filtrarPrograma(programa: String?) = aplicarFiltro { it.copy(programa = programa) }

    fun limpiarFiltros() {
        trabajoBusqueda?.cancel()
        _filtros.value = UserFilters()
        limpiarSeleccion()
        paginaActual = 1
        cargar()
    }

    private fun aplicarFiltro(cambio: (UserFilters) -> UserFilters) {
        // Cualquier cambio de filtro reinicia a la primera página y anula la
        // búsqueda pendiente (el texto ya vive en `_filtros`).
        trabajoBusqueda?.cancel()
        _filtros.update(cambio)
        limpiarSeleccion()
        paginaActual = 1
        cargar()
    }

    // ---------------------------------------------------------- Paginación

    fun irAPagina(pagina: Int) {
        val destino = pagina.coerceIn(1, ultimaPagina.coerceAtLeast(1))
        if (destino == paginaActual && _uiState.value is UserManagementUiState.Success) return
        paginaActual = destino
        cargar()
    }

    fun paginaSiguiente() = irAPagina(paginaActual + 1)
    fun paginaAnterior() = irAPagina(paginaActual - 1)

    /** Reintento/recarga de la página y filtros actuales (sin perder la lista). */
    fun recargar() = cargar(conservarLista = true)

    /** Elimina un usuario y recarga la página actual (como la lista web). */
    fun eliminarUsuario(id: Int) {
        viewModelScope.launch {
            usersRepository.eliminar(id).fold(
                onSuccess = {
                    // Un usuario eliminado no debe viajar en un PDF por lote.
                    _seleccionados.update { it - id }
                    // Si era la última fila de una página interior, retrocede una.
                    val pagina = (_uiState.value as? UserManagementUiState.Success)?.pagina
                    if (pagina != null && pagina.items.size == 1 && paginaActual > 1) {
                        paginaActual -= 1
                    }
                    cargar()
                },
                onFailure = { e ->
                    _errorAccion.value = e.message ?: "No se pudo eliminar el usuario."
                },
            )
        }
    }

    fun limpiarErrorAccion() {
        _errorAccion.value = null
    }

    // -------------------------------------------------- Selección múltiple

    fun activarSeleccion() {
        _seleccionActiva.value = true
    }

    /** Cancela el modo selección y descarta los ids marcados. */
    fun desactivarSeleccion() {
        _seleccionActiva.value = false
        _seleccionados.value = emptySet()
    }

    /** Alterna la marca de un usuario (la selección persiste entre páginas). */
    fun toggleSeleccion(id: Int) {
        _seleccionados.update { if (id in it) it - id else it + id }
    }

    /** Marca/desmarca todos los usuarios de la página visible. */
    fun toggleTodosEnPagina(ids: List<Int>) {
        val actuales = _seleccionados.value
        val todosMarcados = ids.isNotEmpty() && ids.all { it in actuales }
        _seleccionados.value = if (todosMarcados) actuales - ids.toSet() else actuales + ids
    }

    /** Ids listos para el request del PDF por lote (orden estable). */
    fun idsSeleccionados(): List<Int> = _seleccionados.value.sorted()

    /** Vacía la marca sin salir del modo (cambios de filtro/búsqueda). */
    fun limpiarSeleccion() {
        _seleccionados.value = emptySet()
    }

    /** Avisa por el snackbar un fallo puntual (p. ej. el PDF por lote). */
    fun reportarError(mensaje: String) {
        _errorAccion.value = mensaje
    }

    // -------------------------------------------------------------- Carga

    private fun cargar(conservarLista: Boolean = false) {
        val version = ++versionSolicitud
        trabajoCarga?.cancel()
        // Con datos ya en pantalla (pull-to-refresh) la lista se queda visible
        // y solo gira el indicador; el skeleton es para la carga inicial.
        val mantenerLista = conservarLista && _uiState.value is UserManagementUiState.Success
        trabajoCarga = viewModelScope.launch {
            if (mantenerLista) {
                _refrescando.value = true
            } else {
                _refrescando.value = false
                _uiState.value = UserManagementUiState.Loading
            }
            try {
                // Si la página pedida ya no existe (p. ej. otro admin borró filas),
                // el servidor la devuelve vacía con `last_page` menor. Se corrige
                // UNA sola vez hacia la última página válida y se reintenta.
                var correccionUsada = false
                while (true) {
                    val f = _filtros.value
                    val resultado = usersRepository.listarPaginado(
                        page = paginaActual,
                        porPagina = POR_PAGINA,
                        search = f.search.trim().ifBlank { null },
                        role = f.role,
                        estado = f.estado,
                        fichaId = f.fichaId,
                        programa = f.programa,
                    )
                    // Una respuesta vieja jamás pisa el estado de una más reciente.
                    if (version != versionSolicitud) return@launch
                    val pagina = resultado.getOrElse { e ->
                        _uiState.value = UserManagementUiState.Error(
                            e.message ?: "No se pudieron cargar los usuarios.",
                        )
                        return@launch
                    }
                    if (pagina.items.isEmpty() && pagina.currentPage > pagina.lastPage && !correccionUsada) {
                        correccionUsada = true
                        paginaActual = pagina.lastPage.coerceAtLeast(1)
                        continue
                    }
                    ultimaPagina = pagina.lastPage.coerceAtLeast(1)
                    _uiState.value = UserManagementUiState.Success(pagina)
                    return@launch
                }
            } finally {
                // Un descarte por versión no debe apagar el indicador del
                // refresco más reciente.
                if (version == versionSolicitud) _refrescando.value = false
            }
        }
    }

    private fun cargarCatalogos() {
        viewModelScope.launch {
            fichasRepository.listar().onSuccess { _fichas.value = it }
        }
        viewModelScope.launch {
            catalogsRepository.listarProgramas().onSuccess { _programas.value = it }
        }
    }
}
