package com.example.proyectwin.data.model

/** Versión publicada del APK Android (respuesta pública del backend). */
data class AppVersionInfo(
    val versionPublicada: String,
    val versionMinima: String,
    /** Política de actualización obligatoria definida por el servidor. */
    val obligatoria: Boolean,
    val urlDescarga: String?,
    val notas: String?,
)
