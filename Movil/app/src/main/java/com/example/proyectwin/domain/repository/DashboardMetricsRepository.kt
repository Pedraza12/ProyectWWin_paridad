package com.example.proyectwin.domain.repository

import com.example.proyectwin.data.model.DashboardMetrics

/** Métricas agregadas del panel de inicio (el backend acota por rol). */
interface DashboardMetricsRepository {

    suspend fun obtener(periodo: String): Result<DashboardMetrics>
}
