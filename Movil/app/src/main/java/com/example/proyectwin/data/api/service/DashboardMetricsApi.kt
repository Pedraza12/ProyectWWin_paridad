package com.example.proyectwin.data.api.service

import com.example.proyectwin.data.api.dto.DashboardMetricsDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Métricas agregadas del panel; el backend aplica el alcance del rol del token. */
interface DashboardMetricsApi {

    @GET("dashboard-metrics")
    suspend fun obtener(@Query("periodo") periodo: String): DashboardMetricsDto
}
