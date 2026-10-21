package com.example.proyectwin.domain.util

import com.example.proyectwin.data.model.AppVersionInfo

/** Resultado de comparar la versión publicada con la instalada. */
data class EvaluacionActualizacion(
    val valida: Boolean,
    val hayActualizacion: Boolean,
    val obligatoria: Boolean,
    val urlDescarga: String?,
    val sinUrlDescarga: Boolean,
)

/**
 * Política única de actualización (compartida por el aviso global y el centro):
 *  - solo anuncia si la versión publicada es mayor y válida;
 *  - una actualización es obligatoria solo si el servidor lo pide Y la
 *    instalada está por debajo de la versión mínima;
 *  - solo acepta enlaces https;
 *  - si hay versión nueva pero no hay URL, se informa (no se anuncia un CTA).
 */
fun evaluarActualizacion(
    info: AppVersionInfo,
    versionInstalada: String,
): EvaluacionActualizacion {
    val valida = VersionComparator.esValida(info.versionPublicada)
    val hay = valida && VersionComparator.comparar(info.versionPublicada, versionInstalada) > 0
    val obligatoria = hay && info.obligatoria &&
        VersionComparator.comparar(versionInstalada, info.versionMinima) < 0
    val url = info.urlDescarga?.trim()?.takeIf { it.startsWith("https://") }

    return EvaluacionActualizacion(
        valida = valida,
        hayActualizacion = hay,
        obligatoria = obligatoria,
        urlDescarga = url,
        sinUrlDescarga = hay && url == null,
    )
}
