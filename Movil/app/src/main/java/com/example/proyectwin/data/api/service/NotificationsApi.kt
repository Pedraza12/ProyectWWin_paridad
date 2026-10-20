package com.example.proyectwin.data.api.service

import com.example.proyectwin.data.api.dto.NotificationDto
import com.example.proyectwin.data.api.dto.NotificationReadRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Bandeja de notificaciones (`/v1/notifications`). */
interface NotificationsApi {

    @GET("notifications")
    suspend fun listar(
        @Query("id_usuario") idUsuario: Int? = null,
        @Query("included") included: String? = null,
    ): List<NotificationDto>

    @GET("notifications/{id}")
    suspend fun obtener(@Path("id") id: Int): NotificationDto

    /** Marcar como leída = PUT parcial `{leida}` (contrato del backend). */
    @PUT("notifications/{id}")
    suspend fun actualizar(@Path("id") id: Int, @Body body: NotificationReadRequest): NotificationDto

    @DELETE("notifications/{id}")
    suspend fun eliminar(@Path("id") id: Int): NotificationDto
}
