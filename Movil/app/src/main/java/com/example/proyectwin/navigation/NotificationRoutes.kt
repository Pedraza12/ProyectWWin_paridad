package com.example.proyectwin.navigation

import com.example.proyectwin.data.model.NotificationType

/**
 * Traduce el `enlace` de una notificación (`modulo:id`) a la ruta del rol
 * actual. Devuelve `null` cuando el enlace no es navegable (antiguo, vacío o
 * de un módulo desconocido): la UI simplemente marca la lectura sin crash.
 */
object NotificationRoutes {

    fun resolverDestino(
        tipo: NotificationType,
        enlaceModulo: String?,
        enlaceId: Int?,
        similitudesRoute: String,
        detailRoute: String,
        fichaRoute: String,
        reporteRoute: String,
    ): String? {
        if (enlaceModulo == null || enlaceId == null) return null
        return when {
            // Coincidencia: conserva el filtro por la propuesta notificada.
            tipo == NotificationType.SIMILITUD && enlaceModulo == "proyecto" && similitudesRoute.isNotEmpty() ->
                similitudesRoute.replace("{proyectoId}", enlaceId.toString())

            enlaceModulo == "proyecto" && detailRoute.isNotEmpty() ->
                detailRoute.replace("{id}", enlaceId.toString())

            enlaceModulo == "ficha" && fichaRoute.isNotEmpty() ->
                if (fichaRoute.contains("{fichaId}")) {
                    fichaRoute.replace("{fichaId}", enlaceId.toString())
                } else {
                    fichaRoute
                }

            enlaceModulo == "reporte" && reporteRoute.isNotEmpty() ->
                reporteRoute.replace("{bugId}", enlaceId.toString())

            else -> null
        }
    }
}
