package com.example.proyectwin.data.model

/** Métricas agregadas reales del panel (GET /dashboard-metrics). */
data class DashboardMetrics(
    val rol: String,
    val periodo: String,
    val usuarios: UsuariosMetricas,
    val fichasTotal: Int,
    val aprendicesTotal: Int,
    val propuestas: PropuestasMetricas,
    val evolucion: List<PuntoEvolucion>,
    val actividad: List<ActividadReciente>,
)

data class UsuariosMetricas(
    val total: Int = 0,
    val aprendices: Int = 0,
    val instructores: Int = 0,
    val admins: Int = 0,
)

data class PropuestasMetricas(
    val total: Int = 0,
    val borrador: Int = 0,
    val pendiente: Int = 0,
    val aprobado: Int = 0,
    val rechazado: Int = 0,
)

/** Un bucket de la serie temporal (mes o día, según el periodo). */
data class PuntoEvolucion(
    val clave: String,
    val label: String,
    val fecha: String,
    val total: Int,
    val propuestas: List<String> = emptyList(),
    val restantes: Int = 0,
)

/** Fila de la actividad reciente (auditoría o historial según el rol). */
data class ActividadReciente(
    val id: Int,
    val titulo: String,
    val meta: String,
    val usuario: String,
    val proyectoId: Int?,
    val fecha: String?,
)
