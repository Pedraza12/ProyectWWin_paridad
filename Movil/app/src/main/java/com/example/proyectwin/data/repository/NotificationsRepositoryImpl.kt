package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.dto.NotificationReadRequest
import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.api.service.NotificationsApi
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.model.Notification
import com.example.proyectwin.domain.repository.NotificationsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepositoryImpl @Inject constructor(
    private val api: NotificationsApi,
) : NotificationsRepository {

    override suspend fun listar(idUsuario: Int?): Result<List<Notification>> =
        safeApiCall { api.listar(idUsuario) }.map { lista -> lista.map { it.toDomain() } }

    override suspend fun marcarComoLeida(notificacion: Notification): Result<Notification> =
        safeApiCall { api.actualizar(notificacion.id, NotificationReadRequest(leida = true)) }
            .map { it.toDomain() }

    override suspend fun eliminar(id: Int): Result<Unit> =
        safeApiCall { api.eliminar(id) }.map {}
}
