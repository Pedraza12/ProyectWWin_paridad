package com.example.proyectwin.data.api.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato JSON de GET /v1/app-version/android: parsea la respuesta completa
 * del backend y tolera respuestas incompletas o con campos extra.
 */
class AppVersionDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parsea la respuesta completa del backend`() {
        val payload = """
            {
              "latest_version": "1.1.0",
              "minimum_supported_version": "1.0.0",
              "enforce_minimum": false,
              "download_url": "https://proyectwin-mvp-production.up.railway.app/apk/proyecttwin.apk",
              "release_notes": "Mejoras de estabilidad"
            }
        """.trimIndent()

        val dto = json.decodeFromString<AppVersionDto>(payload)

        assertEquals("1.1.0", dto.latestVersion)
        assertEquals("1.0.0", dto.minimumSupportedVersion)
        assertFalse(dto.enforceMinimum)
        assertEquals(
            "https://proyectwin-mvp-production.up.railway.app/apk/proyecttwin.apk",
            dto.downloadUrl,
        )
        assertEquals("Mejoras de estabilidad", dto.releaseNotes)
    }

    @Test
    fun `respuesta vacia o incompleta no rompe el parseo`() {
        val dto = json.decodeFromString<AppVersionDto>("{}")

        assertNull(dto.latestVersion)
        assertNull(dto.downloadUrl)
        assertFalse(dto.enforceMinimum)
    }

    @Test
    fun `ignora campos desconocidos del servidor`() {
        val payload = """
            { "latest_version": "2.0.0", "campo_nuevo": { "x": 1 } }
        """.trimIndent()

        val dto = json.decodeFromString<AppVersionDto>(payload)

        assertEquals("2.0.0", dto.latestVersion)
        assertTrue(dto.enforceMinimum.not()) // informativo: sigue en false por defecto
    }
}
