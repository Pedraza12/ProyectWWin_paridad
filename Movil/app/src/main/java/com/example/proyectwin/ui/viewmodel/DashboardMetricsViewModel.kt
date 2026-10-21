package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.model.DashboardMetrics
import com.example.proyectwin.domain.repository.DashboardMetricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Periodos que acepta GET /dashboard-metrics (mismos que la web). */
enum class PeriodoMetricas(val api: String, val etiqueta: String, val corto: String) {
    TRES_MESES("3m", "Últimos 3 meses", "3M"),
    TREINTA_DIAS("30d", "Últimos 30 días", "30D"),
    SIETE_DIAS("7d", "Últimos 7 días", "7D"),
}

sealed class DashboardMetricsUiState {
    data object Loading : DashboardMetricsUiState()
    data class Success(
        val metrics: DashboardMetrics,
        /** Hay una recarga en curso; se conservan los datos anteriores. */
        val actualizando: Boolean = false,
    ) : DashboardMetricsUiState()

    data class Error(val message: String) : DashboardMetricsUiState()
}

/**
 * Métricas agregadas del panel. El backend calcula todo (no se cargan listas
 * completas para las gráficas) y acota por el rol del token.
 */
@HiltViewModel
class DashboardMetricsViewModel @Inject constructor(
    private val repository: DashboardMetricsRepository,
) : ViewModel() {

    private val _periodo = MutableStateFlow(PeriodoMetricas.TRES_MESES)
    val periodo: StateFlow<PeriodoMetricas> = _periodo.asStateFlow()

    private val _uiState = MutableStateFlow<DashboardMetricsUiState>(DashboardMetricsUiState.Loading)
    val uiState: StateFlow<DashboardMetricsUiState> = _uiState.asStateFlow()

    init {
        cargar(PeriodoMetricas.TRES_MESES, mostrarCarga = true)
    }

    fun cambiarPeriodo(periodo: PeriodoMetricas) {
        if (_periodo.value == periodo && _uiState.value is DashboardMetricsUiState.Success) return
        _periodo.value = periodo
        cargar(periodo, mostrarCarga = false)
    }

    fun reintentar() {
        cargar(_periodo.value, mostrarCarga = _uiState.value !is DashboardMetricsUiState.Success)
    }

    /** Pull-to-refresh: conserva lo visible y actualiza en segundo plano. */
    fun refrescar() {
        cargar(_periodo.value, mostrarCarga = false)
    }

    private fun cargar(periodo: PeriodoMetricas, mostrarCarga: Boolean) {
        viewModelScope.launch {
            val previo = _uiState.value as? DashboardMetricsUiState.Success
            _uiState.value = when {
                mostrarCarga || previo == null -> DashboardMetricsUiState.Loading
                else -> previo.copy(actualizando = true)
            }

            val resultado = repository.obtener(periodo.api)
            // Descarta respuestas viejas si el usuario cambió de periodo.
            if (_periodo.value != periodo) return@launch

            resultado.fold(
                onSuccess = { _uiState.value = DashboardMetricsUiState.Success(it) },
                onFailure = { e ->
                    _uiState.value = if (previo == null) {
                        DashboardMetricsUiState.Error(e.message ?: "No se pudieron cargar las métricas.")
                    } else {
                        // Un fallo de refresco no borra la gráfica ya visible.
                        previo.copy(actualizando = false)
                    }
                },
            )
        }
    }
}
