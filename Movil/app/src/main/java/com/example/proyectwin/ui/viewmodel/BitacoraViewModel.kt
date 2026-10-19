package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.AuditLog
import com.example.proyectwin.data.model.GeneralUser
import com.example.proyectwin.domain.repository.AuditRepository
import com.example.proyectwin.domain.repository.UsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Registros por página de la bitácora (igual que la web: `PAGINA_TABLA`). */
const val REGISTROS_POR_PAGINA = 8

/** Filtros aplicados de la bitácora (null = sin filtrar; "Todos" no viaja). */
data class BitacoraFiltros(
    val accion: String? = null,
    val entidad: String? = null,
    val idUsuario: Int? = null,
    val desde: String? = null,
    val hasta: String? = null,
) {
    val hayFiltros: Boolean
        get() = accion != null || entidad != null || idUsuario != null || desde != null || hasta != null
}

sealed class BitacoraUiState {
    data object Loading : BitacoraUiState()

    /**
     * El backend entrega la bitácora completa (array plano, máximo 500 filas,
     * sin paginación de servidor), así que la paginación es en cliente, igual
     * que en la web.
     */
    data class Success(
        val registros: List<AuditLog>,
        val filtros: BitacoraFiltros = BitacoraFiltros(),
        val pagina: Int = 1,
    ) : BitacoraUiState() {
        val totalPaginas: Int
            get() = if (registros.isEmpty()) {
                1
            } else {
                (registros.size + REGISTROS_POR_PAGINA - 1) / REGISTROS_POR_PAGINA
            }

        val visibles: List<AuditLog>
            get() = registros.drop((pagina - 1) * REGISTROS_POR_PAGINA).take(REGISTROS_POR_PAGINA)
    }

    data class Error(val message: String) : BitacoraUiState()
}

/** Bitácora inmutable (`GET /audit-logs`) con filtros del backend. */
@HiltViewModel
class BitacoraViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
    private val usersRepository: UsersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BitacoraUiState>(BitacoraUiState.Loading)
    val uiState: StateFlow<BitacoraUiState> = _uiState.asStateFlow()

    /** Catálogo de responsables para el filtro (mejor esfuerzo). */
    private val _usuarios = MutableStateFlow<List<GeneralUser>>(emptyList())
    val usuarios: StateFlow<List<GeneralUser>> = _usuarios.asStateFlow()

    private var filtros = BitacoraFiltros()
    private var trabajoCarga: Job? = null

    /** Descarta respuestas de solicitudes que ya no corresponden al estado. */
    private var versionSolicitud = 0

    init {
        cargar()
        cargarUsuarios()
    }

    /** Aplica filtros nuevos y vuelve siempre a la primera página. */
    fun load(
        accion: String? = null,
        entidad: String? = null,
        idUsuario: Int? = null,
        desde: String? = null,
        hasta: String? = null,
    ) {
        filtros = BitacoraFiltros(accion, entidad, idUsuario, desde, hasta)
        cargar()
    }

    /** Limpia todos los filtros y recarga la bitácora completa. */
    fun limpiar() {
        filtros = BitacoraFiltros()
        cargar()
    }

    /** Reintento con los filtros vigentes (misma consulta). */
    fun recargar() = cargar()

    // Paginación en cliente: cambiar de página NO repite la consulta.

    fun irAPagina(pagina: Int) {
        val actual = _uiState.value as? BitacoraUiState.Success ?: return
        val destino = pagina.coerceIn(1, actual.totalPaginas)
        if (destino != actual.pagina) {
            _uiState.value = actual.copy(pagina = destino)
        }
    }

    fun paginaSiguiente() {
        val actual = _uiState.value as? BitacoraUiState.Success ?: return
        irAPagina(actual.pagina + 1)
    }

    fun paginaAnterior() {
        val actual = _uiState.value as? BitacoraUiState.Success ?: return
        irAPagina(actual.pagina - 1)
    }

    private fun cargar() {
        val f = filtros
        val version = ++versionSolicitud
        trabajoCarga?.cancel()
        trabajoCarga = viewModelScope.launch {
            _uiState.value = BitacoraUiState.Loading
            auditRepository.listar(f.accion, f.entidad, f.idUsuario, f.desde, f.hasta).fold(
                onSuccess = { registros ->
                    // Una respuesta vieja jamás pisa el estado de una más reciente.
                    if (version != versionSolicitud) return@launch
                    _uiState.value = BitacoraUiState.Success(registros, f, pagina = 1)
                },
                onFailure = { e ->
                    if (version == versionSolicitud) {
                        _uiState.value = BitacoraUiState.Error(
                            e.message ?: "No se pudo cargar la bitácora.",
                        )
                    }
                },
            )
        }
    }

    private fun cargarUsuarios() {
        viewModelScope.launch {
            usersRepository.listar().onSuccess { _usuarios.value = it }
        }
    }
}
