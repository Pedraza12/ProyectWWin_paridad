package com.example.proyectwin.data.api.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Contrato real del paginador de `GET /general-users?paginado=1` (Laravel). */
class PaginaGeneralUsersDtoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `parsea el paginador de Laravel con las claves reales`() {
        val respuesta = """
            {"current_page":2,
             "data":[{"id":9,"nombre":"Ana","apellido":"Martínez","username":"ana1","estado":true,"rol":"aprendiz"}],
             "first_page_url":"http://x?page=1","from":9,"last_page":3,"last_page_url":"http://x?page=3",
             "links":[],"next_page_url":"http://x?page=3","path":"http://x","per_page":8,
             "prev_page_url":"http://x?page=1","to":16,"total":20}
        """.trimIndent()

        val dto = json.decodeFromString<PaginaGeneralUsersDto>(respuesta)

        assertEquals(2, dto.currentPage)
        assertEquals(3, dto.lastPage)
        assertEquals(8, dto.perPage)
        assertEquals(20, dto.total)
        assertEquals(1, dto.data.size)
        assertEquals(9, dto.data.first().id)
        assertEquals("ana1", dto.data.first().username)
    }

    @Test
    fun `respuesta de una sola pagina sin elementos tambien parsea`() {
        val dto = json.decodeFromString<PaginaGeneralUsersDto>(
            """{"current_page":1,"data":[],"last_page":1,"per_page":8,"total":0}""",
        )

        assertEquals(0, dto.data.size)
        assertEquals(0, dto.total)
        assertEquals(1, dto.lastPage)
    }
}
