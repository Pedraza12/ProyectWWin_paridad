package com.example.proyectwin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectwin.data.local.UpdatePreferences
import com.example.proyectwin.data.model.AppVersionInfo
import com.example.proyectwin.domain.repository.AppUpdatesRepository
import com.example.proyectwin.domain.util.VersionComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del aviso de actualización (banner opcional o diálogo obligatorio). */
data class AppUpdateUiState(
    val mostrar: Boolean = false,
    val obligatoria: Boolean = false,
    val versionNueva: String? = null,
    val urlDescarga: String? = null,
    val notas: String? = null,
)

/**
 * Detecta si hay una versión más reciente del APK y gobierna el aviso.
 *
 * Política (sin consultas constantes ni avisos repetidos):
 *  - Una consulta de red por proceso (arranque de la app). No se conserva un
 *    "última consulta" entre arranques: si se publica una versión nueva, al
 *    volver a abrir la app se detecta.
 *  - "Más tarde" silencia esa misma versión durante 24 h; una versión más
 *    nueva vuelve a avisar de inmediato.
 *  - La actualización obligatoria (solo si el servidor la define con
 *    `enforce_minimum`) no se pospone: reaparece en cada arranque.
 *  - Cualquier fallo (sin red, error HTTP, datos incompletos) es silencioso.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdatesRepository,
    private val prefs: UpdatePreferences,
) : ViewModel() {

    companion object {
        /**
         * Sin caché entre procesos: cada arranque hace UNA consulta (el flag
         * `consultaEnSesion` impide repetirla dentro del mismo proceso). Así una
         * versión publicada se detecta al volver a abrir la aplicación.
         */
        internal const val INTERVALO_CONSULTA_MS = 0L

        /** Tras "Más tarde", la misma versión no reaparece hasta 24 h después. */
        internal const val INTERVALO_POSPUESTO_MS = 24L * 60 * 60 * 1000
    }

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    // Una sola consulta de red por proceso (sesión), como máximo.
    private var consultaEnSesion = false

    /** Consulta la versión publicada y decide si corresponde avisar. */
    fun verificar(versionInstalada: String, ahora: Long = System.currentTimeMillis()) {
        if (consultaEnSesion) return
        consultaEnSesion = true
        viewModelScope.launch {
            val ultima = prefs.ultimaConsulta()
            if (ultima != null && ahora - ultima < INTERVALO_CONSULTA_MS) return@launch

            // Silencioso ante cualquier fallo: sin red o con error, la app
            // sigue funcionando y se reintenta en un arranque posterior.
            val info = repository.versionPublicada().getOrNull() ?: return@launch
            if (!VersionComparator.esValida(info.versionPublicada)) return@launch

            prefs.marcarConsulta(ahora)
            evaluar(info, versionInstalada, ahora)
        }
    }

    private suspend fun evaluar(info: AppVersionInfo, versionInstalada: String, ahora: Long) {
        // Sin versión más reciente (o instalada más nueva): nada que anunciar.
        if (VersionComparator.comparar(info.versionPublicada, versionInstalada) <= 0) return

        // Sin enlace https válido no hay acción posible: no se anuncia.
        val url = info.urlDescarga?.takeIf { it.startsWith("https://") } ?: return

        val obligatoria = info.obligatoria &&
            VersionComparator.comparar(versionInstalada, info.versionMinima) < 0

        // Posponer aplica solo al aviso opcional: la obligatoria (política
        // explícita del servidor) vuelve a mostrarse en cada arranque.
        if (!obligatoria) {
            val (pospuesta, pospuestaEn) = prefs.pospuesta()
            if (pospuesta == info.versionPublicada &&
                ahora - pospuestaEn < INTERVALO_POSPUESTO_MS
            ) {
                return
            }
        }

        _uiState.value = AppUpdateUiState(
            mostrar = true,
            obligatoria = obligatoria,
            versionNueva = info.versionPublicada,
            urlDescarga = url,
            notas = info.notas,
        )
    }

    /** "Más tarde": oculta el aviso y no insiste por 24 h con esa versión. */
    fun masTarde(ahora: Long = System.currentTimeMillis()) {
        val estado = _uiState.value
        _uiState.value = AppUpdateUiState()
        val version = estado.versionNueva ?: return
        viewModelScope.launch { prefs.posponer(version, ahora) }
    }

    /**
     * "Actualizar" pulsado (el enlace ya se abrió): oculta el aviso. En la
     * opcional se aplica la misma pausa de 24 h; en la obligatoria reaparece
     * en el próximo arranque mientras la versión instalada no cambie.
     */
    fun actualizacionAtendida(ahora: Long = System.currentTimeMillis()) {
        val estado = _uiState.value
        _uiState.value = AppUpdateUiState()
        if (estado.obligatoria) return
        val version = estado.versionNueva ?: return
        viewModelScope.launch { prefs.posponer(version, ahora) }
    }
}
