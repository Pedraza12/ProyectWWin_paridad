package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reporte de falla (`bug_reports`). */
@Serializable
data class BugReportDto(
    val id: Int = 0,
    val titulo: String? = null,
    @SerialName("numero_ficha") val numeroFicha: String? = null,
    val motivo: String? = null,
    val descripcion: String = "",
    val tipo: String = "otro",
    val estado: String = "pendiente",
    val respuesta: String? = null,
    val fecha: String? = null,
    @SerialName("id_usuario") val idUsuario: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val generalUser: GeneralUserDto? = null,
)

/**
 * POST /bug-reports: el backend fija `estado = pendiente` y firma el reporte
 * con el usuario del token; no se envían esos campos desde el cliente.
 */
@Serializable
data class BugReportCreateRequest(
    val titulo: String? = null,
    @SerialName("numero_ficha") val numeroFicha: String? = null,
    val motivo: String? = null,
    val descripcion: String,
    val tipo: String,
    val fecha: String,
)

/** PUT /bug-reports: objeto completo (estado/respuesta desde el admin). `fecha` es obligatoria. */
@Serializable
data class BugReportUpdateRequest(
    val titulo: String?,
    @SerialName("numero_ficha") val numeroFicha: String? = null,
    val motivo: String? = null,
    val descripcion: String,
    val tipo: String,
    val estado: String,
    val respuesta: String? = null,
    val fecha: String,
    @SerialName("id_usuario") val idUsuario: Int,
)
