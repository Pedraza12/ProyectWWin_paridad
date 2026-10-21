package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.api.service.DashboardMetricsApi
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.model.DashboardMetrics
import com.example.proyectwin.domain.repository.DashboardMetricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardMetricsRepositoryImpl @Inject constructor(
    private val api: DashboardMetricsApi,
) : DashboardMetricsRepository {

    override suspend fun obtener(periodo: String): Result<DashboardMetrics> =
        safeApiCall { api.obtener(periodo) }.map { it.toDomain() }
}
