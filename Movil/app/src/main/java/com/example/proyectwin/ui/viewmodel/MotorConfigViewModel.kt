package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.domain.repository.MotorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Configuración real del motor para el dial de umbral (no editable aquí). */
data class MotorConfigUiState(
    val cargando: Boolean = false,
    val umbralPct: Int? = null,
    val meses: Int? = null,
    val error: String? = null,
)

@HiltViewModel
class MotorConfigViewModel @Inject constructor(
    private val motorRepository: MotorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MotorConfigUiState())
    val uiState: StateFlow<MotorConfigUiState> = _uiState.asStateFlow()

    private var cargado = false

    fun cargar(forzar: Boolean = false) {
        if (!forzar && (cargado || _uiState.value.cargando)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cargando = true, error = null)
            motorRepository.obtenerConfig().fold(
                onSuccess = { config ->
                    cargado = true
                    _uiState.value = MotorConfigUiState(
                        cargando = false,
                        umbralPct = (config.umbral * 100).roundToInt().coerceIn(0, 100),
                        meses = config.meses,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        cargando = false,
                        error = e.message ?: "No se pudo consultar el umbral del motor.",
                    )
                },
            )
        }
    }
}
