package com.example.proyectwin.data.repository

import com.example.proyectwin.data.api.safeApiCall
import com.example.proyectwin.data.api.service.AppVersionApi
import com.example.proyectwin.data.mapper.toDomain
import com.example.proyectwin.data.model.AppVersionInfo
import com.example.proyectwin.domain.repository.AppUpdatesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdatesRepositoryImpl @Inject constructor(
    private val api: AppVersionApi,
) : AppUpdatesRepository {

    override suspend fun versionPublicada(): Result<AppVersionInfo> =
        safeApiCall { api.actual() }.map { it.toDomain() }
}
