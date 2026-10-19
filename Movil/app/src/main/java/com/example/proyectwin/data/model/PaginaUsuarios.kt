package com.example.proyectwin.data.model

/** Página de usuarios obtenida con `paginado=1` (paginación del servidor). */
data class PaginaUsuarios(
    val items: List<GeneralUser>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)
