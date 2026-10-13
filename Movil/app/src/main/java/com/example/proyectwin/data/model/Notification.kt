package com.example.proyectwin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: Int,
    val mensaje: String,
    val tipo: String = NotificationType.SISTEMA.value,
    val userId: Int? = null,
    val projectId: Int? = null,
    val leido: Boolean = false,
    val createdAt: String? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val enlace: String? = null,
    val fecha: String? = null
) {
    val notifType: NotificationType get() = NotificationType.fromValue(tipo)

    /** `enlace` del backend tiene formato "recurso:id" (p.ej. "proyecto:12"). */
    val enlaceModulo: String? get() = enlace?.takeIf { it.contains(':') }?.substringBefore(':')

    val enlaceId: Int? get() = enlace?.substringAfter(':', "")?.toIntOrNull()
}
