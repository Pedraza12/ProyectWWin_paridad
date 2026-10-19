package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respuesta paginada de `GET /general-users?paginado=1` (paginator de Laravel):
 * solo se declaran las claves estables que consume la app; el resto se ignora.
 */
@Serializable
data class PaginaGeneralUsersDto(
    val data: List<GeneralUserDto> = emptyList(),
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    val total: Int = 0,
)
