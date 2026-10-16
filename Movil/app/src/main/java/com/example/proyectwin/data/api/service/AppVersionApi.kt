package com.example.proyectwin.data.api.service

import com.example.proyectwin.data.api.dto.AppVersionDto
import retrofit2.http.GET

/** Versión publicada de la app (pública: se consulta sin token). */
interface AppVersionApi {

    @GET("app-version/android")
    suspend fun actual(): AppVersionDto
}
