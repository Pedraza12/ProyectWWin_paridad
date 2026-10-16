package com.example.proyectwin.domain.repository

import com.example.proyectwin.data.model.AppVersionInfo

/** Consulta pública de la versión publicada del APK (actualizaciones). */
interface AppUpdatesRepository {

    suspend fun versionPublicada(): Result<AppVersionInfo>
}
