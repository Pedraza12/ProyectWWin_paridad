package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bandeja de notificaciones (`notifications`). */
@Serializable
data class NotificationDto(
    val id: Int = 0,
    val titulo: String = "",
    val descripcion: String? = null,
    val tipo: String = "sistema",
    val enlace: String? = null,
    /** El backend no castea la columna: viaja como 0/1 (int). */
    val leida: Int = 0,
    val fecha: String? = null,
    @SerialName("id_usuario") val idUsuario: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val user: GeneralUserDto? = null,
)

/**
 * POST /notifications: el backend exige destinatario real (`exists`).
 * Si no se conoce, el campo se omite (nunca se envía `id_usuario: 0`).
 */
@Serializable
data class NotificationRequest(
    val titulo: String,
    val descripcion: String?,
    val tipo: String,
    val enlace: String?,
    val leida: Boolean,
    val fecha: String,
    @SerialName("id_usuario") val idUsuario: Int? = null,
)

/**
 * PUT /notifications/{id}: el dueño solo marca leída (el backend ignora el
 * resto de campos para no-admin); no se envía destinatario.
 */
@Serializable
data class NotificationReadRequest(val leida: Boolean)
