package com.example.proyectwin.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /app-version/android: versión publicada del APK (consulta pública).
 * Los campos son tolerantes a datos incompletos: la app decide qué hacer sin
 * romperse si el servidor responde algo inesperado.
 */
@Serializable
data class AppVersionDto(
    @SerialName("latest_version") val latestVersion: String? = null,
    @SerialName("minimum_supported_version") val minimumSupportedVersion: String? = null,
    @SerialName("enforce_minimum") val enforceMinimum: Boolean = false,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("release_notes") val releaseNotes: String? = null,
)
